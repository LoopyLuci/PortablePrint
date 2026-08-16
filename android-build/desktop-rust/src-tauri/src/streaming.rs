/// Content streaming module for screen mirroring and media streaming.

use std::collections::HashMap;
use serde::{Deserialize, Serialize};

#[derive(Clone, Serialize, Deserialize)]
pub struct StreamConfig {
    pub stream_type: String, // "video", "audio", "screen", "camera"
    pub width: u32,
    pub height: u32,
    pub fps: u32,
    pub bitrate_kbps: u32,
    pub codec: String,
}

impl Default for StreamConfig {
    fn default() -> Self {
        Self {
            stream_type: "video".into(),
            width: 1920,
            height: 1080,
            fps: 30,
            bitrate_kbps: 5000,
            codec: "h264".into(),
        }
    }
}

pub fn create_stream_url(host: &str, port: u16, stream_id: &str) -> String {
    format!("ws://{}:{}/stream/{}", host, port, stream_id)
}

pub fn negotiate_quality(remote_width: u32, remote_height: u32, bandwidth_kbps: u32) -> StreamConfig {
    let mut config = StreamConfig::default();

    // Auto-negotiate quality based on available bandwidth
    if bandwidth_kbps >= 15000 && remote_width >= 3840 {
        config = StreamConfig { width: 3840, height: 2160, fps: 60, bitrate_kbps: 16000, ..Default::default() };
    } else if bandwidth_kbps >= 5000 && remote_width >= 1920 {
        config = StreamConfig { width: 1920, height: 1080, fps: 30, bitrate_kbps: 5000, ..Default::default() };
    } else if bandwidth_kbps >= 2500 {
        config = StreamConfig { width: 1280, height: 720, fps: 30, bitrate_kbps: 2500, ..Default::default() };
    } else {
        config = StreamConfig { width: 854, height: 480, fps: 24, bitrate_kbps: 800, ..Default::default() };
    }

    config
}
