"""StreamSync CLI entry point - python -m streamsync_cli."""

import sys
import logging


def main():
    """Detect available UI backends and launch the most appropriate one."""
    from streamsync_cli.ui import detect_ui_backend

    # Parse quick flags before deferring to click
    args = [a.lower() for a in sys.argv[1:]]

    if "--gui" in args or "-g" in args:
        # Remove the flag so click doesn't see it
        clean_args = [a for a in sys.argv[1:] if a.lower() not in ("--gui", "-g")]
        sys.argv[1:] = clean_args
        try:
            from streamsync_cli.ui.qt_app import run_gui

            run_gui()
        except ImportError:
            logging.error("PyQt6 is not installed. Install with: pip install streamsync-cli[gui]")
            sys.exit(1)
        return

    if "--tui" in args or "-t" in args:
        clean_args = [a for a in sys.argv[1:] if a.lower() not in ("--tui", "-t")]
        sys.argv[1:] = clean_args
        try:
            from streamsync_cli.ui.tui_app import run_tui

            run_tui()
        except ImportError:
            logging.error("Textual is not installed. Install with: pip install streamsync-cli[tui]")
            sys.exit(1)
        return

    # Default: try GUI, fallback to CLI
    if detect_ui_backend() == "gui":
        try:
            from streamsync_cli.ui.qt_app import run_gui

            run_gui()
            return
        except ImportError:
            pass

    # Fall through to CLI
    from streamsync_cli.ui.cli_app import cli

    cli()


if __name__ == "__main__":
    main()
