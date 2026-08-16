"""mDNS device discovery using zeroconf."""

import itertools
import json
import logging
import socket
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional, Set

from zeroconf import (
    IPVersion,
    ServiceBrowser,
    ServiceInfo,
    ServiceStateChange,
    Zeroconf,
)

from streamsync_cli.core.config import SERVICE_TYPE, DEFAULT_PORT

logger = logging.getLogger(__name__)

_DISCOVERY_RESPONSE_TEXT = json.dumps(
    {
        "protocol": "streamsync",
        "version": 1,
    }
).encode("utf-8")


@dataclass
class DeviceInfo:
    """Information about a discovered StreamSync device."""

    device_id: str
    device_name: str
    host: str
    port: int = DEFAULT_PORT
    ip_addresses: List[str] = field(default_factory=list)
    version: int = 1
    first_seen: float = field(default_factory=time.time)
    last_seen: float = field(default_factory=time.time)
    is_local: bool = False

    @property
    def address(self) -> str:
        """Return the best address to connect to."""
        if self.ip_addresses:
            return self.ip_addresses[0]
        return self.host

    @property
    def age_seconds(self) -> float:
        """Seconds since this device was last seen."""
        return time.time() - self.last_seen

    @property
    def is_expired(self, timeout: float = 30.0) -> bool:
        """Check if this device is considered expired."""
        return self.age_seconds > timeout


class DeviceDiscovery:
    """mDNS-based device discovery using zeroconf.

    Discovers StreamSync devices on the local network and tracks
    their availability.
    """

    def __init__(
        self,
        service_type: str = SERVICE_TYPE,
        device_id: str = "",
        device_name: str = "",
        port: int = DEFAULT_PORT,
    ):
        self.service_type = service_type
        self.device_id = device_id
        self.device_name = device_name
        self.port = port

        self._zeroconf: Optional[Zeroconf] = None
        self._browser: Optional[ServiceBrowser] = None
        self._running = False
        self._lock = threading.Lock()
        self._devices: Dict[str, DeviceInfo] = {}
        self._on_device_discovered: Optional[Callable[[DeviceInfo], None]] = None
        self._on_device_lost: Optional[Callable[[DeviceInfo], None]] = None
        self._thread: Optional[threading.Thread] = None
        self._service_info: Optional[ServiceInfo] = None

    @property
    def devices(self) -> List[DeviceInfo]:
        """Get list of currently active devices (non-expired)."""
        with self._lock:
            now = time.time()
            active = [
                d for d in self._devices.values() if now - d.last_seen <= 60.0
            ]
            return sorted(active, key=lambda d: d.device_name.lower())

    def get_device(self, device_id: str) -> Optional[DeviceInfo]:
        """Get device info by device_id."""
        with self._lock:
            return self._devices.get(device_id)

    def set_callbacks(
        self,
        on_discovered: Optional[Callable[[DeviceInfo], None]] = None,
        on_lost: Optional[Callable[[DeviceInfo], None]] = None,
    ):
        """Set callbacks for device discovery events."""
        self._on_device_discovered = on_discovered
        self._on_device_lost = on_lost

    def start(self) -> bool:
        """Start mDNS discovery.

        Returns:
            True if started successfully, False otherwise.
        """
        if self._running:
            return True

        try:
            self._zeroconf = Zeroconf(ip_version=IPVersion.V4Only)

            # Register our own service so we're discoverable
            self._register_own_service()

            # Start browsing for other services
            self._browser = ServiceBrowser(
                self._zeroconf,
                self.service_type,
                handlers=[self._on_service_state_change],
            )

            self._running = True
            logger.info(
                "Device discovery started on %s (device: %s)",
                self.service_type,
                self.device_name or self.device_id,
            )
            return True

        except OSError as e:
            logger.error("Failed to start mDNS discovery: %s", e)
            self._cleanup()
            return False

    def _register_own_service(self):
        """Register this device as a StreamSync service on the network."""
        if not self.device_id:
            return

        hostname = socket.gethostname()
        local_ip = self._get_local_ip()

        # Build service properties
        props = {
            "device_id": self.device_id.encode("utf-8"),
            "device_name": (self.device_name or hostname).encode("utf-8"),
            "version": b"1",
        }

        self._service_info = ServiceInfo(
            type_=self.service_type,
            name=f"{self.device_id}.{self.service_type}",
            addresses=[socket.inet_aton(local_ip)] if local_ip else [],
            port=self.port,
            weight=0,
            priority=0,
            properties=props,
            server=f"{hostname}.local.",
        )

        try:
            self._zeroconf.register_service(
                self._service_info,
                cooperating_servers=[],
            )
            logger.debug("Registered own service: %s", self._service_info.name)
        except OSError as e:
            logger.warning("Could not register own service: %s", e)

    def _get_local_ip(self) -> str:
        """Get the local IP address."""
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("10.255.255.255", 1))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except OSError:
            return "127.0.0.1"

    def _on_service_state_change(
        self,
        zeroconf: Zeroconf,
        service_type: str,
        name: str,
        state_change: ServiceStateChange,
    ):
        """Handle mDNS service state changes."""
        if state_change == ServiceStateChange.Added:
            self._add_service(zeroconf, service_type, name)
        elif state_change == ServiceStateChange.Removed:
            self._remove_service(name)

    def _add_service(self, zeroconf: Zeroconf, service_type: str, name: str):
        """Process a newly discovered service."""
        try:
            info = zeroconf.get_service_info(service_type, name)
            if info is None:
                return

            # Extract properties
            device_id = self._decode_prop(info.properties, b"device_id", name.split(".")[0])
            device_name = self._decode_prop(
                info.properties, b"device_name", device_id
            )
            version_str = self._decode_prop(info.properties, b"version", "1")

            # Skip our own device
            if device_id == self.device_id:
                return

            # Get IP addresses
            ip_addresses = []
            if info.addresses:
                for addr_bytes in info.addresses:
                    try:
                        ip = socket.inet_ntoa(addr_bytes)
                        if not ip.startswith("127."):
                            ip_addresses.append(ip)
                    except OSError:
                        continue

            device = DeviceInfo(
                device_id=device_id,
                device_name=device_name,
                host=info.server or name,
                port=info.port or DEFAULT_PORT,
                ip_addresses=ip_addresses,
                version=int(version_str) if version_str.isdigit() else 1,
                last_seen=time.time(),
            )

            with self._lock:
                is_new = device_id not in self._devices
                self._devices[device_id] = device

            if is_new:
                logger.info("Discovered device: %s (%s)", device_name, device_id)
                if self._on_device_discovered:
                    try:
                        self._on_device_discovered(device)
                    except Exception:
                        logger.exception("Device discovered callback failed")
            else:
                logger.debug("Updated device: %s (%s)", device_name, device_id)

        except Exception as e:
            logger.warning("Error processing discovered service %s: %s", name, e)

    def _remove_service(self, name: str):
        """Process a removed service."""
        device_id = name.split(".")[0]
        with self._lock:
            if device_id in self._devices:
                device = self._devices.pop(device_id)
                logger.info("Device lost: %s (%s)", device.device_name, device_id)
                if self._on_device_lost:
                    try:
                        self._on_device_lost(device)
                    except Exception:
                        logger.exception("Device lost callback failed")

    @staticmethod
    def _decode_prop(props: dict, key: bytes, default: str = "") -> str:
        """Decode a zeroconf property value."""
        val = props.get(key)
        if val is None:
            return default
        if isinstance(val, bytes):
            return val.decode("utf-8", errors="replace")
        return str(val)

    def stop(self):
        """Stop mDNS discovery and unregister the service."""
        self._running = False
        self._cleanup()
        logger.info("Device discovery stopped")

    def _cleanup(self):
        """Clean up zeroconf resources."""
        try:
            if self._service_info and self._zeroconf:
                try:
                    self._zeroconf.unregister_service(self._service_info)
                except Exception:
                    pass
                self._service_info = None
        except Exception:
            pass

        try:
            if self._browser:
                self._browser.cancel()
                self._browser = None
        except Exception:
            pass

        try:
            if self._zeroconf:
                self._zeroconf.close()
                self._zeroconf = None
        except Exception:
            pass

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, *args):
        self.stop()
