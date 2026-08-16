"""Configuration management for StreamSync."""

import json
import logging
import os
import platform
import sys
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

# Default port for StreamSync WebSocket connections
DEFAULT_PORT = 9472
# Default service type for mDNS discovery
SERVICE_TYPE = "_streamsync._tcp.local."
# Protocol version
PROTOCOL_VERSION = 1
# Maximum chunk size for file transfers (4 MB)
MAX_CHUNK_SIZE = 4 * 1024 * 1024
# Buffer size for streaming
STREAM_BUFFER_SIZE = 256 * 1024


def get_config_dir() -> Path:
    """Get the platform-appropriate configuration directory."""
    system = platform.system()
    if system == "Windows":
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
    elif system == "Darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return base / "streamsync"


def get_data_dir() -> Path:
    """Get the platform-appropriate data directory."""
    system = platform.system()
    if system == "Windows":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    elif system == "Darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    return base / "streamsync"


@dataclass
class StreamSyncConfig:
    """Application configuration with defaults."""

    # Network
    port: int = DEFAULT_PORT
    service_type: str = SERVICE_TYPE
    protocol_version: int = PROTOCOL_VERSION

    # File transfer
    download_dir: str = ""
    max_chunk_size: int = MAX_CHUNK_SIZE
    buffer_size: int = STREAM_BUFFER_SIZE
    max_concurrent_transfers: int = 4
    auto_accept_transfers: bool = False

    # Streaming
    stream_port: int = DEFAULT_PORT + 1
    player_command: str = ""
    stream_buffer_size: int = STREAM_BUFFER_SIZE

    # Clipboard
    clipboard_poll_interval: float = 1.0
    clipboard_enabled: bool = True

    # Discovery
    discovery_interval: float = 5.0
    device_timeout: float = 30.0

    # Daemon
    daemon_enabled: bool = False
    daemon_autostart: bool = False

    # Security
    encryption_enabled: bool = True
    encryption_key: str = ""
    require_auth: bool = False
    auth_token: str = ""

    # UI
    theme: str = "dark"
    minimize_to_tray: bool = True
    notifications_enabled: bool = True

    # Identity
    device_name: str = field(default_factory=lambda: f"{platform.node()}-streamsync")
    device_id: str = ""

    @classmethod
    def load(cls, config_path: Optional[Path] = None) -> "StreamSyncConfig":
        """Load configuration from file, merging with defaults."""
        if config_path is None:
            config_path = get_config_dir() / "config.json"

        # Accept both Path and str
        config_path = Path(config_path) if not isinstance(config_path, Path) else config_path

        config = cls()

        if config_path.exists():
            try:
                with open(config_path, "r") as f:
                    data = json.load(f)
                for key, value in data.items():
                    if hasattr(config, key):
                        setattr(config, key, value)
                logger.info("Configuration loaded from %s", config_path)
            except (json.JSONDecodeError, OSError) as e:
                logger.warning("Failed to load config from %s: %s", config_path, e)
        else:
            logger.info("No config file found at %s, using defaults", config_path)

        # Ensure device_id is set
        if not config.device_id:
            import uuid
            config.device_id = str(uuid.uuid4())[:8]

        # Ensure download_dir
        if not config.download_dir:
            config.download_dir = str(Path.home() / "Downloads" / "StreamSync")

        return config

    def save(self, config_path: Optional[Path] = None) -> bool:
        """Save configuration to file."""
        if config_path is None:
            config_dir = get_config_dir()
            config_dir.mkdir(parents=True, exist_ok=True)
            config_path = config_dir / "config.json"

        # Accept both Path and str
        config_path = Path(config_path) if not isinstance(config_path, Path) else config_path

        try:
            config_path.parent.mkdir(parents=True, exist_ok=True)
            with open(config_path, "w") as f:
                json.dump(asdict(self), f, indent=2, default=str)
            logger.info("Configuration saved to %s", config_path)
            return True
        except OSError as e:
            logger.error("Failed to save config to %s: %s", config_path, e)
            return False

    def get_download_dir(self) -> Path:
        """Get the download directory, creating it if needed."""
        path = Path(self.download_dir)
        path.mkdir(parents=True, exist_ok=True)
        return path

    def get_log_dir(self) -> Path:
        """Get the log directory."""
        log_dir = get_data_dir() / "logs"
        log_dir.mkdir(parents=True, exist_ok=True)
        return log_dir
