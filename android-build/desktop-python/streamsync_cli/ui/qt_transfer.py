"""Transfer panel for the Qt GUI - shows file transfer progress."""

import logging
import threading
from typing import Callable, List, Optional

from streamsync_cli.core.config import StreamSyncConfig
from streamsync_cli.core.transfer import TransferInfo, TransferStatus

logger = logging.getLogger(__name__)


class TransferPanel:
    """Panel showing file transfer status and progress."""

    def __init__(self, config: StreamSyncConfig, import_qt: Callable):
        self.config = config
        qtw, qtc, qtg = import_qt()

        self.panel = qtw.QWidget()
        layout = qtw.QVBoxLayout(self.panel)

        # Header
        header = qtw.QLabel("📁 File Transfers")
        header_font = qtg.QFont()
        header_font.setPointSize(14)
        header_font.setBold(True)
        header.setFont(header_font)
        layout.addWidget(header)

        # Active transfers section
        active_label = qtw.QLabel("Active Transfers")
        active_font = qtg.QFont()
        active_font.setBold(True)
        active_label.setFont(active_font)
        layout.addWidget(active_label)

        # Transfer list
        self.transfer_list = qtw.QListWidget()
        self.transfer_list.setMinimumHeight(200)
        layout.addWidget(self.transfer_list)

        # Transfer detail area
        detail_label = qtw.QLabel("Transfer Details")
        detail_label.setFont(active_font)
        layout.addWidget(detail_label)

        detail_widget = qtw.QWidget()
        detail_layout = qtw.QFormLayout(detail_widget)

        self.file_label = qtw.QLabel("No transfer selected")
        detail_layout.addRow("File:", self.file_label)

        self.device_label = qtw.QLabel("-")
        detail_layout.addRow("Device:", self.device_label)

        self.status_label = qtw.QLabel("-")
        detail_layout.addRow("Status:", self.status_label)

        self.progress_bar = qtw.QProgressBar()
        self.progress_bar.setValue(0)
        detail_layout.addRow("Progress:", self.progress_bar)

        self.speed_label = qtw.QLabel("-")
        detail_layout.addRow("Speed:", self.speed_label)

        self.size_label = qtw.QLabel("-")
        detail_layout.addRow("Size:", self.size_label)

        layout.addWidget(detail_widget)

        # Action buttons
        btn_layout = qtw.QHBoxLayout()
        btn_layout.addStretch()

        self.cancel_btn = qtw.QPushButton("❌ Cancel")
        self.cancel_btn.clicked.connect(self._on_cancel)
        self.cancel_btn.setEnabled(False)
        btn_layout.addWidget(self.cancel_btn)

        self.clear_btn = qtw.QPushButton("🗑️ Clear Completed")
        self.clear_btn.clicked.connect(self._on_clear)
        btn_layout.addWidget(self.clear_btn)

        layout.addLayout(btn_layout)

        # Store transfers
        self._transfers: List[TransferInfo] = []
        self._selected_transfer: Optional[TransferInfo] = None

        # Connect selection
        self.transfer_list.currentRowChanged.connect(self._on_selection_changed)

    @property
    def widget(self):
        return self.panel

    def update_transfers(self, transfers: List[TransferInfo]):
        """Update the transfer list."""
        self._transfers = transfers

        # Preserve selection
        current_row = self.transfer_list.currentRow()
        self.transfer_list.clear()

        for t in transfers:
            icon = "↑" if t.direction == "send" else "↓"
            status_icon = {
                TransferStatus.PENDING: "⏳",
                TransferStatus.IN_PROGRESS: "🔄",
                TransferStatus.COMPLETED: "✅",
                TransferStatus.FAILED: "❌",
                TransferStatus.CANCELLED: "🚫",
            }.get(t.status, "❓")

            text = (
                f"{status_icon} {icon} {t.filename[:30]:30s} "
                f"[{t.progress:.0f}%] {t.status.value}"
            )
            self.transfer_list.addItem(text)

        # Restore selection
        if current_row >= 0 and current_row < self.transfer_list.count():
            self.transfer_list.setCurrentRow(current_row)

    def _on_selection_changed(self, row: int):
        """Handle transfer selection changes."""
        if row < 0 or row >= len(self._transfers):
            self._selected_transfer = None
            self.file_label.setText("No transfer selected")
            self.device_label.setText("-")
            self.status_label.setText("-")
            self.progress_bar.setValue(0)
            self.speed_label.setText("-")
            self.size_label.setText("-")
            self.cancel_btn.setEnabled(False)
            return

        t = self._transfers[row]
        self._selected_transfer = t

        self.file_label.setText(t.filename)
        self.device_label.setText(f"{t.device_name} ({t.device_id})")
        self.status_label.setText(t.status.value)
        self.progress_bar.setValue(int(t.progress))

        if t.speed_bps > 0:
            self.speed_label.setText(self._format_speed(t.speed_bps))
        else:
            self.speed_label.setText("-")

        self.size_label.setText(
            f"{self._format_size(t.bytes_transferred)} / {self._format_size(t.file_size)}"
        )

        self.cancel_btn.setEnabled(t.is_active)

    def _on_cancel(self):
        """Handle cancel button click."""
        if self._selected_transfer:
            logger.info("Cancelling transfer: %s", self._selected_transfer.transfer_id)

    def _on_clear(self):
        """Handle clear completed button click."""
        self.transfer_list.clear()
        logger.info("Cleared completed transfers")

    @staticmethod
    def _format_size(size_bytes: int) -> str:
        """Format byte size to human-readable string."""
        if size_bytes < 1024:
            return f"{size_bytes} B"
        elif size_bytes < 1024 * 1024:
            return f"{size_bytes / 1024:.1f} KB"
        elif size_bytes < 1024 * 1024 * 1024:
            return f"{size_bytes / (1024 * 1024):.1f} MB"
        return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"

    @staticmethod
    def _format_speed(speed_bps: float) -> str:
        """Format transfer speed."""
        if speed_bps < 1024:
            return f"{speed_bps:.0f} B/s"
        elif speed_bps < 1024 * 1024:
            return f"{speed_bps / 1024:.1f} KB/s"
        else:
            return f"{speed_bps / (1024 * 1024):.1f} MB/s"
