use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use rand::Rng;

/// Generate a random 256-bit AES key
pub fn generate_key() -> Vec<u8> {
    let mut key = vec![0u8; 32];
    rand::thread_rng().fill(&mut key);
    key
}

/// Encrypt data using AES-256-GCM
pub fn encrypt(data: &[u8], key: &[u8]) -> Result<Vec<u8>, String> {
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| format!("Key error: {}", e))?;
    let mut nonce_bytes = vec![0u8; 12];
    rand::thread_rng().fill(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);
    let ciphertext = cipher
        .encrypt(nonce, data)
        .map_err(|e| format!("Encrypt error: {}", e))?;
    // Prepend nonce to ciphertext
    let mut result = nonce_bytes.clone();
    result.extend_from_slice(&ciphertext);
    Ok(result)
}

/// Decrypt data using AES-256-GCM (nonce is prepended)
pub fn decrypt(encrypted: &[u8], key: &[u8]) -> Result<Vec<u8>, String> {
    if encrypted.len() < 13 {
        return Err("Data too short".into());
    }
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| format!("Key error: {}", e))?;
    let (nonce_bytes, ciphertext) = encrypted.split_at(12);
    let nonce = Nonce::from_slice(nonce_bytes);
    cipher
        .decrypt(nonce, ciphertext)
        .map_err(|e| format!("Decrypt error: {}", e))
}
