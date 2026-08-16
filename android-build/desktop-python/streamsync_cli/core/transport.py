"""WebSocket transport layer using the websockets library."""

import asyncio
import json
import logging
import ssl
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Dict, Optional, Set, Awaitable, Any

import websockets
from websockets.asyncio.server import ServerConnection, serve
from websockets.asyncio.client import connect

from streamsync_cli.core.protocol import (
    StreamSyncMessage,
    ProtocolError,
    make_hello,
    make_ping,
    make_pong,
    make_bye,
)

logger = logging.getLogger(__name__)

# Maximum message size for WebSocket frames (50 MB)
MAX_FRAME_SIZE = 50 * 1024 * 1024


class TransportError(Exception):
    """Raised when a transport-level error occurs."""
    pass


@dataclass
class Connection:
    """Represents an active WebSocket connection."""

    websocket: Any  # ServerConnection or WebSocket client
    device_id: str
    device_name: str = ""
    remote_address: str = ""
    connected_at: float = field(default_factory=time.time)
    last_activity: float = field(default_factory=time.time)
    is_server_side: bool = False

    @property
    def is_alive(self) -> bool:
        """Check if the connection is still alive."""
        try:
            return not self.websocket.closed
        except Exception:
            return False

    @property
    def idle_seconds(self) -> float:
        return time.time() - self.last_activity


class WebSocketTransport:
    """WebSocket transport layer for StreamSync.

    Handles both client and server WebSocket connections with
    automatic reconnection, keepalive, and message routing.
    """

    def __init__(self, device_id: str, device_name: str = ""):
        self.device_id = device_id
        self.device_name = device_name

        self._server: Optional[serve] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._thread: Optional[threading.Thread] = None
        self._running = False

        # Active connections: device_id -> Connection
        self._connections: Dict[str, Connection] = {}
        self._lock = threading.Lock()

        # Message handlers: type -> callable
        self._handlers: Dict[str, Callable] = {}

        # Connection callbacks
        self._on_connect: Optional[Callable[[Connection], None]] = None
        self._on_disconnect: Optional[Callable[[Connection], None]] = None

        # Keepalive
        self._keepalive_task: Optional[asyncio.Task] = None
        self._keepalive_interval = 15.0

    def set_connection_callbacks(
        self,
        on_connect: Optional[Callable[[Connection], None]] = None,
        on_disconnect: Optional[Callable[[Connection], None]] = None,
    ):
        """Set callbacks for connection events."""
        self._on_connect = on_connect
        self._on_disconnect = on_disconnect

    def on_message(self, msg_type: str, handler: Callable):
        """Register a handler for a specific message type.

        Handler receives (connection, message).
        """
        self._handlers[msg_type] = handler

    def start_server(self, host: str = "0.0.0.0", port: int = 9472) -> bool:
        """Start the WebSocket server in a background thread.

        Returns:
            True if the server started successfully.
        """
        if self._running:
            logger.warning("Transport server already running")
            return True

        self._running = True
        self._thread = threading.Thread(
            target=self._run_server_loop,
            args=(host, port),
            daemon=True,
            name="ws-server",
        )
        self._thread.start()
        logger.info("WebSocket server starting on %s:%s", host, port)
        return True

    def _run_server_loop(self, host: str, port: int):
        """Run the asyncio event loop for the WebSocket server."""
        try:
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            self._loop.run_until_complete(self._async_server(host, port))
        except Exception as e:
            logger.error("WebSocket server error: %s", e)
            self._running = False

    async def _async_server(self, host: str, port: int):
        """Async WebSocket server."""
        self._server = await serve(
            self._handle_client,
            host,
            port,
            max_size=MAX_FRAME_SIZE,
            ping_interval=20,
            ping_timeout=10,
        )

        # Start keepalive
        self._keepalive_task = asyncio.create_task(self._keepalive_loop())

        logger.info("WebSocket server listening on %s:%s", host, port)
        await self._server.serve_forever()

    async def _handle_client(self, websocket: ServerConnection):
        """Handle an incoming WebSocket client connection."""
        remote = websocket.remote_address
        addr = f"{remote[0]}:{remote[1]}" if remote else "unknown"
        logger.info("New WebSocket connection from %s", addr)

        conn = Connection(
            websocket=websocket,
            device_id="",
            remote_address=addr,
            is_server_side=True,
        )

        try:
            async for raw_message in websocket:
                conn.last_activity = time.time()
                await self._process_message(conn, raw_message)
        except websockets.exceptions.ConnectionClosed:
            logger.info("WebSocket connection closed: %s", addr)
        except Exception as e:
            logger.warning("WebSocket error (%s): %s", addr, e)
        finally:
            self._remove_connection(conn)

    async def connect_to_device(
        self, host: str, port: int = 9472, device_id: str = ""
    ) -> Optional[Connection]:
        """Connect to a remote StreamSync device.

        Args:
            host: Device IP address or hostname.
            port: Device port.
            device_id: Expected device ID (optional).

        Returns:
            Connection object on success, None on failure.
        """
        uri = f"ws://{host}:{port}"
        try:
            ws = await connect(
                uri,
                max_size=MAX_FRAME_SIZE,
                ping_interval=20,
                ping_timeout=10,
                open_timeout=10,
            )
            logger.info("Connected to %s", uri)

            conn = Connection(
                websocket=ws,
                device_id=device_id,
                remote_address=f"{host}:{port}",
            )

            # Send hello
            hello = make_hello(self.device_id, self.device_name)
            await self._send(conn, hello)

            with self._lock:
                if device_id:
                    self._connections[device_id] = conn

            return conn

        except (OSError, asyncio.TimeoutError, websockets.exceptions.WebSocketException) as e:
            logger.warning("Failed to connect to %s: %s", uri, e)
            return None

    async def _process_message(self, conn: Connection, raw: bytes):
        """Process an incoming message from a WebSocket."""
        try:
            message = StreamSyncMessage.from_json(raw.decode("utf-8"))
        except (ProtocolError, UnicodeDecodeError, json.JSONDecodeError) as e:
            logger.warning("Invalid message from %s: %s", conn.remote_address, e)
            return

        # Track device_id from messages
        if message.device_id and not conn.device_id:
            conn.device_id = message.device_id
            conn.device_name = message.device_name
            with self._lock:
                self._connections[message.device_id] = conn
            if self._on_connect:
                try:
                    self._on_connect(conn)
                except Exception:
                    logger.exception("On-connect callback failed")

        # Handle built-in message types
        msg_type = message.type
        if msg_type == "ping":
            pong = make_pong(self.device_id, self.device_name)
            await self._send(conn, pong)
        elif msg_type == "pong":
            pass  # Keepalive acknowledged
        elif msg_type == "hello":
            logger.info(
                "Hello from %s (%s)", message.device_name, message.device_id
            )
            # Reply with our hello
            hello = make_hello(self.device_id, self.device_name)
            await self._send(conn, hello)

        # Dispatch to registered handlers
        handler = self._handlers.get(msg_type)
        if handler:
            try:
                if asyncio.iscoroutinefunction(handler):
                    await handler(conn, message)
                else:
                    handler(conn, message)
            except Exception:
                logger.exception("Handler error for message type '%s'", msg_type)

    def _remove_connection(self, conn: Connection):
        """Remove a connection and notify."""
        with self._lock:
            to_remove = [
                did
                for did, c in self._connections.items()
                if c is conn or c.websocket is conn.websocket
            ]
            for did in to_remove:
                del self._connections[did]

        if self._on_disconnect:
            try:
                self._on_disconnect(conn)
            except Exception:
                logger.exception("On-disconnect callback failed")

    async def _send(self, conn: Connection, message: StreamSyncMessage):
        """Send a message over a connection."""
        try:
            data = message.to_bytes()
            await conn.websocket.send(data)
            conn.last_activity = time.time()
        except websockets.exceptions.ConnectionClosed:
            logger.debug("Cannot send, connection closed for %s", conn.device_id)
            self._remove_connection(conn)
        except Exception as e:
            logger.warning("Send error to %s: %s", conn.device_id, e)
            self._remove_connection(conn)

    def send_message(self, device_id: str, message: StreamSyncMessage) -> bool:
        """Send a message to a specific device by ID.

        Returns:
            True if the message was queued for sending.
        """
        conn = self._connections.get(device_id)
        if not conn or not conn.is_alive:
            logger.warning("No connection to device %s", device_id)
            return False

        if self._loop and self._loop.is_running():
            asyncio.run_coroutine_threadsafe(
                self._send(conn, message), self._loop
            )
            return True
        return False

    def broadcast(self, message: StreamSyncMessage, exclude: Set[str] = None):
        """Send a message to all connected devices."""
        if exclude is None:
            exclude = set()
        with self._lock:
            targets = [
                (did, conn)
                for did, conn in self._connections.items()
                if did not in exclude and conn.is_alive
            ]
        for device_id, conn in targets:
            if self._loop and self._loop.is_running():
                asyncio.run_coroutine_threadsafe(
                    self._send(conn, message), self._loop
                )

    async def _keepalive_loop(self):
        """Send periodic pings to keep connections alive."""
        while self._running:
            await asyncio.sleep(self._keepalive_interval)
            ping = make_ping(self.device_id, self.device_name)
            with self._lock:
                connections = list(self._connections.values())
            for conn in connections:
                if conn.is_alive:
                    try:
                        await self._send(conn, ping)
                    except Exception:
                        pass

    def stop(self):
        """Stop the transport server and close all connections."""
        self._running = False

        if self._keepalive_task and self._loop and self._loop.is_running():
            self._keepalive_task.cancel()

        if self._server and self._loop and self._loop.is_running():
            self._server.close()

        # Close all connections
        with self._lock:
            for conn in list(self._connections.values()):
                try:
                    bye = make_bye(self.device_id, self.device_name)
                    asyncio.run_coroutine_threadsafe(
                        self._send(conn, bye), self._loop
                    )
                except Exception:
                    pass
            self._connections.clear()

        logger.info("WebSocket transport stopped")

    @property
    def connection_count(self) -> int:
        return len(self._connections)

    @property
    def connected_devices(self) -> Dict[str, Connection]:
        with self._lock:
            return dict(self._connections)
