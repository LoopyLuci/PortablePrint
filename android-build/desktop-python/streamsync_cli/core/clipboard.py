"""Clipboard synchronization service using pyperclip."""

import hashlib
import logging
import threading
import time
from typing import Callable, Optional

from streamsync_cli.core.protocol import make_clipboard_push

logger = logging.getLogger(__name__)


class ClipboardSync:
    """Clipboard synchronization service.

    Monitors local clipboard changes and broadcasts them to connected
    devices. Also receives remote clipboard updates and applies them
    locally.
    """

    def __init__(self, poll_interval: float = 1.0):
        self.poll_interval = poll_interval
        self._enabled = True
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._last_content: str = ""
        self._last_hash: str = ""
        self._lock = threading.Lock()
        self._transport = None
        self._device_id: str = ""
        self._device_name: str = ""

        # Whether we're currently applying a remote clipboard update
        # (to avoid echo)
        self._applying_remote = False

        self._on_change: Optional[Callable[[str], None]] = None

    def set_transport(self, transport, device_id: str = "", device_name: str = ""):
        """Set the transport for sending clipboard updates."""
        self._transport = transport
        self._device_id = device_id
        self._device_name = device_name

    def set_callback(self, callback: Callable[[str], None]):
        """Set callback for local clipboard changes."""
        self._on_change = callback

    @property
    def enabled(self) -> bool:
        return self._enabled

    def set_enabled(self, enabled: bool):
        """Enable or disable clipboard sync."""
        self._enabled = enabled
        logger.info("Clipboard sync %s", "enabled" if enabled else "disabled")

    def start(self) -> bool:
        """Start the clipboard monitoring thread."""
        if self._running:
            return True

        # Test pyperclip availability
        try:
            import pyperclip  # noqa: F401
        except ImportError:
            logger.warning("pyperclip not available, clipboard sync disabled")
            return False

        self._running = True
        self._thread = threading.Thread(
            target=self._monitor_loop,
            daemon=True,
            name="clipboard-sync",
        )
        self._thread.start()
        logger.info("Clipboard sync started (poll interval: %.1fs)", self.poll_interval)
        return True

    def _monitor_loop(self):
        """Main monitoring loop - polls clipboard for changes."""
        import pyperclip

        # Initialize with current clipboard content
        try:
            self._last_content = pyperclip.paste()
            self._last_hash = self._hash_content(self._last_content)
        except Exception:
            self._last_content = ""
            self._last_hash = ""

        while self._running:
            try:
                time.sleep(self.poll_interval)

                if not self._enabled or self._applying_remote:
                    self._applying_remote = False
                    continue

                current = pyperclip.paste()
                current_hash = self._hash_content(current)

                if current_hash and current_hash != self._last_hash:
                    self._last_content = current
                    self._last_hash = current_hash
                    self._on_local_change(current)

            except pyperclip.PyperclipException as e:
                logger.debug("Clipboard access error: %s", e)
                time.sleep(5)  # Back off on errors
            except Exception as e:
                logger.warning("Clipboard monitor error: %s", e)
                time.sleep(5)

    def _on_local_change(self, content: str):
        """Handle a local clipboard change."""
        logger.debug("Local clipboard changed (%d chars)", len(content))

        if self._on_change:
            try:
                self._on_change(content)
            except Exception:
                logger.exception("Clipboard change callback failed")

        # Broadcast to connected devices
        if self._transport and content:
            msg = make_clipboard_push(
                device_id=self._device_id,
                device_name=self._device_name,
                content=content,
            )
            self._transport.broadcast(msg)

    def apply_remote_clipboard(self, content: str):
        """Apply a clipboard update received from a remote device."""
        if not self._enabled or not content:
            return

        self._applying_remote = True

        try:
            import pyperclip

            current = pyperclip.paste()
            if self._hash_content(current) != self._hash_content(content):
                pyperclip.copy(content)
                self._last_content = content
                self._last_hash = self._hash_content(content)
                logger.info("Remote clipboard applied (%d chars)", len(content))
        except pyperclip.PyperclipException as e:
            logger.warning("Failed to apply remote clipboard: %s", e)
        except Exception as e:
            logger.warning("Clipboard apply error: %s", e)

    def stop(self):
        """Stop the clipboard monitoring thread."""
        self._running = False
        logger.info("Clipboard sync stopped")

    @staticmethod
    def _hash_content(content: str) -> str:
        """Compute a hash of clipboard content for change detection."""
        if not content:
            return ""
        return hashlib.sha256(content.encode("utf-8")).hexdigest()[:16]

    def get_current_content(self) -> str:
        """Get the current clipboard content."""
        return self._last_content
