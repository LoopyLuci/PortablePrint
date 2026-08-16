"""CLI commands using Click.

Usage:
    streamsync discover
    streamsync send <file> <device-id>
    streamsync receive <output-dir>
    streamsync stream <file>
    streamsync clipboard
    streamsync daemon
"""

import logging
import sys
import time
from pathlib import Path
from typing import Optional

import click

from streamsync_cli.core.config import StreamSyncConfig, DEFAULT_PORT
from streamsync_cli.core.discovery import DeviceDiscovery
from streamsync_cli.core.transfer import FileTransferManager
from streamsync_cli.core.streaming import MediaStreamer
from streamsync_cli.server.server import StreamSyncDaemon

logger = logging.getLogger(__name__)

# Shared config
_config: Optional[StreamSyncConfig] = None


def get_config() -> StreamSyncConfig:
    """Get or create the shared configuration."""
    global _config
    if _config is None:
        _config = StreamSyncConfig.load()
    return _config


class AliasedGroup(click.Group):
    """Click group that supports command aliases."""

    def get_command(self, ctx, cmd_name):
        rv = click.Group.get_command(self, ctx, cmd_name)
        if rv is not None:
            return rv
        # Support partial matches
        matches = [x for x in self.list_commands(ctx) if x.startswith(cmd_name)]
        if matches:
            return click.Group.get_command(self, ctx, matches[0])
        return None


@click.group(cls=AliasedGroup)
@click.option("--verbose", "-v", is_flag=True, help="Enable verbose logging")
@click.option("--config", "-c", type=click.Path(), help="Path to config file")
@click.version_option(version="1.0.0", prog_name="streamsync")
def cli(verbose: bool, config: Optional[str]):
    """StreamSync Desktop Companion - File transfer & content streaming.

    Cross-platform companion app for StreamSync enabling file transfer,
    content streaming, and clipboard sync for devices without a native
    streaming client.
    """
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    if config:
        global _config
        _config = StreamSyncConfig.load(Path(config))


@cli.command()
@click.option("--timeout", "-t", default=10, help="Discovery timeout in seconds")
@click.option("--json", "-j", "json_output", is_flag=True, help="JSON output format")
def discover(timeout: int, json_output: bool):
    """Discover StreamSync devices on the local network."""
    config = get_config()
    click.echo("🔍 Discovering StreamSync devices...")

    found_devices = []

    with DeviceDiscovery(
        device_id=config.device_id,
        device_name=config.device_name,
        port=config.port,
    ) as discovery:
        wait_until = time.time() + timeout
        while time.time() < wait_until:
            devices = discovery.devices
            if devices:
                found_devices = devices
                # Print as they appear
                for d in devices:
                    click.echo(
                        f"  📱 {d.device_name:20s}  {d.device_id:12s}  "
                        f"{d.address:15s}  v{d.version}"
                    )
            time.sleep(1)

    if not found_devices:
        click.echo("  No devices found.")
        return

    if json_output and found_devices:
        import json
        click.echo(json.dumps(
            [
                {
                    "device_id": d.device_id,
                    "device_name": d.device_name,
                    "host": d.host,
                    "port": d.port,
                    "ip_addresses": d.ip_addresses,
                    "version": d.version,
                }
                for d in found_devices
            ],
            indent=2,
        ))


@cli.command()
@click.argument("file_path", type=click.Path(exists=True))
@click.argument("device_id")
@click.option("--port", "-p", default=DEFAULT_PORT, help="Device port")
def send(file_path: str, device_id: str, port: int):
    """Send a FILE to a DEVICE.

    FILE is the path to the file to send.

    DEVICE_ID is the target device identifier (from 'discover' command).
    """
    config = get_config()
    file_path = str(Path(file_path).resolve())

    # Validate file
    path = Path(file_path)
    if not path.is_file():
        click.echo(f"❌ Error: {file_path} is not a file", err=True)
        sys.exit(1)

    file_size = path.stat().st_size

    click.echo(f"📤 Sending: {path.name} ({_format_size(file_size)})")
    click.echo(f"   To device: {device_id}")

    # Use the daemon for transport
    daemon = StreamSyncDaemon(config)
    daemon.start()

    transfer_id = daemon.transfer_manager.send_file(
        file_path, device_id, device_name=""
    )

    if not transfer_id:
        click.echo("❌ Failed to initiate file transfer", err=True)
        daemon.stop()
        sys.exit(1)

    click.echo(f"   Transfer ID: {transfer_id}")

    # Poll for completion
    try:
        while True:
            info = daemon.transfer_manager.get_transfer(transfer_id)
            if info:
                if info.status.value == "completed":
                    click.echo(f"✅ File sent successfully!")
                    break
                elif info.status.value in ("failed", "cancelled"):
                    click.echo(
                        f"❌ Transfer failed: {info.error_message}", err=True
                    )
                    break
                # Progress
                bar = _progress_bar(info.progress)
                click.echo(
                    f"\r   Progress: {bar} {info.progress:.1f}% "
                    f"({_format_size(info.bytes_transferred)}/{_format_size(info.file_size)})",
                    nl=False,
                )
            time.sleep(0.5)
    except KeyboardInterrupt:
        daemon.transfer_manager.cancel_transfer(transfer_id)
        click.echo("\n⚠️  Transfer cancelled")
    finally:
        daemon.stop()


@cli.command()
@click.argument("output_dir", type=click.Path(), default="")
@click.option("--port", "-p", default=DEFAULT_PORT, help="Listen port")
@click.option("--timeout", "-t", default=0, help="Receive timeout (0 = infinite)")
def receive(output_dir: str, port: int, timeout: int):
    """Receive files from StreamSync devices.

    OUTPUT_DIR is the directory to save received files (default: ~/Downloads/StreamSync).
    """
    config = get_config()
    if output_dir:
        config.download_dir = str(Path(output_dir).resolve())

    click.echo(f"📥 Receiving files to: {config.download_dir}")
    click.echo("   Waiting for incoming transfers... (Ctrl+C to stop)")

    daemon = StreamSyncDaemon(config)
    daemon.start()

    try:
        start = time.time()
        while True:
            if timeout > 0 and (time.time() - start) > timeout:
                click.echo("\n⏰ Receive timeout reached")
                break

            transfers = daemon.transfer_manager.get_active_transfers()
            for t in transfers:
                if t.direction == "receive":
                    bar = _progress_bar(t.progress)
                    click.echo(
                        f"\r   📥 {t.filename:30s} {bar} {t.progress:.1f}% "
                        f"({_format_size(t.bytes_transferred)}/{_format_size(t.file_size)})",
                        nl=False,
                    )

            time.sleep(0.5)
    except KeyboardInterrupt:
        click.echo("\n🛑 Receive stopped")
    finally:
        daemon.stop()


@cli.command()
@click.argument("file_path", type=click.Path(exists=True))
@click.option("--method", "-m", type=click.Choice(["vlc", "mpv", "http", "auto"]), default="auto")
@click.option("--port", "-p", default=9473, help="Stream server port")
def stream(file_path: str, method: str, port: int):
    """Stream a media FILE over the network.

    Starts a streaming server for the specified media file.
    Other devices can connect to receive the stream.
    """
    config = get_config()
    file_path = str(Path(file_path).resolve())

    path = Path(file_path)
    if not path.is_file():
        click.echo(f"❌ Error: {file_path} is not a file", err=True)
        sys.exit(1)

    file_size = path.stat().st_size
    click.echo(f"📺 Streaming: {path.name} ({_format_size(file_size)})")
    click.echo(f"   Method: {method.upper()}")

    streamer = MediaStreamer(stream_port=port)
    stream_id = streamer.start_streaming(file_path, method=method)

    if not stream_id:
        click.echo(
            "❌ Failed to start streaming. Install VLC or MPV for local playback.",
            err=True,
        )
        sys.exit(1)

    click.echo(f"   Stream URL: http://localhost:{port}/stream")
    click.echo("   Streaming... (Ctrl+C to stop)")

    try:
        while True:
            info = streamer.get_stream(stream_id)
            if info and info.status.value in ("stopped", "error"):
                click.echo(f"\n📺 Stream ended: {info.status.value}")
                break
            time.sleep(1)
    except KeyboardInterrupt:
        click.echo("\n🛑 Stream stopped")
    finally:
        streamer.stop_all()


@cli.command()
@click.option("--port", "-p", default=DEFAULT_PORT, help="Listen port")
def clipboard(port: int):
    """Start clipboard synchronization mode.

    Monitors the local clipboard and syncs with connected devices.
    """
    config = get_config()
    click.echo("📋 Clipboard sync mode")
    click.echo("   Syncing clipboard with connected devices... (Ctrl+C to stop)")

    daemon = StreamSyncDaemon(config)
    daemon.start()

    try:
        while True:
            if daemon.clipboard:
                content = daemon.clipboard.get_current_content()
                if content:
                    preview = content[:60] + "..." if len(content) > 60 else content
                    click.echo(f"\r   📋 {preview:70s}", nl=False)
            time.sleep(1)
    except KeyboardInterrupt:
        click.echo("\n🛑 Clipboard sync stopped")
    finally:
        daemon.stop()


@cli.command()
@click.option("--port", "-p", default=DEFAULT_PORT, help="Listen port")
@click.option("--daemon/--no-daemon", default=True, help="Run as daemon (background)")
def daemon(port: int, daemon: bool):
    """Run the StreamSync background service.

    Starts the discovery, transfer, streaming, and clipboard services
    in the background.
    """
    config = get_config()
    config.port = port

    click.echo("🚀 StreamSync Daemon")
    click.echo(f"   Device: {config.device_name} ({config.device_id})")
    click.echo(f"   Port: {port}")
    click.echo("   Starting services...")

    svc = StreamSyncDaemon(config)
    svc.start()

    click.echo("✅ All services running")
    click.echo("   Press Ctrl+C to stop")

    try:
        while svc.is_running:
            n_connections = svc.transport.connection_count if svc.transport else 0
            n_devices = len(svc.discovery.devices) if svc.discovery else 0
            n_transfers = len(svc.transfer_manager.get_active_transfers()) if svc.transfer_manager else 0

            status = (
                f"   📱 {n_devices} devices  "
                f"🔗 {n_connections} connections  "
                f"📦 {n_transfers} transfers"
            )
            click.echo(f"\r{status:70s}", nl=False)
            time.sleep(2)
    except KeyboardInterrupt:
        click.echo("\n🛑 Stopping daemon...")
    finally:
        svc.stop()
        click.echo("✅ Daemon stopped")


def _format_size(size_bytes: int) -> str:
    """Format byte size to human-readable string."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"


def _progress_bar(percent: float, width: int = 20) -> str:
    """Generate a text progress bar."""
    filled = int(width * percent / 100)
    return "█" * filled + "░" * (width - filled)


if __name__ == "__main__":
    cli()
