"""File transfer manager with chunked transfer and progress tracking."""

import asyncio
import base64
import hashlib
import logging
import os
import threading
import time
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Callable, Dict, Optional, Set

from streamsync_cli.core.protocol import (
    StreamSyncMessage,
    make_file_request,
    make_file_chunk,
    make_file_complete,
    make_file_error,
)
from streamsync_cli.core.config import MAX_CHUNK_SIZE

logger = logging.getLogger(__name__)


class TransferStatus(Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


@dataclass
class TransferInfo:
    """Information about a file transfer."""

    transfer_id: str
    filename: str
    file_path: str = ""
    file_size: int = 0
    file_hash: str = ""
    mime_type: str = ""
    device_id: str = ""
    device_name: str = ""
    direction: str = ""  # "send" or "receive"
    status: TransferStatus = TransferStatus.PENDING
    bytes_transferred: int = 0
    total_chunks: int = 0
    chunks_received: int = 0
    chunk_size: int = MAX_CHUNK_SIZE
    error_message: str = ""
    started_at: Optional[float] = None
    completed_at: Optional[float] = None
    speed_bps: float = 0.0

    @property
    def progress(self) -> float:
        """Transfer progress as a percentage (0-100)."""
        if self.file_size == 0:
            return 0.0
        if self.bytes_transferred >= self.file_size:
            return 100.0
        return (self.bytes_transferred / self.file_size) * 100.0

    @property
    def elapsed_seconds(self) -> float:
        """Seconds since the transfer started."""
        if self.started_at is None:
            return 0.0
        end = self.completed_at or time.time()
        return end - self.started_at

    @property
    def eta_seconds(self) -> float:
        """Estimated remaining time in seconds."""
        if self.speed_bps <= 0 or self.bytes_transferred <= 0:
            return 0.0
        remaining = self.file_size - self.bytes_transferred
        return remaining / self.speed_bps

    @property
    def is_active(self) -> bool:
        return self.status in (TransferStatus.PENDING, TransferStatus.IN_PROGRESS)


class FileTransferManager:
    """Manages file transfers - both sending and receiving.

    Handles chunking, reassembly, progress tracking, and hash verification.
    """

    def __init__(self, download_dir: str = ""):
        self.download_dir = download_dir
        self._transfers: Dict[str, TransferInfo] = {}
        self._lock = threading.Lock()
        self._on_progress: Optional[Callable[[TransferInfo], None]] = None
        self._on_complete: Optional[Callable[[TransferInfo], None]] = None
        self._on_error: Optional[Callable[[TransferInfo, str], None]] = None
        self._transport = None  # Set after construction

    def set_transport(self, transport):
        """Set the transport for sending messages."""
        self._transport = transport

    def set_callbacks(
        self,
        on_progress: Optional[Callable[[TransferInfo], None]] = None,
        on_complete: Optional[Callable[[TransferInfo], None]] = None,
        on_error: Optional[Callable[[TransferInfo, str], None]] = None,
    ):
        """Set transfer event callbacks."""
        self._on_progress = on_progress
        self._on_complete = on_complete
        self._on_error = on_error

    def get_transfer(self, transfer_id: str) -> Optional[TransferInfo]:
        """Get transfer info by ID."""
        with self._lock:
            return self._transfers.get(transfer_id)

    def get_active_transfers(self) -> list:
        """Get all active transfers."""
        with self._lock:
            return [t for t in self._transfers.values() if t.is_active]

    def get_all_transfers(self) -> list:
        """Get all transfers."""
        with self._lock:
            return list(self._transfers.values())

    def send_file(
        self,
        file_path: str,
        device_id: str,
        device_name: str = "",
        transfer_id: str = "",
    ) -> Optional[str]:
        """Initiate sending a file to a device.

        Args:
            file_path: Path to the file to send.
            device_id: Target device ID.
            device_name: Target device name (for display).
            transfer_id: Optional transfer ID (generated if empty).

        Returns:
            Transfer ID if initiated, None on error.
        """
        path = Path(file_path)
        if not path.exists():
            logger.error("File not found: %s", file_path)
            return None
        if not path.is_file():
            logger.error("Not a file: %s", file_path)
            return None

        import uuid
        tid = transfer_id or str(uuid.uuid4())[:12]

        file_size = path.stat().st_size
        file_hash = self._compute_file_hash(str(path))

        info = TransferInfo(
            transfer_id=tid,
            filename=path.name,
            file_path=str(path.resolve()),
            file_size=file_size,
            file_hash=file_hash,
            device_id=device_id,
            device_name=device_name,
            direction="send",
            status=TransferStatus.PENDING,
            started_at=time.time(),
        )

        with self._lock:
            self._transfers[tid] = info

        # Send file request
        if self._transport:
            request = make_file_request(
                device_id=self._transport.device_id,
                device_name=self._transport.device_name,
                filename=path.name,
                file_size=file_size,
                file_hash=file_hash,
                mime_type=self._guess_mime(path.name),
            )
            self._transport.send_message(device_id, request)

        logger.info(
            "File transfer initiated: %s -> %s (%.1f MB)",
            path.name,
            device_name or device_id,
            file_size / (1024 * 1024),
        )
        return tid

    def start_sending_chunks(
        self, transfer_id: str, device_id: str
    ) -> bool:
        """Start sending file chunks for an accepted transfer."""
        info = self.get_transfer(transfer_id)
        if not info or info.direction != "send":
            return False

        info.status = TransferStatus.IN_PROGRESS
        file_path = info.file_path

        try:
            total_sent = 0
            chunk_index = 0
            chunk_size = info.chunk_size

            with open(file_path, "rb") as f:
                while True:
                    chunk = f.read(chunk_size)
                    if not chunk:
                        break

                    data_b64 = base64.b64encode(chunk).decode("ascii")
                    msg = make_file_chunk(
                        device_id=self._transport.device_id,
                        device_name=self._transport.device_name,
                        transfer_id=transfer_id,
                        chunk_index=chunk_index,
                        total_chunks=-1,  # unknown until end
                        data_b64=data_b64,
                    )
                    if self._transport:
                        self._transport.send_message(device_id, msg)

                    total_sent += len(chunk)
                    info.bytes_transferred = total_sent
                    info.chunks_received = chunk_index + 1

                    # Update speed
                    elapsed = info.elapsed_seconds
                    if elapsed > 0:
                        info.speed_bps = total_sent / elapsed

                    if self._on_progress:
                        self._on_progress(info)

                    chunk_index += 1

            # Send completion
            info.total_chunks = chunk_index
            complete_msg = make_file_complete(
                device_id=self._transport.device_id,
                device_name=self._transport.device_name,
                transfer_id=transfer_id,
                file_hash=info.file_hash,
            )
            if self._transport:
                self._transport.send_message(device_id, complete_msg)

            info.status = TransferStatus.COMPLETED
            info.completed_at = time.time()
            logger.info(
                "File sent: %s (%d chunks, %.1f MB)",
                info.filename,
                chunk_index,
                info.file_size / (1024 * 1024),
            )

            if self._on_complete:
                self._on_complete(info)

            return True

        except (OSError, IOError) as e:
            info.status = TransferStatus.FAILED
            info.error_message = str(e)
            if self._on_error:
                self._on_error(info, str(e))
            if self._transport:
                err_msg = make_file_error(
                    device_id=self._transport.device_id,
                    device_name=self._transport.device_name,
                    transfer_id=transfer_id,
                    error=str(e),
                )
                self._transport.send_message(device_id, err_msg)
            return False

    def receive_chunk(
        self,
        transfer_id: str,
        device_id: str,
        data_b64: str,
        chunk_index: int,
        total_chunks: int,
    ) -> bool:
        """Receive and store a file chunk, writing to disk.

        Returns:
            True if the chunk was stored successfully.
        """
        info = self.get_transfer(transfer_id)
        if not info:
            logger.warning("Received chunk for unknown transfer: %s", transfer_id)
            return False

        try:
            chunk = base64.b64decode(data_b64)
        except Exception as e:
            logger.error("Failed to decode chunk: %s", e)
            return False

        # Write chunk to a temp file
        temp_path = Path(info.file_path + ".part")
        try:
            with open(temp_path, "ab") as f:
                f.seek(chunk_index * info.chunk_size)
                f.write(chunk)
        except OSError as e:
            logger.error("Failed to write chunk: %s", e)
            return False

        info.bytes_transferred += len(chunk)
        info.chunks_received += 1
        if total_chunks > 0:
            info.total_chunks = total_chunks

        # Update speed
        elapsed = info.elapsed_seconds
        if elapsed > 0:
            info.speed_bps = info.bytes_transferred / elapsed

        if self._on_progress:
            self._on_progress(info)

        return True

    def complete_receive(
        self, transfer_id: str, expected_hash: str = ""
    ) -> bool:
        """Finalize a received file transfer.

        Renames .part file to final name and verifies hash.

        Returns:
            True if the transfer completed successfully.
        """
        info = self.get_transfer(transfer_id)
        if not info:
            return False

        temp_path = Path(info.file_path + ".part")
        final_path = Path(info.file_path)

        if not temp_path.exists():
            info.status = TransferStatus.FAILED
            info.error_message = "Temporary file not found"
            return False

        try:
            # Verify hash if provided
            if expected_hash:
                actual_hash = self._compute_file_hash(str(temp_path))
                if actual_hash != expected_hash:
                    logger.warning(
                        "Hash mismatch for %s: expected=%s, actual=%s",
                        info.filename,
                        expected_hash,
                        actual_hash,
                    )
                    # Still keep the file but mark warning

            # Rename .part to final
            temp_path.rename(final_path)

            info.status = TransferStatus.COMPLETED
            info.completed_at = time.time()

            logger.info(
                "File received: %s (%.1f MB)",
                info.filename,
                info.file_size / (1024 * 1024),
            )

            if self._on_complete:
                self._on_complete(info)

            return True

        except OSError as e:
            info.status = TransferStatus.FAILED
            info.error_message = str(e)
            if self._on_error:
                self._on_error(info, str(e))
            return False

    def init_receive(
        self,
        transfer_id: str,
        filename: str,
        file_size: int,
        device_id: str,
        device_name: str = "",
        file_hash: str = "",
        mime_type: str = "",
    ) -> TransferInfo:
        """Initialize a receive operation.

        Creates the transfer tracking info and prepares the output file.

        Returns:
            The TransferInfo for the new receive operation.
        """
        download_dir = Path(self.download_dir)
        download_dir.mkdir(parents=True, exist_ok=True)

        file_path = str(download_dir / filename)

        info = TransferInfo(
            transfer_id=transfer_id,
            filename=filename,
            file_path=file_path,
            file_size=file_size,
            file_hash=file_hash,
            mime_type=mime_type,
            device_id=device_id,
            device_name=device_name,
            direction="receive",
            status=TransferStatus.IN_PROGRESS,
            started_at=time.time(),
        )

        with self._lock:
            self._transfers[transfer_id] = info

        # Create empty .part file
        Path(file_path + ".part").parent.mkdir(parents=True, exist_ok=True)
        Path(file_path + ".part").write_bytes(b"")

        logger.info(
            "Receiving file: %s from %s (%.1f MB)",
            filename,
            device_name or device_id,
            file_size / (1024 * 1024),
        )

        return info

    def cancel_transfer(self, transfer_id: str) -> bool:
        """Cancel an active transfer."""
        info = self.get_transfer(transfer_id)
        if not info:
            return False

        info.status = TransferStatus.CANCELLED
        info.completed_at = time.time()

        # Clean up .part file
        part_path = Path(info.file_path + ".part")
        if part_path.exists():
            try:
                part_path.unlink()
            except OSError:
                pass

        logger.info("Transfer cancelled: %s", transfer_id)
        return True

    @staticmethod
    def _compute_file_hash(file_path: str) -> str:
        """Compute SHA-256 hash of a file."""
        h = hashlib.sha256()
        try:
            with open(file_path, "rb") as f:
                while True:
                    chunk = f.read(65536)
                    if not chunk:
                        break
                    h.update(chunk)
            return h.hexdigest()
        except OSError:
            return ""

    @staticmethod
    def _guess_mime(filename: str) -> str:
        """Guess MIME type from file extension."""
        ext = Path(filename).suffix.lower()
        mime_map = {
            ".mp4": "video/mp4",
            ".mkv": "video/x-matroska",
            ".avi": "video/x-msvideo",
            ".mov": "video/quicktime",
            ".webm": "video/webm",
            ".mp3": "audio/mpeg",
            ".wav": "audio/wav",
            ".flac": "audio/flac",
            ".ogg": "audio/ogg",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".png": "image/png",
            ".gif": "image/gif",
            ".webp": "image/webp",
            ".pdf": "application/pdf",
            ".zip": "application/zip",
            ".tar": "application/x-tar",
            ".gz": "application/gzip",
        }
        return mime_map.get(ext, "application/octet-stream")
