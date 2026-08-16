"""Textual-based TUI (Terminal User Interface) fallback for StreamSync.

Uses the Textual library for a rich terminal interface when
PyQt6/PySide6 is not available.
"""

import asyncio
import logging
import time
from pathlib import Path
from typing import Optional

from rich.text import Text
from rich.table import Table
from rich.layout import Layout
from rich.panel import Panel
from rich.align import Align

from streamsync_cli.core.config import StreamSyncConfig
from streamsync_cli.core.discovery import DeviceInfo, DeviceDiscovery
from streamsync_cli.core.transfer import FileTransferManager, TransferInfo, TransferStatus
from streamsync_cli.server.server import StreamSyncDaemon

logger = logging.getLogger(__name__)


def run_tui():
    """Entry point for the TUI mode.

    Attempts to import Textual and run the app.
    """
    try:
        from textual.app import App, ComposeResult
        from textual.containers import Container, Horizontal, Vertical
        from textual.widgets import (
            Header, Footer, Static, ListView, ListItem,
            Label, Button, Input, ProgressBar, TabbedContent, TabPane,
            RichLog, DataTable, LoadingIndicator,
        )
        from textual.screen import Screen
        from textual.reactive import reactive
        from textual import work
    except ImportError:
        logger.error(
            "Textual is not installed. Install with: pip install streamsync-cli[tui]"
        )
        return

    # ---- StreamSync TUI App ----

    class DevicesWidget(Static):
        """Widget showing discovered devices."""

        def __init__(self):
            super().__init__()
            self._devices: list = []

        def set_devices(self, devices: list):
            self._devices = devices
            self.refresh()

        def on_mount(self):
            self.refresh()

        def render(self) -> Table:
            table = Table(
                title="📱 Discovered Devices",
                style="bold cyan",
                border_style="blue",
                header_style="bold white",
            )
            table.add_column("Name", style="green")
            table.add_column("ID", style="yellow")
            table.add_column("Address", style="white")
            table.add_column("Version", style="magenta")

            if not self._devices:
                table.add_row(
                    "No devices found",
                    "",
                    "Scanning...",
                    "",
                )
            else:
                for d in self._devices:
                    table.add_row(
                        d.device_name,
                        d.device_id,
                        d.address,
                        f"v{d.version}",
                    )

            return table


    class TransfersWidget(Static):
        """Widget showing active file transfers."""

        def __init__(self):
            super().__init__()
            self._transfers: list = []

        def set_transfers(self, transfers: list):
            self._transfers = transfers
            self.refresh()

        def on_mount(self):
            self.refresh()

        def render(self) -> Table:
            table = Table(
                title="📦 Transfers",
                style="bold cyan",
                border_style="blue",
                header_style="bold white",
            )
            table.add_column("File", style="green")
            table.add_column("Dir", style="yellow")
            table.add_column("Progress", style="white")
            table.add_column("Status", style="magenta")

            if not self._transfers:
                table.add_row("No active transfers", "", "", "")
            else:
                for t in self._transfers:
                    bar = "█" * int(t.progress / 5) + "░" * (20 - int(t.progress / 5))
                    table.add_row(
                        t.filename[:25],
                        "↑" if t.direction == "send" else "↓",
                        f"{bar} {t.progress:.0f}%",
                        t.status.value,
                    )

            return table


    class StreamSyncTUI(App):
        """StreamSync Textual TUI application."""

        CSS = """
        Screen {
            background: #1a1b26;
        }

        Header {
            background: #24283b;
            color: #a9b1d6;
        }

        Footer {
            background: #24283b;
            color: #565f89;
        }

        #main-container {
            height: 100%;
        }

        DevicesWidget {
            height: auto;
            margin: 1;
        }

        TransfersWidget {
            height: auto;
            margin: 1;
        }

        #status-bar {
            background: #1a1b26;
            color: #565f89;
            height: 1;
            text-align: center;
        }

        #controls {
            dock: bottom;
            height: 3;
            padding: 0 1;
        }

        Button {
            background: #2ac3de;
            color: #1a1b26;
            margin: 0 1;
        }

        Button:hover {
            background: #7dcfff;
        }

        #log-panel {
            height: 8;
            border: solid #565f89;
            margin: 1;
        }
        """

        def __init__(self):
            super().__init__()
            self.config = StreamSyncConfig.load()
            self.daemon = StreamSyncDaemon(self.config)
            self._update_timer = None

        def compose(self) -> ComposeResult:
            yield Header(show_clock=True)
            yield Container(
                DevicesWidget(),
                TransfersWidget(),
                RichLog(id="log-panel", highlight=True, markup=True),
                id="main-container",
            )
            yield Horizontal(
                Button("🔍 Scan", id="scan", variant="primary"),
                Button("📁 Send", id="send", variant="default"),
                Button("📥 Receive", id="receive", variant="default"),
                Button("📺 Stream", id="stream", variant="default"),
                Button("🛑 Stop", id="stop", variant="error"),
                id="controls",
            )
            yield Footer()

        def on_mount(self):
            """Start the daemon and begin periodic updates."""
            self.daemon.start()
            self.set_interval(2, self.update_ui)

        def update_ui(self):
            """Periodic UI refresh."""
            try:
                devices_widget = self.query_one(DevicesWidget)
                devices = self.daemon.discovery.devices if self.daemon.discovery else []
                devices_widget.set_devices(devices)

                transfers_widget = self.query_one(TransfersWidget)
                transfers = (
                    self.daemon.transfer_manager.get_active_transfers()
                    if self.daemon.transfer_manager
                    else []
                )
                transfers_widget.set_transfers(transfers)

                log = self.query_one("#log-panel", RichLog)
                n_connections = (
                    self.daemon.transport.connection_count
                    if self.daemon.transport
                    else 0
                )
                log.clear()
                log.write(
                    f"[bold cyan]StreamSync[/] | "
                    f"[green]{len(devices)} devices[/] | "
                    f"[yellow]{n_connections} connections[/] | "
                    f"[magenta]{len(transfers)} transfers[/]"
                )
            except Exception as e:
                logger.debug("TUI update error: %s", e)

        def on_button_pressed(self, event: Button.Pressed):
            """Handle button clicks."""
            action = event.button.id

            if action == "scan":
                log = self.query_one("#log-panel", RichLog)
                log.write("[cyan]🔍 Scanning for devices...[/]")

            elif action == "stop":
                self.daemon.stop()
                self.exit(0)

        def on_unmount(self):
            """Clean up when exiting."""
            self.daemon.stop()

    # Run the app
    app = StreamSyncTUI()
    app.run()
