/// Screen mirroring module — frame capture and transmission.
use crate::proto;

/// Configuration for screen mirroring.
#[derive(Clone, Debug)]
pub struct ScreenMirrorConfig {
    pub width: u32,
    pub height: u32,
    pub quality: u8,
    pub fps: u32,
    pub max_bitrate_kbps: u32,
    pub enable_delta_frames: bool,
}

impl Default for ScreenMirrorConfig {
    fn default() -> Self {
        Self {
            width: 1920,
            height: 1080,
            quality: 70,
            fps: 15,
            max_bitrate_kbps: 5000,
            enable_delta_frames: true,
        }
    }
}

/// A screen frame for transmission.
pub struct ScreenFrame {
    pub sequence: u64,
    pub jpeg_data: Vec<u8>,
    pub width: u32,
    pub height: u32,
    pub is_delta: bool,
    pub timestamp_ms: u64,
}

/// Simulate capturing a screen frame (real impl uses OS capture APIs via `scrap` / `xcap`).
pub fn capture_frame(_config: &ScreenMirrorConfig) -> Option<ScreenFrame> {
    None
}

/// Convert a screen frame to a StreamSyncMessage.
pub fn frame_to_message(frame: &ScreenFrame, session_id: &str) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: crate::protocol::random_id(),
        timestamp_ms: frame.timestamp_ms,
        sender_id: session_id.to_string(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::ScreenFrame(
            proto::ScreenFrame {
                session_id: session_id.to_string(),
                sequence: frame.sequence,
                width: frame.width,
                height: frame.height,
                jpeg_data: frame.jpeg_data.clone(),
                quality: 70,
                is_delta: frame.is_delta,
                dirty_regions: vec![],
                timestamp_ms: frame.timestamp_ms,
            },
        )),
    }
}

/// Parse a screen frame from a message.
pub fn message_to_frame(msg: &proto::StreamSyncMessage) -> Option<ScreenFrame> {
    if let Some(proto::stream_sync_message::Payload::ScreenFrame(ref frame)) = msg.payload {
        Some(ScreenFrame {
            sequence: frame.sequence,
            jpeg_data: frame.jpeg_data.clone(),
            width: frame.width,
            height: frame.height,
            is_delta: frame.is_delta,
            timestamp_ms: frame.timestamp_ms,
        })
    } else {
        None
    }
}
