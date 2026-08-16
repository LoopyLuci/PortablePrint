"""StreamSync Desktop Companion - Cross-platform file transfer & content streaming."""

__version__ = "1.0.0"
__author__ = "StreamSync Contributors"
__license__ = "MIT"

import logging

# Configure package-level logger
logger = logging.getLogger("streamsync_cli")
logger.addHandler(logging.NullHandler())

from streamsync_cli.core.config import StreamSyncConfig
