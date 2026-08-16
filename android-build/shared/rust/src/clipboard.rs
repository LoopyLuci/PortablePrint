/// Clipboard module — clipboard sync message handling.
use crate::proto;

/// A clipboard entry synchronized between devices.
#[derive(Clone, Debug)]
pub struct ClipboardEntry {
    pub text: Option<String>,
    pub source_device: String,
    pub timestamp_ms: u64,
}

/// Extract clipboard text from a StreamSyncMessage.
pub fn parse_clipboard_message(msg: &proto::StreamSyncMessage) -> Option<ClipboardEntry> {
    if let Some(proto::stream_sync_message::Payload::Clipboard(ref clip)) = msg.payload {
        return Some(ClipboardEntry {
            text: clip.content.as_ref().and_then(|c| {
                if let proto::clipboard_message::Content::Text(ref t) = c {
                    Some(t.clone())
                } else {
                    None
                }
            }),
            source_device: clip.source_device_id.clone(),
            timestamp_ms: clip.timestamp_ms,
        });
    }
    None
}

/// Build a clipboard sync message from text.
pub fn build_clipboard_message(text: &str, source_device: &str) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: crate::protocol::random_id(),
        timestamp_ms: crate::protocol::now_ms(),
        sender_id: source_device.to_string(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Clipboard(
            proto::ClipboardMessage {
                session_id: String::new(),
                timestamp_ms: crate::protocol::now_ms(),
                source_device_id: source_device.to_string(),
                content: Some(proto::clipboard_message::Content::Text(text.to_string())),
            },
        )),
    }
}
