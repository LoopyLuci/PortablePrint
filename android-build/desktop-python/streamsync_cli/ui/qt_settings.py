"""Settings dialog for the StreamSync Qt GUI."""

import logging
from pathlib import Path
from typing import Callable, Optional

from streamsync_cli.core.config import StreamSyncConfig

logger = logging.getLogger(__name__)


class SettingsDialog:
    """Settings dialog for configuring StreamSync."""

    def __init__(
        self,
        config: StreamSyncConfig,
        parent=None,
        import_qt: Optional[Callable] = None,
    ):
        self.config = config
        self._result = False

        if import_qt:
            qtw, qtc, qtg = import_qt()
        else:
            import PyQt6.QtWidgets as qtw  # type: ignore
            import PyQt6.QtCore as qtc
            import PyQt6.QtGui as qtg

        self._qtw = qtw
        self._qtc = qtc
        self._qtg = qtg

        # Build dialog
        self.dialog = qtw.QDialog(parent)
        self.dialog.setWindowTitle("StreamSync Settings")
        self.dialog.setMinimumSize(500, 450)

        layout = qtw.QVBoxLayout(self.dialog)

        # Tab widget for settings categories
        tabs = qtw.QTabWidget()
        layout.addWidget(tabs)

        # General tab
        general_tab = self._build_general_tab()
        tabs.addTab(general_tab, "General")

        # Network tab
        network_tab = self._build_network_tab()
        tabs.addTab(network_tab, "Network")

        # Transfers tab
        transfer_tab = self._build_transfer_tab()
        tabs.addTab(transfer_tab, "Transfers")

        # Security tab
        security_tab = self._build_security_tab()
        tabs.addTab(security_tab, "Security")

        # Buttons
        btn_layout = qtw.QHBoxLayout()
        btn_layout.addStretch()

        cancel_btn = qtw.QPushButton("Cancel")
        cancel_btn.clicked.connect(self.dialog.reject)
        btn_layout.addWidget(cancel_btn)

        save_btn = qtw.QPushButton("💾 Save")
        save_btn.clicked.connect(self._on_save)
        btn_layout.addWidget(save_btn)

        layout.addLayout(btn_layout)

    def _build_general_tab(self) -> object:
        """Build the general settings tab."""
        qtw = self._qtw
        tab = qtw.QWidget()
        layout = qtw.QFormLayout(tab)

        # Device name
        self.device_name_input = qtw.QLineEdit(self.config.device_name)
        layout.addRow("Device Name:", self.device_name_input)

        # Device ID (read-only)
        device_id_input = qtw.QLineEdit(self.config.device_id)
        device_id_input.setReadOnly(True)
        layout.addRow("Device ID:", device_id_input)

        # Download directory
        dir_layout = qtw.QHBoxLayout()
        self.download_dir_input = qtw.QLineEdit(self.config.download_dir)
        dir_layout.addWidget(self.download_dir_input)

        browse_btn = qtw.QPushButton("📂")
        browse_btn.setFixedWidth(40)
        browse_btn.clicked.connect(self._on_browse_download)
        dir_layout.addWidget(browse_btn)

        layout.addRow("Download Directory:", dir_layout)

        # Theme
        self.theme_combo = qtw.QComboBox()
        self.theme_combo.addItems(["dark", "light"])
        self.theme_combo.setCurrentText(self.config.theme)
        layout.addRow("Theme:", self.theme_combo)

        # Notifications
        self.notifications_check = qtw.QCheckBox("Enable notifications")
        self.notifications_check.setChecked(self.config.notifications_enabled)
        layout.addRow("", self.notifications_check)

        # Minimize to tray
        self.tray_check = qtw.QCheckBox("Minimize to system tray")
        self.tray_check.setChecked(self.config.minimize_to_tray)
        layout.addRow("", self.tray_check)

        return tab

    def _build_network_tab(self) -> object:
        """Build the network settings tab."""
        qtw = self._qtw
        tab = qtw.QWidget()
        layout = qtw.QFormLayout(tab)

        # Port
        self.port_spin = qtw.QSpinBox()
        self.port_spin.setRange(1024, 65535)
        self.port_spin.setValue(self.config.port)
        layout.addRow("WebSocket Port:", self.port_spin)

        # Stream port
        self.stream_port_spin = qtw.QSpinBox()
        self.stream_port_spin.setRange(1024, 65535)
        self.stream_port_spin.setValue(self.config.stream_port)
        layout.addRow("Streaming Port:", self.stream_port_spin)

        # Discovery interval
        self.discovery_spin = qtw.QDoubleSpinBox()
        self.discovery_spin.setRange(1.0, 60.0)
        self.discovery_spin.setSingleStep(0.5)
        self.discovery_spin.setValue(self.config.discovery_interval)
        layout.addRow("Discovery Interval (s):", self.discovery_spin)

        # Device timeout
        self.timeout_spin = qtw.QDoubleSpinBox()
        self.timeout_spin.setRange(5.0, 300.0)
        self.timeout_spin.setSingleStep(5.0)
        self.timeout_spin.setValue(self.config.device_timeout)
        layout.addRow("Device Timeout (s):", self.timeout_spin)

        return tab

    def _build_transfer_tab(self) -> object:
        """Build the transfer settings tab."""
        qtw = self._qtw
        tab = qtw.QWidget()
        layout = qtw.QFormLayout(tab)

        # Max chunk size
        self.chunk_spin = qtw.QSpinBox()
        self.chunk_spin.setRange(64, 64 * 1024)  # 64 KB to 64 MB
        self.chunk_spin.setValue(self.config.max_chunk_size // (1024 * 1024))
        self.chunk_spin.setSuffix(" MB")
        layout.addRow("Chunk Size:", self.chunk_spin)

        # Max concurrent transfers
        self.concurrent_spin = qtw.QSpinBox()
        self.concurrent_spin.setRange(1, 20)
        self.concurrent_spin.setValue(self.config.max_concurrent_transfers)
        layout.addRow("Max Concurrent:", self.concurrent_spin)

        # Auto-accept
        self.auto_accept_check = qtw.QCheckBox("Auto-accept incoming transfers")
        self.auto_accept_check.setChecked(self.config.auto_accept_transfers)
        layout.addRow("", self.auto_accept_check)

        return tab

    def _build_security_tab(self) -> object:
        """Build the security settings tab."""
        qtw = self._qtw
        tab = qtw.QWidget()
        layout = qtw.QFormLayout(tab)

        # Encryption
        self.encryption_check = qtw.QCheckBox("Enable encryption (AES-256-GCM)")
        self.encryption_check.setChecked(self.config.encryption_enabled)
        layout.addRow("", self.encryption_check)

        # Auth required
        self.auth_check = qtw.QCheckBox("Require authentication")
        self.auth_check.setChecked(self.config.require_auth)
        layout.addRow("", self.auth_check)

        # Auth token
        self.auth_token_input = qtw.QLineEdit(self.config.auth_token)
        self.auth_token_input.setEchoMode(qtw.QLineEdit.EchoMode.Password)
        self.auth_token_input.setPlaceholderText("Authentication token")
        layout.addRow("Auth Token:", self.auth_token_input)

        return tab

    def _on_browse_download(self):
        """Handle download directory browse."""
        qtw = self._qtw
        dir_path = qtw.QFileDialog.getExistingDirectory(
            self.dialog,
            "Select Download Directory",
            self.download_dir_input.text(),
        )
        if dir_path:
            self.download_dir_input.setText(dir_path)

    def _on_save(self):
        """Handle save button click."""
        # Update config from form fields
        self.config.device_name = self.device_name_input.text()
        self.config.download_dir = self.download_dir_input.text()
        self.config.theme = self.theme_combo.currentText()
        self.config.notifications_enabled = self.notifications_check.isChecked()
        self.config.minimize_to_tray = self.tray_check.isChecked()

        self.config.port = self.port_spin.value()
        self.config.stream_port = self.stream_port_spin.value()
        self.config.discovery_interval = self.discovery_spin.value()
        self.config.device_timeout = self.timeout_spin.value()

        self.config.max_chunk_size = self.chunk_spin.value() * 1024 * 1024
        self.config.max_concurrent_transfers = self.concurrent_spin.value()
        self.config.auto_accept_transfers = self.auto_accept_check.isChecked()

        self.config.encryption_enabled = self.encryption_check.isChecked()
        self.config.require_auth = self.auth_check.isChecked()
        self.config.auth_token = self.auth_token_input.text()

        # Save to file
        self.config.save()
        self._result = True
        self.dialog.accept()

    def exec(self) -> bool:
        """Run the dialog modally.

        Returns:
            True if the user saved, False if cancelled.
        """
        self.dialog.exec()
        return self._result
