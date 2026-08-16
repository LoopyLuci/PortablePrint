"""Content streaming module - VLC integration and subprocess-based media streaming."""

import asyncio
import logging
import mimetypes
import os
import platform
import shutil
import subprocess
import threading
import time
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Callable, Dict, Optional

from streamsync_cli.core.config import STREAM_BUFFER_SIZE

logger = logging.getLogger(__name__)


class StreamStatus(Enum):
    STOPPED = "stopped"
    PLAYING = "playing"
    PAUSED = "paused"
    BUFFERING = "buffering"
    ERROR = "error"


@dataclass
class StreamInfo:
    """Information about an active media stream."""

    stream_id: str
    filename: str
    file_path: str = ""
    mime_type: str = ""
    device_id: str = ""
    status: StreamStatus = StreamStatus.STOPPED
    position: float = 0.0  # 0.0 to 1.0
    duration: float = 0.0  # seconds
    volume: int = 100
    started_at: Optional[float] = None
    error_message: str = ""


class MediaStreamer:
    """Media streaming engine.

    Supports VLC (via python-vlc bindings), MPV (via subprocess),
    and a fallback HTTP streaming server for web clients.
    """

    def __init__(self, stream_port: int = 9473):
        self.stream_port = stream_port
        self._active_streams: Dict[str, StreamInfo] = {}
        self._lock = threading.Lock()
        self._vlc_instance = None
        self._vlc_player = None
        self._mpv_process: Optional[subprocess.Popen] = None
        self._http_server = None
        self._server_thread: Optional[threading.Thread] = None
        self._on_status_change: Optional[Callable[[StreamInfo], None]] = None

        # Detect available players
        self._vlc_available = self._check_vlc()
        self._mpv_available = self._check_mpv()

    def set_callback(self, callback: Callable[[StreamInfo], None]):
        """Set stream status change callback."""
        self._on_status_change = callback

    @staticmethod
    def _check_vlc() -> bool:
        """Check if python-vlc is available and VLC is installed."""
        try:
            import vlc  # noqa: F401
            return True
        except ImportError:
            pass
        # Also check for vlc executable
        return shutil.which("vlc") is not None or shutil.which("vlc.exe") is not None

    @staticmethod
    def _check_mpv() -> bool:
        """Check if MPV is available."""
        return shutil.which("mpv") is not None

    def start_streaming(
        self, file_path: str, method: str = "auto"
    ) -> Optional[str]:
        """Start streaming a media file.

        Args:
            file_path: Path to the media file.
            method: 'vlc', 'mpv', 'http', or 'auto'.

        Returns:
            Stream ID if started, None on error.
        """
        path = Path(file_path)
        if not path.exists():
            logger.error("Stream file not found: %s", file_path)
            return None

        import uuid
        stream_id = str(uuid.uuid4())[:8]
        mime_type, _ = mimetypes.guess_type(str(path))

        info = StreamInfo(
            stream_id=stream_id,
            filename=path.name,
            file_path=str(path.resolve()),
            mime_type=mime_type or "application/octet-stream",
            status=StreamStatus.PLAYING,
            started_at=time.time(),
        )

        with self._lock:
            self._active_streams[stream_id] = info

        # Choose method
        if method == "auto":
            if self._vlc_available:
                method = "vlc"
            elif self._mpv_available:
                method = "mpv"
            else:
                method = "http"

        success = False
        if method == "vlc":
            success = self._stream_vlc(info)
        elif method == "mpv":
            success = self._stream_mpv(info)
        else:
            success = self._stream_http(info)

        if not success:
            info.status = StreamStatus.ERROR
            info.error_message = f"Failed to stream via {method}"
            return None

        if self._on_status_change:
            self._on_status_change(info)

        return stream_id

    def _stream_vlc(self, info: StreamInfo) -> bool:
        """Stream using VLC media player."""
        try:
            import vlc  # type: ignore

            if self._vlc_instance is None:
                self._vlc_instance = vlc.Instance(
                    f"--no-xlib --intf dummy --quiet"
                )

            player = self._vlc_instance.media_player_new()
            media = self._vlc_instance.media_new(info.file_path)

            # Stream over HTTP
            media.add_option(f":sout=#http{{mux=ts,dst=:{self.stream_port}/stream}}")
            media.add_option(":sout-keep")
            media.add_option(":no-sout-all")
            media.add_option(":sout-audio")

            player.set_media(media)
            player.play()

            self._vlc_player = player
            info.status = StreamStatus.PLAYING

            # Start monitoring thread
            threading.Thread(
                target=self._monitor_vlc,
                args=(info, player),
                daemon=True,
            ).start()

            logger.info(
                "VLC streaming %s on port %s",
                info.filename,
                self.stream_port,
            )
            return True

        except ImportError:
            logger.warning("python-vlc not available, trying subprocess VLC")
            return self._stream_vlc_subprocess(info)
        except Exception as e:
            logger.error("VLC streaming failed: %s", e)
            return False

    def _stream_vlc_subprocess(self, info: StreamInfo) -> bool:
        """Stream using VLC via subprocess."""
        vlc_exe = shutil.which("vlc") or shutil.which("vlc.exe")
        if not vlc_exe:
            logger.error("VLC executable not found")
            return False

        sout = (
            f"#http{{mux=ts,dst=:{self.stream_port}/stream}}"
        )

        cmd = [
            vlc_exe,
            "-I", "dummy",
            "--no-video-title-show",
            "--quiet",
            info.file_path,
            f":sout={sout}",
            ":sout-keep",
            ":no-sout-all",
        ]

        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            self._vlc_player = process  # Store as process
            info.status = StreamStatus.PLAYING

            threading.Thread(
                target=self._monitor_process,
                args=(info, process),
                daemon=True,
            ).start()

            logger.info(
                "VLC (subprocess) streaming %s on port %s",
                info.filename,
                self.stream_port,
            )
            return True

        except OSError as e:
            logger.error("Failed to launch VLC: %s", e)
            return False

    def _stream_mpv(self, info: StreamInfo) -> bool:
        """Stream using MPV media player."""
        mpv_exe = shutil.which("mpv")
        if not mpv_exe:
            logger.error("MPV executable not found")
            return False

        cmd = [
            mpv_exe,
            "--no-video",
            "--vo=null",
            f"--audio-file={info.file_path}",
            f"--stream-record={os.devnull}",
            info.file_path,
        ]

        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            self._mpv_process = process
            info.status = StreamStatus.PLAYING

            threading.Thread(
                target=self._monitor_process,
                args=(info, process),
                daemon=True,
            ).start()

            logger.info("MPV streaming %s", info.filename)
            return True

        except OSError as e:
            logger.error("Failed to launch MPV: %s", e)
            return False

    def _stream_http(self, info: StreamInfo) -> bool:
        """Stream via a simple HTTP server (for web clients)."""
        import http.server
        import socketserver

        class StreamingHandler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *args, file_path=info.file_path, **kwargs):
                self._file_path = file_path
                super().__init__(*args, **kwargs)

            def do_GET(self):
                file_size = Path(self._file_path).stat().st_size
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header(
                    "Content-Disposition",
                    f'attachment; filename="{Path(self._file_path).name}"',
                )
                self.send_header("Content-Length", str(file_size))
                self.send_header("Accept-Ranges", "bytes")
                self.end_headers()

                with open(self._file_path, "rb") as f:
                    while True:
                        chunk = f.read(STREAM_BUFFER_SIZE)
                        if not chunk:
                            break
                        try:
                            self.wfile.write(chunk)
                        except OSError:
                            break

            def log_message(self, fmt, *args):
                logger.debug("HTTP stream: %s", fmt % args)

        class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
            allow_reuse_address = True
            daemon_threads = True

        try:
            server = ThreadedHTTPServer(
                ("0.0.0.0", self.stream_port),
                lambda *args, **kwargs: StreamingHandler(
                    *args, file_path=info.file_path, **kwargs
                ),
            )
            self._http_server = server
            info.status = StreamStatus.PLAYING

            self._server_thread = threading.Thread(
                target=server.serve_forever,
                daemon=True,
            )
            self._server_thread.start()

            logger.info(
                "HTTP streaming %s on http://0.0.0.0:%s",
                info.filename,
                self.stream_port,
            )
            return True

        except OSError as e:
            logger.error("HTTP server failed: %s", e)
            return False

    def _monitor_vlc(self, info: StreamInfo, player):
        """Monitor VLC player state."""
        try:
            while info.status == StreamStatus.PLAYING:
                state = player.get_state()
                if state in (6, 7):  # ended, error
                    info.status = StreamStatus.STOPPED
                    if self._on_status_change:
                        self._on_status_change(info)
                    break
                info.position = player.get_position()
                time.sleep(1)
        except Exception:
            pass
        finally:
            try:
                player.stop()
            except Exception:
                pass

    def _monitor_process(self, info: StreamInfo, process: subprocess.Popen):
        """Monitor a subprocess player."""
        try:
            process.wait()
        except Exception:
            pass
        finally:
            info.status = StreamStatus.STOPPED
            if self._on_status_change:
                self._on_status_change(info)

    def stop_streaming(self, stream_id: str) -> bool:
        """Stop an active stream."""
        with self._lock:
            info = self._active_streams.get(stream_id)
            if not info:
                return False

        if self._vlc_player:
            try:
                if hasattr(self._vlc_player, 'stop'):
                    self._vlc_player.stop()  # type: ignore
            except Exception:
                pass
            self._vlc_player = None

        if self._mpv_process:
            try:
                self._mpv_process.terminate()
            except Exception:
                pass
            self._mpv_process = None

        if self._http_server:
            try:
                self._http_server.shutdown()
            except Exception:
                pass
            self._http_server = None

        info.status = StreamStatus.STOPPED
        if self._on_status_change:
            self._on_status_change(info)

        return True

    def stop_all(self):
        """Stop all active streams."""
        with self._lock:
            stream_ids = list(self._active_streams.keys())
        for sid in stream_ids:
            self.stop_streaming(sid)

    def get_stream(self, stream_id: str) -> Optional[StreamInfo]:
        """Get stream info by ID."""
        with self._lock:
            return self._active_streams.get(stream_id)

    def get_active_streams(self) -> list:
        """Get all active streams."""
        with self._lock:
            return [
                s for s in self._active_streams.values()
                if s.status == StreamStatus.PLAYING
            ]

    @property
    def has_player(self) -> bool:
        """Check if any media player is available."""
        return self._vlc_available or self._mpv_available

    @property
    def stream_url(self) -> str:
        """Get the HTTP stream URL."""
        return f"http://localhost:{self.stream_port}/stream"
