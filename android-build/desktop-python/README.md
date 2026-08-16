# StreamSync Desktop Companion

A cross-platform desktop companion app for StreamSync that enables file transfer, content streaming, and clipboard synchronization for devices that don't have a native streaming client.

## Features

- **🔍 Device Discovery** - Automatically discover StreamSync devices on your local network via mDNS
- **📁 File Transfer** - Send and receive files with chunked streaming and progress tracking
- **📺 Content Streaming** - Stream media files to VLC/MPV or built-in web receiver
- **📋 Clipboard Sync** - Synchronize clipboard content across devices
- **🔒 End-to-End Encryption** - AES-256-GCM encryption for all transfers
- **🖥️ Multiple Interfaces** - GUI (PyQt6), TUI (Textual), and CLI modes

## Installation

```bash
# Basic installation (CLI + TUI)
pip install .

# With GUI support
pip install ".[gui]"

# With streaming support
pip install ".[streaming]"

# Everything
pip install ".[all]"
```

## Quick Start

```bash
# Discover devices on the network
streamsync discover

# Send a file to a device
streamsync send myvideo.mp4 device-1234

# Start the daemon (background service)
streamsync daemon

# Launch the GUI
python -m streamsync_cli --gui

# Launch the TUI
python -m streamsync_cli --tui

# Stream a media file
streamsync stream myvideo.mp4
```

## CLI Commands

| Command | Description |
|---------|-------------|
| `discover` | Scan for StreamSync devices on the network |
| `send <file> <device>` | Send a file to a specific device |
| `receive <dir>` | Start receiving files to a directory |
| `stream <file>` | Start a streaming server for a media file |
| `clipboard` | Start clipboard sync mode |
| `daemon` | Run the background service daemon |

## Architecture

```
streamsync_cli/
├── __init__.py          # Package metadata
├── __main__.py          # CLI entry point
├── core/                # Core protocol implementation
│   ├── protocol.py      # Message serialization/deserialization
│   ├── discovery.py     # mDNS/zeroconf discovery
│   ├── transport.py     # WebSocket transport layer
│   ├── crypto.py        # AES-256-GCM encryption
│   ├── transfer.py      # File transfer manager
│   ├── streaming.py     # Media streaming (VLC/subprocess)
│   ├── clipboard.py     # Clipboard sync service
│   └── config.py        # Configuration management
├── ui/                  # User interfaces
│   ├── __init__.py      # Auto-detect UI backend
│   ├── qt_app.py        # PyQt6/PySide6 GUI application
│   ├── qt_main_window.py
│   ├── qt_devices.py    # Devices panel
│   ├── qt_transfer.py   # Transfer panel
│   ├── qt_streaming.py  # Streaming panel
│   ├── qt_settings.py   # Settings dialog
│   ├── tui_app.py       # Textual-based TUI
│   └── cli_app.py       # Click-powered CLI
└── server/              # Background server
    ├── __init__.py
    └── server.py        # Background service daemon
```

## Supported Platforms

- **Windows** 10/11
- **macOS** 11+
- **Linux** (most distributions)

## Requirements

- Python 3.9+
- Optional: PyQt6 for GUI mode
- Optional: VLC Media Player for streaming support
- Optional: Textual for TUI mode

## License

MIT
