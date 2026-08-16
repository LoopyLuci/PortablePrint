"""Devices panel for the Qt GUI - shows discovered StreamSync devices."""

import logging
import time
from typing import Callable, List, Optional

from streamsync_cli.core.config import StreamSyncConfig
from streamsync_cli.core.discovery import DeviceInfo

logger = logging.getLogger(__name__)


class DevicesPanel:
    """Panel showing discovered StreamSync devices."""

    def __init__(self, config: StreamSyncConfig, import_qt: Callable):
        self.config = config
        qtw, qtc, qtg = import_qt()

        self.panel = qtw.QWidget()
        layout = qtw.QVBoxLayout(self.panel)

        # Header
        header = qtw.QLabel("🔍 Discovered Devices")
        header_font = qtg.QFont()
        header_font.setPointSize(14)
        header_font.setBold(True)
        header.setFont(header_font)
        layout.addWidget(header)

        # Info label
        self.info_label = qtw.QLabel("Scanning for StreamSync devices on the network...")
        self.info_label.setStyleSheet("color: #565f89; padding: 4px;")
        layout.addWidget(self.info_label)

        # Device table
        self.table = qtw.QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(["Name", "Device ID", "Address", "Port", "Version"])
        self.table.setSelectionBehavior(qtw.QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setSelectionMode(qtw.QAbstractItemView.SelectionMode.SingleSelection)
        self.table.setEditTriggers(qtw.QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.verticalHeader().setVisible(False)
        self.table.setAlternatingRowColors(True)
        layout.addWidget(self.table)

        # Action buttons
        btn_layout = qtw.QHBoxLayout()
        self.refresh_btn = qtw.QPushButton("🔄 Refresh")
        self.refresh_btn.clicked.connect(self._on_refresh)
        btn_layout.addWidget(self.refresh_btn)

        btn_layout.addStretch()

        self.send_btn = qtw.QPushButton("📤 Send File")
        self.send_btn.clicked.connect(self._on_send)
        self.send_btn.setEnabled(False)
        btn_layout.addWidget(self.send_btn)

        self.connect_btn = qtw.QPushButton("🔗 Connect")
        self.connect_btn.clicked.connect(self._on_connect)
        self.connect_btn.setEnabled(False)
        btn_layout.addWidget(self.connect_btn)

        layout.addLayout(btn_layout)

        # Store devices
        self._devices: List[DeviceInfo] = []
        self._selected_device: Optional[DeviceInfo] = None

        # Connect selection
        self.table.itemSelectionChanged.connect(self._on_selection_changed)

    @property
    def widget(self):
        return self.panel

    def update_devices(self, devices: List[DeviceInfo]):
        """Update the device list."""
        self._devices = devices

        self.table.setRowCount(len(devices))
        for i, d in enumerate(devices):
            self.table.setItem(i, 0, self._make_item(d.device_name))
            self.table.setItem(i, 1, self._make_item(d.device_id))
            self.table.setItem(i, 2, self._make_item(d.address))
            self.table.setItem(i, 3, self._make_item(str(d.port)))
            self.table.setItem(i, 4, self._make_item(f"v{d.version}"))

        if devices:
            self.info_label.setText(f"📱 {len(devices)} device(s) found")
        else:
            self.info_label.setText("🔍 Scanning... no devices found yet")

    def _make_item(self, text: str):
        """Create a table item with styling."""
        qtw, qtc, qtg = self._get_qt()
        item = qtw.QTableWidgetItem(text)
        return item

    def _get_qt(self):
        """Get Qt modules."""
        import PyQt6.QtWidgets as qtw
        import PyQt6.QtCore as qtc
        import PyQt6.QtGui as qtg
        return qtw, qtc, qtg

    def _on_selection_changed(self):
        """Handle table selection changes."""
        rows = self.table.selectedItems()
        has_selection = len(rows) > 0
        self.send_btn.setEnabled(has_selection)
        self.connect_btn.setEnabled(has_selection)

        if has_selection:
            row = rows[0].row()
            if 0 <= row < len(self._devices):
                self._selected_device = self._devices[row]
            else:
                self._selected_device = None
        else:
            self._selected_device = None

    def _on_refresh(self):
        """Handle refresh button click."""
        self.info_label.setText("🔄 Refreshing...")
        # The daemon's discovery auto-refreshes; just update display

    def _on_send(self):
        """Handle send button click."""
        if not self._selected_device:
            return

        # Open file dialog
        qtw, qtc, qtg = self._get_qt()
        file_path, _ = qtw.QFileDialog.getOpenFileName(
            self.panel,
            "Select File to Send",
            "",
            "All Files (*)",
        )
        if file_path:
            logger.info(
                "User selected file to send: %s to %s",
                file_path,
                self._selected_device.device_name,
            )
            # The parent MainWindow handles actual sending via daemon

    def _on_connect(self):
        """Handle connect button click."""
        if not self._selected_device:
            return
        logger.info(
            "Connecting to %s (%s)",
            self._selected_device.device_name,
            self._selected_device.address,
        )
