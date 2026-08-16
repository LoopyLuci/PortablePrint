/// Protocol message serialization and deserialization.
use crate::proto;
use prost::Message;

/// Serialize a StreamSyncMessage to bytes.
pub fn serialize_message(msg: &proto::StreamSyncMessage) -> Vec<u8> {
    let mut buf = Vec::with_capacity(msg.encoded_len());
    msg.encode(&mut buf).expect("Message too large");
    buf
}

/// Deserialize bytes into a StreamSyncMessage.
pub fn deserialize_message(bytes: &[u8]) -> Result<proto::StreamSyncMessage, prost::DecodeError> {
    proto::StreamSyncMessage::decode(bytes)
}

/// Create a Hello discovery message.
pub fn create_hello(identity: &proto::DeviceIdentity, listen_port: u32) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: identity.device_id.clone(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Discovery(
            proto::DiscoveryMessage {
                msg: Some(proto::discovery_message::Msg::Hello(proto::Hello {
                    identity: Some(identity.clone()),
                    listen_port,
                    advertised_services: vec!["file_transfer".into(), "streaming".into()],
                    session_token: vec![],
                })),
            },
        )),
    }
}

/// Create a HelloAck response.
pub fn create_hello_ack(device_id: &str, device_name: &str, device_type: i32, accept: bool, reason: &str) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: device_id.to_string(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Discovery(
            proto::DiscoveryMessage {
                msg: Some(proto::discovery_message::Msg::HelloAck(proto::HelloAck {
                    identity: Some(proto::DeviceIdentity {
                        device_id: device_id.to_string(),
                        device_name: device_name.to_string(),
                        device_type,
                        os_version: std::env::consts::OS.to_string(),
                        app_version: "1.0.0".into(),
                        protocol_version: 2,
                        capabilities: vec![],
                        public_key: vec![],
                    }),
                    accept_connection: accept,
                    reason: reason.to_string(),
                })),
            },
        )),
    }
}

/// Create a transfer request message.
pub fn create_transfer_request(
    sender_id: &str, target_id: &str, filename: &str,
    file_size: u64, mime: &str,
) -> proto::StreamSyncMessage {
    let transfer_id = uuid_v4();
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: sender_id.to_string(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Transfer(
            proto::TransferMessage {
                msg: Some(proto::transfer_message::Msg::Request(
                    proto::TransferRequest {
                        transfer_id: transfer_id.clone(),
                        transfer_type: 0,
                        sender_id: sender_id.to_string(),
                        target_id: target_id.to_string(),
                        encryption: 1,
                        compression: 0,
                        files: vec![proto::FileInfo {
                            file_id: uuid_v4(),
                            filename: filename.to_string(),
                            file_size,
                            mime_type: mime.to_string(),
                            file_hash: vec![],
                            relative_path: String::new(),
                            metadata: std::collections::HashMap::new(),
                        }],
                        stream_type: 0,
                        stream_url: String::new(),
                        stream_port: 0,
                        stream_params: std::collections::HashMap::new(),
                        text_payload: String::new(),
                        url_payload: String::new(),
                        clipboard_data: vec![],
                        clipboard_mime: String::new(),
                        screen_width: 0,
                        screen_height: 0,
                        screen_fps: 0,
                        screen_quality: 0,
                        total_size: file_size,
                        custom_headers: std::collections::HashMap::new(),
                    },
                )),
            },
        )),
    }
}

/// Create a transfer response.
pub fn create_transfer_response(transfer_id: &str, accepted: bool, reason: &str, offset: u64) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: String::new(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Transfer(
            proto::TransferMessage {
                msg: Some(proto::transfer_message::Msg::Response(
                    proto::TransferResponse {
                        transfer_id: transfer_id.to_string(),
                        accepted,
                        reason: reason.to_string(),
                        max_chunk_size: 65536,
                        offset,
                        missing_chunks: vec![],
                        alt_stream_url: String::new(),
                    },
                )),
            },
        )),
    }
}

/// Create a transfer chunk message.
pub fn create_chunk(
    transfer_id: &str, file_id: &str, data: Vec<u8>,
    offset: u64, chunk_index: u32, total_chunks: u32,
) -> proto::StreamSyncMessage {
    let checksum = crc32_cksum(&data);
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: String::new(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Transfer(
            proto::TransferMessage {
                msg: Some(proto::transfer_message::Msg::Chunk(
                    proto::TransferChunk {
                        transfer_id: transfer_id.to_string(),
                        file_id: file_id.to_string(),
                        offset,
                        length: data.len() as u32,
                        data,
                        chunk_index,
                        total_chunks,
                        checksum: checksum.to_le_bytes().to_vec(),
                        is_encrypted: false,
                    },
                )),
            },
        )),
    }
}

/// Create a transfer complete message.
pub fn create_transfer_complete(transfer_id: &str, file_ids: Vec<String>, total_bytes: u64, elapsed_ms: u64, speed: f64) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: String::new(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Transfer(
            proto::TransferMessage {
                msg: Some(proto::transfer_message::Msg::Complete(
                    proto::TransferComplete {
                        transfer_id: transfer_id.to_string(),
                        file_ids,
                        total_bytes,
                        elapsed_ms,
                        avg_speed_mbps: speed,
                        final_hash: vec![],
                    },
                )),
            },
        )),
    }
}

/// Create a transfer error message.
pub fn create_transfer_error(transfer_id: &str, code: u32, message: &str, retryable: bool) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: String::new(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Transfer(
            proto::TransferMessage {
                msg: Some(proto::transfer_message::Msg::Error(
                    proto::TransferError {
                        transfer_id: transfer_id.to_string(),
                        error_code: code,
                        error_message: message.to_string(),
                        retryable,
                        error_details: std::collections::HashMap::new(),
                    },
                )),
            },
        )),
    }
}

/// Create a heartbeat message.
pub fn create_heartbeat(battery_level: f64, is_charging: bool, active_transfers: u32) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: String::new(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Control(
            proto::ControlMessage {
                msg: Some(proto::control_message::Msg::Heartbeat(
                    proto::Heartbeat {
                        timestamp_ms: now_ms(),
                        battery_level,
                        is_charging,
                        active_transfers,
                    },
                )),
            },
        )),
    }
}

/// Create a Goodbye message.
pub fn create_goodbye(reason: &str) -> proto::StreamSyncMessage {
    proto::StreamSyncMessage {
        protocol_version: 2,
        message_id: random_id(),
        timestamp_ms: now_ms(),
        sender_id: String::new(),
        sender_signature: vec![],
        payload: Some(proto::stream_sync_message::Payload::Discovery(
            proto::DiscoveryMessage {
                msg: Some(proto::discovery_message::Msg::Goodbye(
                    proto::Goodbye {
                        reason: reason.to_string(),
                        reconnect_delay_seconds: 5,
                    },
                )),
            },
        )),
    }
}

// ─── Public utility ────────────────────────────────────────────────────

/// Generate a random message ID.
pub fn random_id() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    (nanos ^ (nanos >> 32)) as u64
}

/// Get current time in milliseconds.
pub fn now_ms() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

// ─── Internal ──────────────────────────────────────────────────────────

fn crc32_cksum(data: &[u8]) -> u32 {
    data.iter().fold(0u32, |c, &b| {
        (c >> 8) ^ CRC32_TABLE[((c ^ b as u32) & 0xFF) as usize]
    })
}

fn uuid_v4() -> String {
    use rand::Rng;
    let mut bytes = [0u8; 16];
    rand::thread_rng().fill(&mut bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    format!(
        "{:02x}{:02x}{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
        bytes[0], bytes[1], bytes[2], bytes[3],
        bytes[4], bytes[5], bytes[6], bytes[7],
        bytes[8], bytes[9], bytes[10], bytes[11],
        bytes[12], bytes[13], bytes[14], bytes[15]
    )
}

static CRC32_TABLE: [u32; 256] = {
    let mut table = [0u32; 256];
    let mut i = 0;
    while i < 256 {
        let mut crc = i as u32;
        let mut j = 0;
        while j < 8 {
            if crc & 1 != 0 {
                crc = 0xedb88320 ^ (crc >> 1);
            } else {
                crc >>= 1;
            }
            j += 1;
        }
        table[i] = crc;
        i += 1;
    }
    table
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hello_message() {
        let identity = proto::DeviceIdentity {
            device_id: "test-device".into(),
            device_name: "Test Phone".into(),
            device_type: 1,
            os_version: "14".into(),
            app_version: "1.0.0".into(),
            protocol_version: 2,
            capabilities: vec![],
            public_key: vec![],
        };
        let msg = create_hello(&identity, 9876);
        let bytes = serialize_message(&msg);
        assert!(bytes.len() > 10);
        let decoded = deserialize_message(&bytes).unwrap();
        assert_eq!(decoded.sender_id, "test-device");
    }

    #[test]
    fn test_transfer_request() {
        let msg = create_transfer_request("sender", "target", "test.txt", 1024, "text/plain");
        let bytes = serialize_message(&msg);
        assert!(bytes.len() > 20);
        let decoded = deserialize_message(&bytes).unwrap();
        match decoded.payload.unwrap() {
            proto::stream_sync_message::Payload::Transfer(tm) => match tm.msg.unwrap() {
                proto::transfer_message::Msg::Request(req) => {
                    assert_eq!(req.files[0].filename, "test.txt");
                    assert_eq!(req.files[0].file_size, 1024);
                }
                _ => panic!("Expected request"),
            },
            _ => panic!("Expected transfer payload"),
        }
    }

    #[test]
    fn test_chunk_roundtrip() {
        let data = vec![1u8, 2, 3, 4, 5];
        let msg = create_chunk("tx1", "f1", data.clone(), 0, 0, 1);
        let bytes = serialize_message(&msg);
        let decoded = deserialize_message(&bytes).unwrap();
        match decoded.payload.unwrap() {
            proto::stream_sync_message::Payload::Transfer(tm) => match tm.msg.unwrap() {
                proto::transfer_message::Msg::Chunk(chunk) => {
                    assert_eq!(&chunk.data[..], &data[..]);
                    assert_eq!(chunk.chunk_index, 0);
                }
                _ => panic!("Expected chunk"),
            },
            _ => panic!("Expected transfer payload"),
        }
    }
}
