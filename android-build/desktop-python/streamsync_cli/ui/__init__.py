"""User interface modules - auto-detects best available backend."""
import logging
import importlib.util
import os

logger = logging.getLogger(__name__)

# Signal to Qt modules that we're running standalone
_QT_AVAILABLE = None
_TEXTUAL_AVAILABLE = None


def _check_qt() -> bool:
    """Check if a Qt binding is available."""
    global _QT_AVAILABLE
    if _QT_AVAILABLE is not None:
        return _QT_AVAILABLE
    for mod_name in ("PyQt6", "PySide6"):
        if importlib.util.find_spec(mod_name) is not None:
            _QT_AVAILABLE = True
            return True
    _QT_AVAILABLE = False
    return False


def _check_textual() -> bool:
    """Check if Textual is available."""
    global _TEXTUAL_AVAILABLE
    if _TEXTUAL_AVAILABLE is not None:
        return _TEXTUAL_AVAILABLE
    if importlib.util.find_spec("textual") is not None:
        _TEXTUAL_AVAILABLE = True
        return True
    _TEXTUAL_AVAILABLE = False
    return False


def detect_ui_backend() -> str:
    """Detect the best UI backend available.

    Returns:
        'gui' if PyQt6/PySide6 is installed,
        'tui' if Textual is installed,
        'cli' otherwise.
    """
    force = os.environ.get("STREAMSYNC_UI", "").lower()
    if force in ("gui", "qt"):
        if _check_qt():
            return "gui"
        logger.warning("STREAMSYNC_UI=gui but no Qt binding found")
    elif force in ("tui", "textual"):
        if _check_textual():
            return "tui"
        logger.warning("STREAMSYNC_UI=tui but Textual not found")
    elif force == "cli":
        return "cli"

    if _check_qt():
        return "gui"
    if _check_textual():
        return "tui"
    return "cli"
