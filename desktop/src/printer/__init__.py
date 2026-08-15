import socket
from PIL import Image, ImageEnhance
import os
import platform

from src.printer.backend import create_backend

# ESC/POS constants
ESC = b"\x1B"
GS = b"\x1D"

INITIALIZE = ESC + b"\x40"
JUSTIFY_LEFT = ESC + b"\x61\x00"
JUSTIFY_CENTER = ESC + b"\x61\x01"
JUSTIFY_RIGHT = ESC + b"\x61\x02"
PRINT_FEED = ESC + b"\x64\x02"
GSV0 = GS + b"\x76\x30\x00"
HEADER = INITIALIZE + JUSTIFY_CENTER + b"\x1f\x11\x02\x04"
FOOTER = b"\x1F\x11\x08\x1F\x11\x0E\x1f\x11\x07\x1F\x11\x09"

MAX_CHARS_PER_LINE = 14
LINE_HEIGHT_BITS = 40


def _charset():
    try:
        from phomemo_printer.pixel_sans.charset import charset
        return charset
    except Exception:
        return {"CHAR_NOT_FOUND": b"\x00\x00\x00\x00\x00"}


def build_text_print_bytes(text: str) -> bytes:
    newline_separated_text = text.split("\n")
    final_bytes = HEADER

    for newline_chunk in newline_separated_text:
        words = newline_chunk.split(" ")
        chunk = ""
        text_lines = []
        for word in words:
            delimiter = "" if len(chunk) == 0 else " "
            if len(word) + len(chunk) + len(delimiter) > MAX_CHARS_PER_LINE:
                if len(chunk) != 0 and len(chunk) <= MAX_CHARS_PER_LINE:
                    text_lines.append(chunk)
                    chunk = word
                else:
                    word_hunks = [
                        chunk[i : i + MAX_CHARS_PER_LINE - 1]
                        for i in range(0, len(chunk), MAX_CHARS_PER_LINE - 1)
                    ]
                    for word_hunk in word_hunks[:-2]:
                        text_lines.append(word_hunk + "-")
                    if len(word_hunks[-1]) == 1:
                        text_lines.append(word_hunks[-2] + word_hunks[-1])
                    else:
                        text_lines.append(word_hunks[-2] + "-")
                        chunk = word_hunks[-1]
                    delimiter = "" if len(chunk) == 0 else " "
                    if len(word) + len(chunk) + len(delimiter) > MAX_CHARS_PER_LINE:
                        text_lines.append(chunk)
                        chunk = word
                    else:
                        chunk = chunk + delimiter + word
            else:
                chunk = chunk + delimiter + word

        if len(chunk) <= MAX_CHARS_PER_LINE:
            text_lines.append(chunk)
        else:
            word_hunks = [
                chunk[i : i + MAX_CHARS_PER_LINE - 1]
                for i in range(0, len(chunk), MAX_CHARS_PER_LINE - 1)
            ]
            for word_hunk in word_hunks[:-2]:
                text_lines.append(word_hunk + "-")
            if len(word_hunks[-1]) == 1:
                text_lines.append(word_hunks[-2] + word_hunks[-1])
            else:
                text_lines.append(word_hunks[-2] + "-")
                text_lines.append(word_hunks[-1])

        line_data_list = [b"" for _ in range(LINE_HEIGHT_BITS)]
        for text_line in text_lines:
            bytes_per_line = len(text_line) * 5
            if bytes_per_line > MAX_CHARS_PER_LINE * 5:
                raise ValueError("Line too long to print")

            BLOCK_MARKER = (
                GSV0
                + bytes([bytes_per_line])
                + b"\x00"
                + bytes([LINE_HEIGHT_BITS])
                + b"\x00"
            )

            line_data = line_data_list.copy()
            for char in text_line:
                try:
                    char_bytes_list = _charset()[char]
                except KeyError:
                    char_bytes_list = _charset()["CHAR_NOT_FOUND"]
                for index, char_bytes in enumerate(char_bytes_list):
                    line_data[index] += char_bytes

            final_bytes += BLOCK_MARKER
            for bit_line in line_data:
                final_bytes += bit_line

        final_bytes += PRINT_FEED

    final_bytes += PRINT_FEED
    final_bytes += FOOTER
    return final_bytes


def build_image_print_bytes(image_path: str, brightness: float | None = None) -> bytes:
    image = Image.open(image_path)
    if image.width > image.height:
        image = image.transpose(Image.ROTATE_90)

    IMAGE_WIDTH_BYTES = 70
    IMAGE_WIDTH_BITS = IMAGE_WIDTH_BYTES * 8
    image = image.resize(
        size=(IMAGE_WIDTH_BITS, int(image.height * IMAGE_WIDTH_BITS / image.width))
    )

    if brightness is not None:
        enhancer = ImageEnhance.Brightness(image)
        image = enhancer.enhance(brightness)

    image = image.convert(mode="1")

    final_bytes = HEADER
    for start_index in range(0, image.height, 256):
        end_index = (
            start_index + 256 if image.height - 256 > start_index else image.height
        )
        line_height = end_index - start_index

        BLOCK_MARKER = (
            GSV0
            + bytes([IMAGE_WIDTH_BYTES])
            + b"\x00"
            + bytes([line_height - 1])
            + b"\x00"
        )
        final_bytes += BLOCK_MARKER

        for image_line_index in range(line_height):
            image_line = b""
            for byte_start in range(int(image.width / 8)):
                byte = 0
                for bit in range(8):
                    if (
                        image.getpixel(
                            (byte_start * 8 + bit, image_line_index + start_index)
                        )
                        == 0
                    ):
                        byte |= 1 << (7 - bit)
                if byte == 0x0A:
                    byte = 0x14
                image_line += byte.to_bytes(1, "little")
            final_bytes += image_line

    final_bytes += PRINT_FEED
    final_bytes += PRINT_FEED
    final_bytes += FOOTER
    return final_bytes


def print_text(bt_address: str, channel: int, text: str):
    payload = build_text_print_bytes(text)
    backend = create_backend()
    try:
        backend.connect(bt_address, channel)
        backend.send(payload)
    finally:
        backend.close()


def print_image(bt_address: str, channel: int, image_path: str, brightness: float | None = None):
    payload = build_image_print_bytes(image_path, brightness=brightness)
    backend = create_backend()
    try:
        backend.connect(bt_address, channel)
        backend.send(payload)
    finally:
        backend.close()
