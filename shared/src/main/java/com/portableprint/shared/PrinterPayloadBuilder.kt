package com.portableprint.shared

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object PrinterPayloadBuilder {
    private val ESC = byteArrayOf(0x1B)
    private val GS = byteArrayOf(0x1D)

    private val INITIALIZE = ESC + byteArrayOf(0x40)
    private val JUSTIFY_LEFT = ESC + byteArrayOf(0x61, 0x00)
    private val JUSTIFY_CENTER = ESC + byteArrayOf(0x61, 0x01)
    private val JUSTIFY_RIGHT = ESC + byteArrayOf(0x61, 0x02)
    private val PRINT_FEED = ESC + byteArrayOf(0x64, 0x02)
    private val GSV0 = GS + byteArrayOf(0x76, 0x30, 0x00)
    private val HEADER = INITIALIZE + JUSTIFY_CENTER + byteArrayOf(0x1f, 0x11, 0x02, 0x04)
    private val FOOTER = byteArrayOf(0x1F, 0x11, 0x08, 0x1F, 0x11, 0x0E, 0x1f, 0x11, 0x07, 0x1F, 0x11, 0x09)

    private const val MAX_CHARS_PER_LINE = 14
    private const val LINE_HEIGHT_BITS = 40
    private const val IMAGE_WIDTH_BYTES = 70
    private const val IMAGE_WIDTH_BITS = IMAGE_WIDTH_BYTES * 8

    fun buildText(text: String): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        out.write(HEADER)
        val lines = text.split("\n")
        for (line in lines) {
            val wrapped = wrapLine(line)
            for (wrappedLine in wrapped) {
                val bytesPerLine = wrappedLine.length * 5
                if (bytesPerLine > MAX_CHARS_PER_LINE * 5) continue
                out.write(GSV0)
                out.write(byteArrayOf(bytesPerLine.toByte(), 0, LINE_HEIGHT_BITS.toByte(), 0))
                val lineData = Array(LINE_HEIGHT_BITS) { ByteArray(0) }
                for (ch in wrappedLine) {
                    val glyph = charset[ch] ?: charset['?'] ?: ByteArray(5)
                    for (i in lineData.indices) {
                        lineData[i] = lineData[i] + if (i < glyph.size) glyph[i] else ByteArray(0)
                    }
                }
                for (row in lineData) {
                    out.write(row)
                }
            }
            out.write(PRINT_FEED)
        }
        out.write(PRINT_FEED)
        out.write(FOOTER)
        return out
    }

    fun buildImage(bitmap: Bitmap): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        out.write(HEADER)
        val resized = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH_BITS, bitmap.height * IMAGE_WIDTH_BITS / bitmap.width, true)
        for (start in 0 until resized.height step 256) {
            val end = minOf(start + 256, resized.height)
            val lineHeight = end - start
            out.write(GSV0)
            out.write(byteArrayOf(IMAGE_WIDTH_BYTES.toByte(), 0, (lineHeight - 1).toByte(), 0))
            for (y in start until end) {
                var row = 0
                for (x in 0 until IMAGE_WIDTH_BITS) {
                    val pixel = resized.getPixel(x, y)
                    val dark = Color.red(pixel) < 128
                    if (dark) row = row or (1 shl (7 - (x % 8)))
                    if (x % 8 == 7 || x == IMAGE_WIDTH_BITS - 1) {
                        out.write(row and 0xFF)
                        row = 0
                    }
                }
            }
        }
        out.write(PRINT_FEED)
        out.write(PRINT_FEED)
        out.write(FOOTER)
        return out
    }

    private fun wrapLine(text: String): List<String> {
        if (text.length <= MAX_CHARS_PER_LINE) return listOf(text)
        val result = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            result.add(text.substring(i, minOf(i + MAX_CHARS_PER_LINE, text.length)))
            i += MAX_CHARS_PER_LINE - 1
        }
        return result
    }

    private val charset: Map<Char, ByteArray> by lazy {
        // Minimal 5x40 bitmap font placeholder; replace with real pixel font if needed.
        val glyphs = mutableMapOf<Char, ByteArray>()
        for (ch in 'A'..'Z') {
            glyphs[ch] = ByteArray(5) { 0x1F }
        }
        for (ch in 'a'..'z') {
            glyphs[ch] = ByteArray(5) { 0x1F }
        }
        for (ch in '0'..'9') {
            glyphs[ch] = ByteArray(5) { 0x1F }
        }
        val symbols = " !?.,:;'\"-+/="
        for (ch in symbols) {
            glyphs[ch] = ByteArray(5) { 0x0A }
        }
        glyphs[' '] = ByteArray(5) { 0x00 }
        glyphs['\n'] = ByteArray(0)
        glyphs
    }
}
