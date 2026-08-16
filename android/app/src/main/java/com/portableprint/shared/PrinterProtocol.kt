package com.portableprint.shared

object PrinterProtocol {
    val ESC: ByteArray = byteArrayOf(0x1B)
    val GS: ByteArray = byteArrayOf(0x1D)

    val INITIALIZE: ByteArray = ESC + byteArrayOf(0x40)
    val JUSTIFY_LEFT: ByteArray = ESC + byteArrayOf(0x61, 0x00)
    val JUSTIFY_CENTER: ByteArray = ESC + byteArrayOf(0x61, 0x01)
    val JUSTIFY_RIGHT: ByteArray = ESC + byteArrayOf(0x61, 0x02)
    val PRINT_FEED: ByteArray = ESC + byteArrayOf(0x64, 0x02)
    val GSV0: ByteArray = GS + byteArrayOf(0x76, 0x30, 0x00)
    val HEADER: ByteArray = INITIALIZE + JUSTIFY_CENTER + byteArrayOf(0x1f, 0x11, 0x02, 0x04)
    val FOOTER: ByteArray = byteArrayOf(0x1F, 0x11, 0x08, 0x1F, 0x11, 0x0E, 0x1f, 0x11, 0x07, 0x1F, 0x11, 0x09)
}
