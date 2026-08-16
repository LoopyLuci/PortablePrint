"""Streaming panel for the Qt GUI - media streaming controls."""

import logging
from pathlib import Path
from typing import Callable, List, Optional

from streamsync_cli.core.config import StreamSyncConfig
from streamsync_cli.core.streaming import StreamInfo, StreamStatus

logger = logging.getLogger(__name__)


class StreamingPanel:
    """Panel for media streaming controls."""

    def __init__(self, config: StreamSyncConfig, import_qt: Callable):
        self.config = config
        qtw, qtc, qtg = import_qt()

        self.panel = qtw.QWidget()
        self._qt_modules = (qtw, qtc, qtg)

        layout = qtw.QVBoxLayout(self.panel)

        # Header
        header = qtw.QLabel("📺 Media Streaming")
        header_font = qtg.QFont()
        header_font.setPointSize(14)
        header_font.setBold(True)
        header.setFont(header_font)
        layout.addWidget(header)

        # File selection area
        file_group = qtw.QGroupBox("Media File")
        file_layout = qtw.QHBoxLayout(file_group)

        self.file_path_input = qtw.QLineEdit()
        self.file_path_input.setPlaceholderText("Select a media file to stream...")
        self.file_path_input.setReadOnly(True)
        file_layout.addWidget(self.file_path_input)

        self.browse_btn = qtw.QPushButton("📂 Browse")
        self.browse_btn.clicked.connect(self._on_browse)
        file_layout.addWidget(self.browse_btn)

        layout.addWidget(file_group)

        # Stream settings
        settings_group = qtw.QGroupBox("Stream Settings")
        settings_layout = qtw.QFormLayout(settings_group)

        self.method_combo = qtw.QComboBox()
        self.method_combo.addItems(["auto", "vlc", "mpv", "http"])
        settings_layout.addRow("Method:", self.method_combo)

        self.port_spin = qtw.QSpinBox()
        self.port_spin.setRange(1024, 65535)
        self.port_spin.setValue(config.stream_port)
        settings_layout.addRow("Port:", self.port_spin)

        layout.addWidget(settings_group)

        # Stream control
        control_layout = qtw.QHBoxLayout()
        self.start_btn = qtw.QPushButton("▶️ Start Streaming")
        self.start_btn.clicked.connect(self._on_start)
        self.start_btn.setEnabled(False)
        control_layout.addWidget(self.start_btn)

        self.stop_btn = qtw.QPushButton("⏹️ Stop")
        self.stop_btn.clicked.connect(self._on_stop)
        self.stop_btn.setEnabled(False)
        control_layout.addWidget(self.stop_btn)

        layout.addLayout(control_layout)

        # Status display
        status_group = qtw.QGroupBox("Stream Status")
        status_layout = qtw.QFormLayout(status_group)

        self.status_display = qtw.QLabel("Ready")
        status_layout.addRow("Status:", self.status_display)

        self.url_display = qtw.QLineEdit()
        self.url_display.setReadOnly(True)
        self.url_display.setPlaceholderText("Stream URL will appear here")
        status_layout.addRow("URL:", self.url_display)

        layout.addWidget(status_group)

        # Active streams list
        streams_label = qtw.QLabel("Active Streams")
        streams_font = qtg.QFont()
        streams_font.setBold(True)
        streams_label.setFont(streams_font)
        layout.addWidget(streams_label)

        self.streams_list = qtw.QListWidget()
        self.streams_list.setMinimumHeight(100)
        layout.addWidget(self.streams_list)

        layout.addStretch()

        # State
        self._current_stream_id: Optional[str] = None
        self._active_streams: List[StreamInfo] = []

    @property
    def widget(self):
        return self.panel

    def _on_browse(self):
        """Handle browse button click."""
        qtw = self._qt_modules[0]
        file_path, _ = qtw.QFileDialog.getOpenFileName(
            self.panel,
            "Select Media File",
            "",
            "Media Files (*.mp4 *.mkv *.avi *.mov *.webm *.mp3 *.wav *.flac *.ogg);;All Files (*)",
        )
        if file_path:
            self.file_path_input.setText(file_path)
            self.start_btn.setEnabled(True)

    def _on_start(self):
        """Handle start streaming button click."""
        file_path = self.file_path_input.text()
        if not file_path or not Path(file_path).exists():
            return

        method = self.method_combo.currentText()
        if self._current_stream_id:
            logger.warning("Already streaming")
            return

        logger.info("Starting stream: %s (method: %s)", file_path, method)
        self.status_display.setText("⏳ Starting stream...")
        self.start_btn.setEnabled(False)

    def _on_stop(self):
        """Handle stop button click."""
        if self._current_stream_id:
            logger.info("Stopping stream: %s", self._current_stream_id)
            self.status_display.setText("⏹️ Stopped")
            self.url_display.clear()
            self.start_btn.setEnabled(True)
            self.stop_btn.setEnabled(False)
            self._current_stream_id = None

    def update_streams(self, streams: List[StreamInfo]):
        """Update the active streams list."""
        self._active_streams = streams
        self.streams_list.clear()

        for s in streams:
            icon = "▶️" if s.status == StreamStatus.PLAYING else "⏸️" if s.status == StreamStatus.PAUSED else "⏹️"
            self.streams_list.addItem(f"{icon} {s.filename} [{s.status.value}]")

    def set_stream_url(self, url: str):
        """Set the stream URL display."""
        self.url_display.setText(url)

    def set_stream_status(self, status: str):
        """Update stream status display."""
        self.status_display.setText(status)

    def set_controls_enabled(self, streaming: bool):
        """Enable or disable stream controls."""
        self.start_btn.setEnabled(not streaming and bool(self.file_path_input.text()))
        self.stop_btn.setEnabled(streaming)
