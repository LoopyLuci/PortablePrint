import os
import sys
import platform
from typing import Protocol

class PrinterBackend(Protocol):
    def connect(self, address: str, channel: int) -> None: ...
    def send(self, payload: bytes) -> None: ...
    def close(self) -> None: ...


class _RfcommBackend:
    def __init__(self):
        self._sock = None

    def connect(self, address: str, channel: int) -> None:
        import socket
        self._sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        self._sock.connect((address, channel))

    def send(self, payload: bytes) -> None:
        if not self._sock:
            raise RuntimeError("Not connected")
        self._sock.sendall(payload)

    def close(self) -> None:
        if self._sock:
            try:
                self._sock.close()
            except Exception:
                pass
            self._sock = None


class _BleBackend:
    def __init__(self):
        self._client = None
        self._loop = None

    async def _connect_async(self, address: str, channel: int):
        import bleak
        self._client = bleak.BleakClient(address)
        await self._client.connect()
        services = await self._client.get_services()
        target_char = None
        for service in services:
            for char in service.characteristics:
                if "write" in char.properties:
                    target_char = char
                    break
            if target_char:
                break
        if not target_char:
            raise RuntimeError("No writable BLE characteristic found")
        self._target_char = target_char

    async def _send_async(self, payload: bytes):
        if not self._client or not self._client.is_connected:
            raise RuntimeError("BLE not connected")
        await self._client.write_gatt_char(self._target_char.uuid, payload, response=False)

    async def _close_async(self):
        if self._client and self._client.is_connected:
            await self._client.disconnect()
        self._client = None

    def connect(self, address: str, channel: int) -> None:
        import asyncio
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._loop.run_until_complete(self._connect_async(address, channel))

    def send(self, payload: bytes) -> None:
        if not self._loop:
            raise RuntimeError("Backend not initialized")
        self._loop.run_until_complete(self._send_async(payload))

    def close(self) -> None:
        if self._loop:
            self._loop.run_until_complete(self._close_async())
            self._loop.close()
            self._loop = None


def create_backend() -> PrinterBackend:
    system = platform.system()
    if system == "Darwin":
        return _BleBackend()
    return _RfcommBackend()
