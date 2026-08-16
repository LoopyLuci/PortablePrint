"""PyQt6/PySide6 GUI application entry point."""

import logging
import os
import sys
from typing import Optional

from streamsync_cli.core.config import StreamSyncConfig

logger = logging.getLogger(__name__)


def run_gui():
    """Run the StreamSync Qt GUI application.

    Auto-detects available Qt bindings (PyQt6 > PySide6).
    """
    qt_app_cls = None

    # Try PyQt6 first, then PySide6
    for mod_name, app_cls_name in [("PyQt6.QtWidgets", "QApplication"), ("PySide6.QtWidgets", "QApplication")]:
        try:
            import importlib
            qtw = importlib.import_module(mod_name)
            qt_app_cls = getattr(qtw, app_cls_name)
            break
        except ImportError:
            continue

    if qt_app_cls is None:
        logger.error("No Qt binding found. Install with: pip install streamsync-cli[gui]")
        return

    config = StreamSyncConfig.load()

    # Create QApplication
    app = qt_app_cls(sys.argv)
    app.setApplicationName("StreamSync")
    app.setApplicationVersion("1.0.0")
    app.setOrganizationName("StreamSync")

    # Apply theme
    _apply_theme(app, config.theme)

    # Create and show main window
    from streamsync_cli.ui.qt_main_window import MainWindow
    window = MainWindow(config)
    window.show()

    sys.exit(app.exec())


def _apply_theme(app, theme: str):
    """Apply a dark or light theme to the Qt application."""
    if theme == "dark":
        dark_style = """
        QMainWindow, QDialog, QWidget {
            background-color: #1a1b26;
            color: #c0caf5;
        }
        QMenuBar {
            background-color: #24283b;
            color: #a9b1d6;
        }
        QMenuBar::item:selected {
            background-color: #2ac3de;
            color: #1a1b26;
        }
        QMenu {
            background-color: #24283b;
            color: #a9b1d6;
            border: 1px solid #3b4261;
        }
        QMenu::item:selected {
            background-color: #2ac3de;
            color: #1a1b26;
        }
        QPushButton {
            background-color: #2ac3de;
            color: #1a1b26;
            border: none;
            padding: 6px 16px;
            border-radius: 4px;
            font-weight: bold;
        }
        QPushButton:hover {
            background-color: #7dcfff;
        }
        QPushButton:pressed {
            background-color: #0db9d7;
        }
        QPushButton:disabled {
            background-color: #3b4261;
            color: #565f89;
        }
        QLineEdit, QSpinBox, QComboBox {
            background-color: #24283b;
            color: #c0caf5;
            border: 1px solid #3b4261;
            border-radius: 4px;
            padding: 4px 8px;
        }
        QLineEdit:focus, QSpinBox:focus, QComboBox:focus {
            border-color: #2ac3de;
        }
        QComboBox::drop-down {
            border: none;
        }
        QComboBox QAbstractItemView {
            background-color: #24283b;
            color: #c0caf5;
            selection-background-color: #2ac3de;
            selection-color: #1a1b26;
        }
        QTableWidget, QListWidget, QTreeWidget {
            background-color: #1a1b26;
            color: #c0caf5;
            border: 1px solid #3b4261;
            gridline-color: #3b4261;
        }
        QTableWidget::item:selected, QListWidget::item:selected, QTreeWidget::item:selected {
            background-color: #2ac3de;
            color: #1a1b26;
        }
        QHeaderView::section {
            background-color: #24283b;
            color: #a9b1d6;
            border: 1px solid #3b4261;
            padding: 4px;
        }
        QTabWidget::pane {
            border: 1px solid #3b4261;
            background-color: #1a1b26;
        }
        QTabBar::tab {
            background-color: #24283b;
            color: #a9b1d6;
            padding: 8px 16px;
            border: 1px solid #3b4261;
            border-bottom: none;
            border-top-left-radius: 4px;
            border-top-right-radius: 4px;
        }
        QTabBar::tab:selected {
            background-color: #1a1b26;
            color: #2ac3de;
            border-bottom: 2px solid #2ac3de;
        }
        QTabBar::tab:hover {
            background-color: #3b4261;
        }
        QLabel {
            color: #c0caf5;
        }
        QGroupBox {
            border: 1px solid #3b4261;
            border-radius: 4px;
            margin-top: 8px;
            padding-top: 16px;
            color: #a9b1d6;
        }
        QGroupBox::title {
            subcontrol-origin: margin;
            left: 10px;
            padding: 0 5px;
        }
        QProgressBar {
            border: 1px solid #3b4261;
            border-radius: 4px;
            text-align: center;
            color: #c0caf5;
            background-color: #24283b;
        }
        QProgressBar::chunk {
            background-color: #2ac3de;
            border-radius: 3px;
        }
        QScrollBar:vertical {
            background-color: #1a1b26;
            width: 10px;
            border: none;
        }
        QScrollBar::handle:vertical {
            background-color: #3b4261;
            border-radius: 5px;
            min-height: 20px;
        }
        QScrollBar::handle:vertical:hover {
            background-color: #565f89;
        }
        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
            height: 0px;
        }
        QStatusBar {
            background-color: #24283b;
            color: #565f89;
        }
        QCheckBox {
            color: #c0caf5;
        }
        QCheckBox::indicator {
            width: 16px;
            height: 16px;
        }
        """
        app.setStyleSheet(dark_style)
