/// Crypto module — AES-256-GCM encryption for StreamSync transfers.
use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use rand::Rng;

const NONCE_SIZE: usize = 12;

/// Generate a random 256-bit AES key.
pub fn generate_key() -> [u8; 32] {
    let mut key = [0u8; 32];
    rand::thread_rng().fill(&mut key);
    key
}

/// Encrypt data with AES-256-GCM. Nonce is prepended to ciphertext.
pub fn encrypt(data: &[u8], key: &[u8; 32]) -> Result<Vec<u8>, String> {
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| format!("Key: {}", e))?;
    let mut nonce_bytes = [0u8; NONCE_SIZE];
    rand::thread_rng().fill(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);
    let ciphertext = cipher.encrypt(nonce, data).map_err(|e| format!("Encrypt: {}", e))?;
    let mut out = nonce_bytes.to_vec();
    out.extend_from_slice(&ciphertext);
    Ok(out)
}

/// Decrypt data encrypted with encrypt(). Nonce is expected prepended.
pub fn decrypt(data: &[u8], key: &[u8; 32]) -> Result<Vec<u8>, String> {
    if data.len() < NONCE_SIZE + 1 {
        return Err("Data too short".into());
    }
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| format!("Key: {}", e))?;
    let (nonce_bytes, ciphertext) = data.split_at(NONCE_SIZE);
    let nonce = Nonce::from_slice(nonce_bytes);
    cipher.decrypt(nonce, ciphertext).map_err(|e| format!("Decrypt: {}", e))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encrypt_decrypt() {
        let key = generate_key();
        let plaintext = b"Hello StreamSync!";
        let encrypted = encrypt(plaintext, &key).unwrap();
        assert_ne!(encrypted, plaintext);
        let decrypted = decrypt(&encrypted, &key).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_key_generation() {
        let k1 = generate_key();
        let k2 = generate_key();
        assert_ne!(k1, k2);
    }
}
