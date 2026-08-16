package com.streamsync.shared

/**
 * JVM/Android implementation of currentTimeMillis.
 */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
