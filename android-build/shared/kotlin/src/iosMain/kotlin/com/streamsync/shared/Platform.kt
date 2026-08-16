package com.streamsync.shared

import platform.Foundation.NSDate

/**
 * iOS implementation of currentTimeMillis using NSDate.
 */
internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
