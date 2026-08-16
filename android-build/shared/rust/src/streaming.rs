/// Streaming module — content streaming and adaptive quality negotiation.

/// Configuration for a media stream.
#[derive(Clone, Debug)]
pub struct StreamConfig {
    pub stream_type: StreamType,
    pub width: u32,
    pub height: u32,
    pub fps: u32,
    pub bitrate_kbps: u32,
    pub codec: String,
    pub has_audio: bool,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum StreamType {
    Video,
    Audio,
    Screen,
    Camera,
    Microphone,
    File,
}

impl Default for StreamConfig {
    fn default() -> Self {
        Self {
            stream_type: StreamType::Video,
            width: 1920,
            height: 1080,
            fps: 30,
            bitrate_kbps: 5000,
            codec: "h264".into(),
            has_audio: true,
        }
    }
}

impl StreamConfig {
    /// Create a config appropriate for the given bandwidth.
    pub fn negotiate(bandwidth_kbps: u32, _max_width: u32) -> Self {
        if bandwidth_kbps >= 15000 {
            Self { width: 3840, height: 2160, fps: 60, bitrate_kbps: 16000, ..Default::default() }
        } else if bandwidth_kbps >= 8000 {
            Self { width: 1920, height: 1080, fps: 60, bitrate_kbps: 8000, ..Default::default() }
        } else if bandwidth_kbps >= 5000 {
            Self { width: 1920, height: 1080, fps: 30, bitrate_kbps: 5000, ..Default::default() }
        } else if bandwidth_kbps >= 2500 {
            Self { width: 1280, height: 720, fps: 30, bitrate_kbps: 2500, ..Default::default() }
        } else {
            Self { width: 854, height: 480, fps: 24, bitrate_kbps: 800, ..Default::default() }
        }
    }

    pub fn stream_url(&self, host: &str, port: u16, stream_id: &str) -> String {
        format!("ws://{}:{}/stream/{}", host, port, stream_id)
    }
}
