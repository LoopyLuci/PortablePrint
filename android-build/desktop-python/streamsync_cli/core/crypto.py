"""Encryption module using AES-256-GCM via the cryptography library."""

import base64
import hashlib
import logging
import os
from typing import Optional, Tuple

from cryptography.fernet import Fernet
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

logger = logging.getLogger(__name__)

# Key derivation: use HKDF to turn a password/passphrase into a 256-bit key
SALT_LENGTH = 32
NONCE_LENGTH = 12  # 96 bits for AES-GCM
TAG_LENGTH = 16  # 128-bit authentication tag


def generate_key() -> bytes:
    """Generate a new random 256-bit AES key."""
    return AESGCM.generate_key(bit_length=256)


def derive_key(password: str, salt: Optional[bytes] = None) -> Tuple[bytes, bytes]:
    """Derive a 256-bit AES key from a password string using HKDF.

    Args:
        password: The password/passphrase.
        salt: Optional salt; generated randomly if not provided.

    Returns:
        Tuple of (key, salt).
    """
    if salt is None:
        salt = os.urandom(SALT_LENGTH)

    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        info=b"streamsync-key-v1",
    )
    key = hkdf.derive(password.encode("utf-8"))
    return key, salt


def encrypt(data: bytes, key: bytes) -> bytes:
    """Encrypt data using AES-256-GCM.

    Args:
        data: The plaintext bytes to encrypt.
        key: 32-byte AES key.

    Returns:
        Nonce || ciphertext || tag (all concatenated).
    """
    if len(key) != 32:
        raise ValueError("Key must be 32 bytes (256-bit)")

    aesgcm = AESGCM(key)
    nonce = os.urandom(NONCE_LENGTH)
    ciphertext = aesgcm.encrypt(nonce, data, associated_data=None)
    return nonce + ciphertext


def decrypt(data: bytes, key: bytes) -> bytes:
    """Decrypt data using AES-256-GCM.

    Args:
        data: Nonce || ciphertext || tag.
        key: 32-byte AES key.

    Returns:
        Decrypted plaintext bytes.
    """
    if len(key) != 32:
        raise ValueError("Key must be 32 bytes (256-bit)")
    if len(data) < NONCE_LENGTH + TAG_LENGTH:
        raise ValueError("Data too short to contain nonce and tag")

    aesgcm = AESGCM(key)
    nonce = data[:NONCE_LENGTH]
    ciphertext = data[NONCE_LENGTH:]
    return aesgcm.decrypt(nonce, ciphertext, associated_data=None)


def encrypt_string(plaintext: str, key: bytes) -> str:
    """Encrypt a string to a Base64-encoded string."""
    return base64.b64encode(encrypt(plaintext.encode("utf-8"), key)).decode("ascii")


def decrypt_string(ciphertext_b64: str, key: bytes) -> str:
    """Decrypt a Base64-encoded ciphertext string."""
    raw = base64.b64decode(ciphertext_b64)
    return decrypt(raw, key).decode("utf-8")


def key_to_b64(key: bytes) -> str:
    """Convert a binary key to Base64 for storage."""
    return base64.b64encode(key).decode("ascii")


def b64_to_key(key_b64: str) -> bytes:
    """Restore a binary key from Base64 storage."""
    return base64.b64decode(key_b64)


# Legacy support using Fernet (simpler symmetric encryption for small messages)
def generate_fernet_key() -> bytes:
    """Generate a Fernet-compatible key."""
    return Fernet.generate_key()


def fernet_encrypt(data: bytes, key: bytes) -> bytes:
    """Encrypt with Fernet (AES-128-CBC + HMAC)."""
    f = Fernet(key)
    return f.encrypt(data)


def fernet_decrypt(data: bytes, key: bytes) -> bytes:
    """Decrypt with Fernet."""
    f = Fernet(key)
    return f.decrypt(data)
