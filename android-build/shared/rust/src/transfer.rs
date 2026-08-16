/// Transfer module — chunked file transfer with resume support.
use sha2::{Sha256, Digest};

pub const DEFAULT_CHUNK_SIZE: usize = 64 * 1024; // 64KB

/// A single file chunk for transfer.
#[derive(Clone)]
pub struct Chunk {
    pub data: Vec<u8>,
    pub offset: u64,
    pub index: u32,
    pub total: u32,
}

/// Split file data into chunks for transfer.
pub fn chunk_file(data: &[u8], chunk_size: usize) -> Vec<Chunk> {
    let total = ((data.len() + chunk_size - 1) / chunk_size) as u32;
    data.chunks(chunk_size)
        .enumerate()
        .map(|(i, chunk)| Chunk {
            data: chunk.to_vec(),
            offset: (i as u64) * (chunk_size as u64),
            index: i as u32,
            total,
        })
        .collect()
}

/// Reassemble chunks back into a complete file, sorted by index.
pub fn reassemble_chunks(chunks: &[Chunk]) -> Vec<u8> {
    let mut sorted = chunks.to_vec();
    sorted.sort_by_key(|c| c.index);
    let mut out = Vec::with_capacity(sorted.iter().map(|c| c.data.len()).sum());
    for chunk in &sorted {
        out.extend_from_slice(&chunk.data);
    }
    out
}

/// Calculate SHA-256 hash of data.
pub fn sha256_hash(data: &[u8]) -> Vec<u8> {
    let mut hasher = Sha256::new();
    hasher.update(data);
    hasher.finalize().to_vec()
}

/// Verify a file hash.
pub fn verify_hash(data: &[u8], expected: &[u8]) -> bool {
    sha256_hash(data).as_slice() == expected
}

/// Calculate transfer progress stats.
pub fn progress(transferred: u64, total: u64, start_ms: u64) -> (f64, u32, u64) {
    let elapsed_ms = crate::protocol::now_ms() - start_ms;
    let speed = if elapsed_ms > 0 {
        (transferred as f64) / (elapsed_ms as f64) * 1000.0 / 1_000_000.0
    } else { 0.0 };
    let percent = if total > 0 {
        ((transferred * 100) / total) as u32
    } else { 0 };
    let remaining = if speed > 0.0 {
        ((total - transferred) as f64 / (speed * 1_000_000.0 / 1000.0)) as u64
    } else { 0 };
    (speed, percent.clamp(0, 100), remaining)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_chunk_and_reassemble() {
        let data: Vec<u8> = (0..200).map(|i| (i % 256) as u8).collect();
        let chunks = chunk_file(&data, 64);
        assert!(chunks.len() >= 3);
        let reassembled = reassemble_chunks(&chunks);
        assert_eq!(data, reassembled);
    }

    #[test]
    fn test_sha256() {
        let hash = sha256_hash(b"test data");
        assert_eq!(hash.len(), 32);
        assert!(verify_hash(b"test data", &hash));
        assert!(!verify_hash(b"wrong data", &hash));
    }
}
