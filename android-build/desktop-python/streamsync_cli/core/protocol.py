"""Protocol message handling - construct and deconstruct StreamSync messages.

Message Format (JSON over WebSocket):
{
  "version": 1,
  "type": "<message-type>",
  "device_id": "<sender-id>",
  "device_name": "<sender-name>",
  "payload": { ... }
}

Message Types:
- "ping" / "pong" - keepalive
- "hello" - initial handshake / device announcement
- "bye" - disconnect notification
- "file_request" - request to send a file
- "file_accept" / "file_reject" - response to file request
- "file_chunk" - chunk of file data
- "file_complete" - file transfer finished
- "file_error" - file transfer error
- "stream_request" - request to start streaming
- "stream_accept" / "stream_reject" - streaming response
- "stream_data" - streaming media data
- "stream_stop" - stop streaming
- "clipboard_push" - clipboard content update
- "clipboard_request" - request current clipboard
- "clipboard_data" - clipboard content data
- "discovery_query" - query for devices
- "discovery_response" - response to discovery query
"""

import json
import logging
import uuid
from dataclasses import dataclass, field, asdict
from datetime import datetime
from typing import Any, Dict, Optional

logger = logging.getLogger(__name__)

PROTOCOL_VERSION = 1
MAX_MESSAGE_SIZE = 50 * 1024 * 1024  # 50 MB max per message


class ProtocolError(Exception):
    """Raised when a protocol message is malformed or invalid."""
    pass


@dataclass
class StreamSyncMessage:
    """A structured StreamSync protocol message."""

    type: str
    device_id: str
    device_name: str = ""
    version: int = PROTOCOL_VERSION
    message_id: str = field(default_factory=lambda: str(uuid.uuid4())[:8])
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")
    payload: Dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> str:
        """Serialize this message to a JSON string."""
        data = asdict(self)
        return json.dumps(data, ensure_ascii=False, default=str)

    def to_bytes(self) -> bytes:
        """Serialize to JSON bytes."""
        return self.to_json().encode("utf-8")

    @classmethod
    def from_json(cls, json_str: str) -> "StreamSyncMessage":
        """Deserialize a JSON string into a StreamSyncMessage.

        Args:
            json_str: The JSON string to parse.

        Returns:
            A StreamSyncMessage instance.

        Raises:
            ProtocolError: If the JSON is invalid or required fields are missing.
        """
        try:
            data = json.loads(json_str)
        except json.JSONDecodeError as e:
            raise ProtocolError(f"Invalid JSON: {e}")

        msg_type = data.get("type", "")
        if not msg_type:
            raise ProtocolError("Message missing 'type' field")

        device_id = data.get("device_id", "")
        if not device_id:
            raise ProtocolError("Message missing 'device_id' field")

        return cls(
            version=data.get("version", PROTOCOL_VERSION),
            type=msg_type,
            device_id=device_id,
            device_name=data.get("device_name", ""),
            message_id=data.get("message_id", ""),
            timestamp=data.get("timestamp", ""),
            payload=data.get("payload", {}),
        )


# --- Convenience constructors ---

def make_hello(device_id: str, device_name: str, **extra) -> StreamSyncMessage:
    """Create a 'hello' announcement message."""
    payload = {"device_name": device_name, **extra}
    return StreamSyncMessage(
        type="hello",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_ping(device_id: str, device_name: str = "") -> StreamSyncMessage:
    """Create a ping message."""
    return StreamSyncMessage(type="ping", device_id=device_id, device_name=device_name)


def make_pong(device_id: str, device_name: str = "") -> StreamSyncMessage:
    """Create a pong message."""
    return StreamSyncMessage(type="pong", device_id=device_id, device_name=device_name)


def make_bye(device_id: str, device_name: str = "") -> StreamSyncMessage:
    """Create a bye message."""
    return StreamSyncMessage(type="bye", device_id=device_id, device_name=device_name)


def make_file_request(
    device_id: str,
    device_name: str,
    filename: str,
    file_size: int,
    file_hash: str = "",
    mime_type: str = "",
) -> StreamSyncMessage:
    """Create a file transfer request."""
    payload = {
        "filename": filename,
        "file_size": file_size,
        "file_hash": file_hash,
        "mime_type": mime_type,
    }
    return StreamSyncMessage(
        type="file_request",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_file_accept(
    device_id: str, device_name: str, transfer_id: str
) -> StreamSyncMessage:
    """Create a file accept response."""
    return StreamSyncMessage(
        type="file_accept",
        device_id=device_id,
        device_name=device_name,
        payload={"transfer_id": transfer_id},
    )


def make_file_reject(
    device_id: str, device_name: str, transfer_id: str, reason: str = ""
) -> StreamSyncMessage:
    """Create a file reject response."""
    payload = {"transfer_id": transfer_id, "reason": reason}
    return StreamSyncMessage(
        type="file_reject",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_file_chunk(
    device_id: str,
    device_name: str,
    transfer_id: str,
    chunk_index: int,
    total_chunks: int,
    data_b64: str,
) -> StreamSyncMessage:
    """Create a file chunk message."""
    payload = {
        "transfer_id": transfer_id,
        "chunk_index": chunk_index,
        "total_chunks": total_chunks,
        "data": data_b64,
    }
    return StreamSyncMessage(
        type="file_chunk",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_file_complete(
    device_id: str,
    device_name: str,
    transfer_id: str,
    file_hash: str = "",
) -> StreamSyncMessage:
    """Create a file transfer complete message."""
    payload = {"transfer_id": transfer_id, "file_hash": file_hash}
    return StreamSyncMessage(
        type="file_complete",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_file_error(
    device_id: str,
    device_name: str,
    transfer_id: str,
    error: str,
) -> StreamSyncMessage:
    """Create a file transfer error message."""
    payload = {"transfer_id": transfer_id, "error": error}
    return StreamSyncMessage(
        type="file_error",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_stream_request(
    device_id: str,
    device_name: str,
    filename: str,
    mime_type: str = "",
    stream_method: str = "websocket",
) -> StreamSyncMessage:
    """Create a streaming request."""
    payload = {
        "filename": filename,
        "mime_type": mime_type,
        "stream_method": stream_method,
    }
    return StreamSyncMessage(
        type="stream_request",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_stream_accept(
    device_id: str, device_name: str, stream_id: str, endpoint: str = ""
) -> StreamSyncMessage:
    """Create a stream accept response."""
    payload = {"stream_id": stream_id, "endpoint": endpoint}
    return StreamSyncMessage(
        type="stream_accept",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_stream_reject(
    device_id: str, device_name: str, stream_id: str, reason: str = ""
) -> StreamSyncMessage:
    """Create a stream reject response."""
    payload = {"stream_id": stream_id, "reason": reason}
    return StreamSyncMessage(
        type="stream_reject",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_stream_stop(
    device_id: str, device_name: str, stream_id: str
) -> StreamSyncMessage:
    """Create a stream stop message."""
    return StreamSyncMessage(
        type="stream_stop",
        device_id=device_id,
        device_name=device_name,
        payload={"stream_id": stream_id},
    )


def make_clipboard_push(
    device_id: str, device_name: str, content: str, content_type: str = "text"
) -> StreamSyncMessage:
    """Create a clipboard push message."""
    payload = {"content": content, "content_type": content_type}
    return StreamSyncMessage(
        type="clipboard_push",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )


def make_discovery_response(
    device_id: str,
    device_name: str,
    port: int,
    version: int = PROTOCOL_VERSION,
) -> StreamSyncMessage:
    """Create a discovery response."""
    payload = {
        "port": port,
        "version": version,
        "device_name": device_name,
    }
    return StreamSyncMessage(
        type="discovery_response",
        device_id=device_id,
        device_name=device_name,
        payload=payload,
    )
