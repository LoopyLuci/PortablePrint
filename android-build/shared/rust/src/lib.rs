// StreamSync Core Library
// Shared Rust core for protocol serialization, mDNS discovery,
// WebSocket transport, encryption, file transfer, streaming,
// clipboard sync, and screen mirroring.

/// Generated protobuf code for the StreamSync protocol.
#[allow(clippy::all)]
#[allow(non_snake_case)]
#[allow(non_camel_case_types)]
pub mod proto {
    include!(concat!(env!("OUT_DIR"), "/streamsync.rs"));
}

pub mod protocol;
pub mod discovery;
pub mod transport;
pub mod crypto;
pub mod transfer;
pub mod streaming;
pub mod clipboard;
pub mod screen;

use thiserror::Error;

/// Library-level error types.
#[derive(Error, Debug)]
pub enum StreamSyncError {
    #[error("Protocol error: {0}")]
    Protocol(String),

    #[error("Transport error: {0}")]
    Transport(String),

    #[error("Discovery error: {0}")]
    Discovery(String),

    #[error("Encryption error: {0}")]
    Encryption(String),

    #[error("Transfer error: {0}")]
    Transfer(String),

    #[error("Streaming error: {0}")]
    Streaming(String),

    #[error("Clipboard error: {0}")]
    Clipboard(String),

    #[error("Screen mirroring error: {0}")]
    Screen(String),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Serialization error: {0}")]
    Serialization(String),
}

impl From<prost::EncodeError> for StreamSyncError {
    fn from(e: prost::EncodeError) -> Self {
        StreamSyncError::Serialization(e.to_string())
    }
}

impl From<prost::DecodeError> for StreamSyncError {
    fn from(e: prost::DecodeError) -> Self {
        StreamSyncError::Serialization(e.to_string())
    }
}

/// Result type used throughout the library.
pub type Result<T> = std::result::Result<T, StreamSyncError>;

/// Protocol version constant matching the proto definition.
pub const PROTOCOL_VERSION: u32 = 2;

/// Default WebSocket port for StreamSync.
pub const DEFAULT_WS_PORT: u16 = 8891;

/// Default mDNS service type.
pub const MDNS_SERVICE_TYPE: &str = "_streamsync._tcp.local.";

/// Maximum chunk size for file transfers (256KB).
pub const MAX_CHUNK_SIZE: u32 = 262_144;

/// Application version.
pub const APP_VERSION: &str = env!("CARGO_PKG_VERSION");

/// Get the current local device identity.
pub fn local_device_id() -> String {
    let hostname = hostname();
    format!("streamsync-{}", hostname.replace('.', "-"))
}

/// Get the hostname of the current machine.
pub fn hostname() -> String {
    std::env::var("COMPUTERNAME")
        .or_else(|_| std::env::var("HOSTNAME"))
        .unwrap_or_else(|_| "unknown-device".to_string())
}
