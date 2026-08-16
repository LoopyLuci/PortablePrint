"""Main window for the StreamSync Qt GUI application."""

import logging
import threading
import time
from pathlib import Path
from typing import Optional

from streamsync_cli.core.config import StreamSyncConfig
from streamsync_cli.core.discovery import DeviceInfo, DeviceDiscovery
from streamsync_cli.core.transfer import TransferInfo
from streamsync_cli.server.server import StreamSyncDaemon

logger = logging.getLogger(__name__)


class MainWindow:
    """Main application window.

    This is implemented as a QWidget-compatible wrapper that
    works with both PyQt6 and PySide6. The actual widget setup
    uses the detected Qt binding.
    """

    def __init__(self, config: StreamSyncConfig):
        self.config = config
        self.daemon = StreamSyncDaemon(config)

        # Import Qt bindings (already checked in qt_app.run_gui)
        self._import_qt()

        # Build the window
        self._build()

        # Start the daemon
        self._start_daemon()

        # Start UI update timer
        self._start_update_timer()

    def _import_qt(self):
        """Import the available Qt binding."""
        try:
            from PyQt6.QtCore import Qt, QTimer, QSize
            from PyQt6.QtGui import QAction, QIcon, QFont
            from PyQt6.QtWidgets import (
                QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
                QTabWidget, QStatusBar, QMenuBar, QMenu,
                QMessageBox, QSystemTrayIcon, QApplication,
            )
            self.Qt = Qt
            self.QTimer = QTimer
            self.QAction = QAction
            self.QIcon = QIcon
            self.QFont = QFont
            self.QMainWindow = QMainWindow
            self.QWidget = QWidget
            self.QVBoxLayout = QVBoxLayout
            self.QHBoxLayout = QHBoxLayout
            self.QTabWidget = QTabWidget
            self.QStatusBar = QStatusBar
            self.QMenuBar = QMenuBar
            self.QMenu = QMenu
            self.QMessageBox = QMessageBox
            self.QSystemTrayIcon = QSystemTrayIcon
            self.QApplication = QApplication
        except ImportError:
            # Fallback to PySide6
            from PySide6.QtCore import Qt, QTimer, QSize  # type: ignore
            from PySide6.QtGui import QAction, QIcon, QFont
            from PySide6.QtWidgets import (
                QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
                QTabWidget, QStatusBar, QMenuBar, QMenu,
                QMessageBox, QSystemTrayIcon, QApplication,
            )
            self.Qt = Qt
            self.QTimer = QTimer
            self.QAction = QAction
            self.QIcon = QIcon
            self.QFont = QFont
            self.QMainWindow = QMainWindow
            self.QWidget = QWidget
            self.QVBoxLayout = QVBoxLayout
            self.QHBoxLayout = QHBoxLayout
            self.QTabWidget = QTabWidget
            self.QStatusBar = QStatusBar
            self.QMenuBar = QMenuBar
            self.QMenu = QMenu
            self.QMessageBox = QMessageBox
            self.QSystemTrayIcon = QSystemTrayIcon
            self.QApplication = QApplication

    def _build(self):
        """Build the main window UI."""
        # Main window
        self.window = self.QMainWindow()
        self.window.setWindowTitle(f"StreamSync - {self.config.device_name}")
        self.window.setMinimumSize(900, 600)
        self.window.resize(1100, 700)

        # Central widget with tabs
        central = self.QWidget()
        self.window.setCentralWidget(central)
        layout = self.QVBoxLayout(central)

        self.tabs = self.QTabWidget()
        layout.addWidget(self.tabs)

        # Create tab panels
        from streamsync_cli.ui.qt_devices import DevicesPanel
        from streamsync_cli.ui.qt_transfer import TransferPanel
        from streamsync_cli.ui.qt_streaming import StreamingPanel
        from streamsync_cli.ui.qt_settings import SettingsDialog

        self.devices_panel = DevicesPanel(self.config, self._import_qt_impl)
        self.transfer_panel = TransferPanel(self.config, self._import_qt_impl)
        self.streaming_panel = StreamingPanel(self.config, self._import_qt_impl)

        self.tabs.addTab(self.devices_panel, "🔍 Devices")
        self.tabs.addTab(self.transfer_panel, "📁 Transfers")
        self.tabs.addTab(self.streaming_panel, "📺 Streaming")

        # Menu bar
        self._build_menu()

        # Status bar
        self.status_bar = self.QStatusBar()
        self.window.setStatusBar(self.status_bar)
        self.status_label = self.QWidget()
        self.status_bar.addPermanentWidget(self.status_label)

        # System tray
        self._setup_tray()

    def _import_qt_impl(self):
        """Import Qt and return the module for use in child widgets."""
        try:
            import PyQt6.QtWidgets as qtw
            import PyQt6.QtCore as qtc
            import PyQt6.QtGui as qtg
            return qtw, qtc, qtg
        except ImportError:
            import PySide6.QtWidgets as qtw  # type: ignore
            import PySide6.QtCore as qtc
            import PySide6.QtGui as qtg
            return qtw, qtc, qtg

    def _build_menu(self):
        """Build the menu bar."""
        menu_bar = self.window.menuBar()

        # File menu
        file_menu = menu_bar.addMenu("&File")
        settings_action = self.QAction("&Settings...", self.window)
        settings_action.triggered.connect(self._show_settings)
        file_menu.addAction(settings_action)
        file_menu.addSeparator()
        exit_action = self.QAction("E&xit", self.window)
        exit_action.triggered.connect(self.window.close)
        file_menu.addAction(exit_action)

        # View menu
        view_menu = menu_bar.addMenu("&View")
        for i, name in enumerate(["🔍 Devices", "📁 Transfers", "📺 Streaming"]):
            action = self.QAction(name, self.window)
            idx = i
            action.triggered.connect(lambda checked, i=idx: self.tabs.setCurrentIndex(i))
            view_menu.addAction(action)

        # Help menu
        help_menu = menu_bar.addMenu("&Help")
        about_action = self.QAction("&About StreamSync", self.window)
        about_action.triggered.connect(self._show_about)
        help_menu.addAction(about_action)

    def _setup_tray(self):
        """Set up the system tray icon."""
        if not self.QSystemTrayIcon.isSystemTrayAvailable():
            return

        self.tray_icon = self.QSystemTrayIcon(self.window)
        self.tray_icon.setToolTip(f"StreamSync - {self.config.device_name}")

        tray_menu = self.QMenu()
        show_action = self.QAction("Show", self.window)
        show_action.triggered.connect(self.window.show)
        tray_menu.addAction(show_action)

        hide_action = self.QAction("Hide", self.window)
        hide_action.triggered.connect(self.window.hide)
        tray_menu.addAction(hide_action)

        tray_menu.addSeparator()
        quit_action = self.QAction("Quit", self.window)
        quit_action.triggered.connect(self.QApplication.quit)
        tray_menu.addAction(quit_action)

        self.tray_icon.setContextMenu(tray_menu)
        self.tray_icon.activated.connect(self._on_tray_activated)
        self.tray_icon.show()

    def _on_tray_activated(self, reason):
        """Handle system tray icon activation."""
        if reason == self.QSystemTrayIcon.ActivationReason.DoubleClick:
            self.window.show()
            self.window.raise_()

    def _start_daemon(self):
        """Start the StreamSync daemon."""
        self.daemon.start()
        logger.info("GUI daemon started")

    def _start_update_timer(self):
        """Start periodic UI update timer."""
        self._update_timer = self.QTimer()
        self._update_timer.timeout.connect(self._update_ui)
        self._update_timer.start(2000)  # Update every 2 seconds

    def _update_ui(self):
        """Update UI panels with latest data."""
        try:
            # Update devices panel
            devices = (
                self.daemon.discovery.devices if self.daemon.discovery else []
            )
            self.devices_panel.update_devices(devices)

            # Update transfer panel
            transfers = (
                self.daemon.transfer_manager.get_all_transfers()
                if self.daemon.transfer_manager
                else []
            )
            self.transfer_panel.update_transfers(transfers)

            # Update status bar
            n_devices = len(devices)
            n_connections = (
                self.daemon.transport.connection_count
                if self.daemon.transport
                else 0
            )
            n_transfers = len(
                self.daemon.transfer_manager.get_active_transfers()
                if self.daemon.transfer_manager
                else []
            )
            self.status_bar.showMessage(
                f"📱 {n_devices} devices  |  "
                f"🔗 {n_connections} connections  |  "
                f"📦 {n_transfers} active transfers"
            )
        except Exception as e:
            logger.debug("UI update error: %s", e)

    def _show_settings(self):
        """Show the settings dialog."""
        from streamsync_cli.ui.qt_settings import SettingsDialog
        dialog = SettingsDialog(self.config, self.window, self._import_qt_impl)

        # We need to pass the widget itself, not the wrapper
        # The SettingsDialog handles this internally
        if dialog.exec():
            self.config.save()
            logger.info("Settings updated")

    def _show_about(self):
        """Show the about dialog."""
        self.QMessageBox.about(
            self.window,
            "About StreamSync",
            f"<h2>StreamSync Desktop Companion</h2>"
            f"<p>Version 1.0.0</p>"
            f"<p>Cross-platform file transfer, content streaming, "
            f"and clipboard sync for devices without a native client.</p>"
            f"<p>Device: <b>{self.config.device_name}</b></p>"
            f"<p>ID: <code>{self.config.device_id}</code></p>",
        )

    def show(self):
        """Show the main window."""
        self.window.show()

    def close(self):
        """Clean shutdown."""
        self.daemon.stop()
        self.window.close()
