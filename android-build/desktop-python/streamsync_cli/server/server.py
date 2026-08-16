"""Background server daemon for StreamSync.

Handles incoming connections, file transfers, and streams
when running as a background service.
"""

import asyncio
import logging
import os
import signal
import sys
import threading
import time
from pathlib import Path
from typing import Optional

from streamsync_cli.core.config import StreamSyncConfig, DEFAULT_PORT
from streamsync_cli.core.discovery import DeviceDiscovery
from streamsync_cli.core.transport import WebSocketTransport, Connection
from streamsync_cli.core.transfer import FileTransferManager
from streamsync_cli.core.streaming import MediaStreamer
from streamsync_cli.core.clipboard import ClipboardSync
from streamsync_cli.core.protocol import (
    StreamSyncMessage,
    make_file_accept,
    make_file_reject,
    make_file_complete,
    make_file_error,
    make_stream_accept,
    make_stream_reject,
)

logger = logging.getLogger(__name__)


class StreamSyncDaemon:
    """Background server daemon for StreamSync.

    Runs the WebSocket server, mDNS discovery, file transfer manager,
    media streamer, and clipboard sync as a unified background service.
    """

    def __init__(self, config: Optional[StreamSyncConfig] = None):
        self.config = config or StreamSyncConfig.load()
        self._running = False
        self._thread: Optional[threading.Thread] = None

        # Components
        self.discovery: Optional[DeviceDiscovery] = None
        self.transport: Optional[WebSocketTransport] = None
        self.transfer_manager: Optional[FileTransferManager] = None
        self.streamer: Optional[MediaStreamer] = None
        self.clipboard: Optional[ClipboardSync] = None

    def start(self):
        """Start all daemon components."""
        if self._running:
            logger.warning("Daemon already running")
            return

        self._running = True

        # Initialize config
        device_id = self.config.device_id
        device_name = self.config.device_name

        # File transfer manager
        self.transfer_manager = FileTransferManager(
            download_dir=self.config.download_dir
        )

        # Media streamer
        self.streamer = MediaStreamer(stream_port=self.config.stream_port)

        # WebSocket transport
        self.transport = WebSocketTransport(
            device_id=device_id,
            device_name=device_name,
        )
        self.transfer_manager.set_transport(self.transport)

        # Register message handlers
        self._register_handlers()

        # Start transport server
        if not self.transport.start_server(port=self.config.port):
            logger.error("Failed to start transport server")
            self._running = False
            return

        # Start mDNS discovery
        self.discovery = DeviceDiscovery(
            device_id=device_id,
            device_name=device_name,
            port=self.config.port,
        )
        self.discovery.start()

        # Clipboard sync
        if self.config.clipboard_enabled:
            self.clipboard = ClipboardSync(
                poll_interval=self.config.clipboard_poll_interval
            )
            self.clipboard.set_transport(
                self.transport, device_id=device_id, device_name=device_name
            )
            self.clipboard.start()

        logger.info(
            "StreamSync daemon started (device: %s, port: %s)",
            device_name,
            self.config.port,
        )

    def _register_handlers(self):
        """Register WebSocket message handlers."""
        transport = self.transport
        if not transport:
            return

        # File request handler
        def on_file_request(conn: Connection, msg: StreamSyncMessage):
            payload = msg.payload
            filename = payload.get("filename", "unknown")
            file_size = payload.get("file_size", 0)
            file_hash = payload.get("file_hash", "")
            mime_type = payload.get("mime_type", "")
            transfer_id = msg.message_id or "unknown"

            if self.config.auto_accept_transfers:
                # Auto-accept
                accept = make_file_accept(
                    device_id=self.config.device_id,
                    device_name=self.config.device_name,
                    transfer_id=transfer_id,
                )
                self.transport.send_message(conn.device_id, accept)

                # Initialize receive
                self.transfer_manager.init_receive(
                    transfer_id=transfer_id,
                    filename=filename,
                    file_size=file_size,
                    device_id=conn.device_id,
                    device_name=conn.device_name,
                    file_hash=file_hash,
                    mime_type=mime_type,
                )
            else:
                # Queue for user approval (UI will pick up)
                logger.info(
                    "File request from %s: %s (%.1f MB)",
                    conn.device_name,
                    filename,
                    file_size / (1024 * 1024),
                )

        transport.on_message("file_request", on_file_request)

        # File accept handler
        def on_file_accept(conn: Connection, msg: StreamSyncMessage):
            transfer_id = msg.payload.get("transfer_id", "")
            if transfer_id:
                logger.info(
                    "File transfer accepted: %s by %s",
                    transfer_id,
                    conn.device_name,
                )
                # Start sending chunks in a background thread
                threading.Thread(
                    target=self.transfer_manager.start_sending_chunks,
                    args=(transfer_id, conn.device_id),
                    daemon=True,
                ).start()

        transport.on_message("file_accept", on_file_accept)

        # File reject handler
        def on_file_reject(conn: Connection, msg: StreamSyncMessage):
            transfer_id = msg.payload.get("transfer_id", "")
            reason = msg.payload.get("reason", "Rejected")
            logger.info("File transfer rejected: %s (%s)", transfer_id, reason)
            if transfer_id:
                self.transfer_manager.cancel_transfer(transfer_id)

        transport.on_message("file_reject", on_file_reject)

        # File chunk handler
        def on_file_chunk(conn: Connection, msg: StreamSyncMessage):
            payload = msg.payload
            transfer_id = payload.get("transfer_id", "")
            data_b64 = payload.get("data", "")
            chunk_index = payload.get("chunk_index", 0)
            total_chunks = payload.get("total_chunks", 0)

            if transfer_id:
                self.transfer_manager.receive_chunk(
                    transfer_id,
                    conn.device_id,
                    data_b64,
                    chunk_index,
                    total_chunks,
                )

        transport.on_message("file_chunk", on_file_chunk)

        # File complete handler
        def on_file_complete(conn: Connection, msg: StreamSyncMessage):
            payload = msg.payload
            transfer_id = payload.get("transfer_id", "")
            file_hash = payload.get("file_hash", "")
            if transfer_id:
                self.transfer_manager.complete_receive(transfer_id, file_hash)

        transport.on_message("file_complete", on_file_complete)

        # File error handler
        def on_file_error(conn: Connection, msg: StreamSyncMessage):
            transfer_id = msg.payload.get("transfer_id", "")
            error = msg.payload.get("error", "Unknown error")
            logger.error("File transfer error: %s - %s", transfer_id, error)

        transport.on_message("file_error", on_file_error)

        # Clipboard push handler
        def on_clipboard_push(conn: Connection, msg: StreamSyncMessage):
            content = msg.payload.get("content", "")
            if self.clipboard and content:
                self.clipboard.apply_remote_clipboard(content)

        transport.on_message("clipboard_push", on_clipboard_push)

    def stop(self):
        """Stop all daemon components gracefully."""
        self._running = False

        if self.clipboard:
            self.clipboard.stop()
        if self.streamer:
            self.streamer.stop_all()
        if self.discovery:
            self.discovery.stop()
        if self.transport:
            self.transport.stop()

        logger.info("StreamSync daemon stopped")

    def run_in_background(self) -> threading.Thread:
        """Run the daemon in a background thread.

        Returns:
            The daemon thread.
        """
        self._thread = threading.Thread(
            target=self._run_forever,
            daemon=True,
            name="streamsync-daemon",
        )
        self._thread.start()
        return self._thread

    def _run_forever(self):
        """Run the daemon, keeping the main thread alive."""
        self.start()
        try:
            while self._running:
                time.sleep(1)
        except KeyboardInterrupt:
            pass
        finally:
            self.stop()

    @property
    def is_running(self) -> bool:
        return self._running
