#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Mutex;
use tauri::State;
use tokio::sync::broadcast;

mod discovery;
mod transfer;
mod crypto;
mod streaming;

// ─── State ──────────────────────────────────────────────────────────────

struct AppState {
    device_name: Mutex<String>,
    discovered_devices: Mutex<Vec<DeviceInfo>>,
    transfers: Mutex<Vec<TransferInfo>>,
    encryption_key: Mutex<Option<Vec<u8>>>,
    event_tx: broadcast::Sender<AppEvent>,
}

#[derive(Clone, Serialize, Deserialize)]
struct DeviceInfo {
    id: String,
    name: String,
    device_type: String,
    ip: String,
    port: u16,
    signal: u8,
    is_active: bool,
    capabilities: Vec<String>,
}

#[derive(Clone, Serialize, Deserialize)]
struct TransferInfo {
    id: String,
    filename: String,
    file_size: u64,
    transferred: u64,
    speed_mbps: f64,
    status: String,
    direction: String,
    peer: String,
    progress: u8,
}

#[derive(Clone, Serialize, Deserialize)]
struct AppEvent {
    event_type: String,
    payload: String,
}

#[derive(Clone, Serialize, Deserialize)]
struct StreamSession {
    id: String,
    title: String,
    stream_type: String,
    url: String,
    is_active: bool,
    quality: String,
}

// ─── Commands ───────────────────────────────────────────────────────────

#[tauri::command]
fn get_devices(state: State<AppState>) -> Vec<DeviceInfo> {
    state.discovered_devices.lock().unwrap().clone()
}

#[tauri::command]
fn get_transfers(state: State<AppState>) -> Vec<TransferInfo> {
    state.transfers.lock().unwrap().clone()
}

#[tauri::command]
fn get_device_name(state: State<AppState>) -> String {
    state.device_name.lock().unwrap().clone()
}

#[tauri::command]
fn set_device_name(name: String, state: State<AppState>) {
    *state.device_name.lock().unwrap() = name;
}

#[tauri::command]
async fn send_file(target_id: String, file_path: String, state: State<AppState>) -> Result<(), String> {
    let device = {
        let devices = state.discovered_devices.lock().unwrap();
        devices.iter().find(|d| d.id == target_id).cloned()
    }.ok_or("Device not found")?;

    // This would initiate a WebSocket transfer
    let transfer = TransferInfo {
        id: uuid_v4(),
        filename: file_path.rsplit('/').next().unwrap_or("file").to_string(),
        file_size: std::fs::metadata(&file_path).map(|m| m.len()).unwrap_or(0),
        transferred: 0,
        speed_mbps: 0.0,
        status: "pending".into(),
        direction: "sending".into(),
        peer: device.name.clone(),
        progress: 0,
    };

    state.transfers.lock().unwrap().push(transfer);
    Ok(())
}

#[tauri::command]
async fn receive_file(download_dir: String, state: State<AppState>) -> Result<(), String> {
    std::fs::create_dir_all(&download_dir).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
fn start_discovery(state: State<AppState>) -> Result<(), String> {
    // mDNS discovery would start here in a real implementation
    let devices = vec![
        DeviceInfo {
            id: "android-1".into(),
            name: "Pixel 8 Pro".into(),
            device_type: "Android".into(),
            ip: "192.168.1.42".into(),
            port: 9876,
            signal: 92,
            is_active: true,
            capabilities: vec!["file_transfer".into(), "streaming".into(), "clipboard_sync".into()],
        },
        DeviceInfo {
            id: "ios-1".into(),
            name: "iPhone 16".into(),
            device_type: "iOS".into(),
            ip: "192.168.1.55".into(),
            port: 9876,
            signal: 85,
            is_active: true,
            capabilities: vec!["file_transfer".into(), "streaming".into()],
        },
    ];

    *state.discovered_devices.lock().unwrap() = devices;
    Ok(())
}

#[tauri::command]
fn toggle_encryption(enabled: bool, state: State<AppState>) {
    if enabled {
        *state.encryption_key.lock().unwrap() = Some(crypto::generate_key());
    } else {
        *state.encryption_key.lock().unwrap() = None;
    }
}

#[tauri::command]
fn get_streams() -> Vec<StreamSession> {
    vec![]
}

#[tauri::command]
fn start_stream(stream_type: String, quality: String) -> Result<StreamSession, String> {
    Ok(StreamSession {
        id: uuid_v4(),
        title: format!("{} Stream", stream_type),
        stream_type,
        url: format!("ws://0.0.0.0:{}", 9888),
        is_active: true,
        quality,
    })
}

#[tauri::command]
fn stop_stream(stream_id: String) -> Result<(), String> {
    Ok(())
}

// ─── Main ───────────────────────────────────────────────────────────────

fn main() {
    env_logger::init();

    let (event_tx, _) = broadcast::channel(100);

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_process::init())
        .manage(AppState {
            device_name: Mutex::new(whoami::hostname()),
            discovered_devices: Mutex::new(Vec::new()),
            transfers: Mutex::new(Vec::new()),
            encryption_key: Mutex::new(None),
            event_tx,
        })
        .invoke_handler(tauri::generate_handler![
            get_devices,
            get_transfers,
            get_device_name,
            set_device_name,
            send_file,
            receive_file,
            start_discovery,
            toggle_encryption,
            get_streams,
            start_stream,
            stop_stream,
        ])
        .run(tauri::generate_context!())
        .expect("error while running StreamSync Desktop");
}

fn uuid_v4() -> String {
    use rand::Rng;
    let mut bytes = [0u8; 16];
    rand::thread_rng().fill(&mut bytes);
    // Set version 4
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    // Set variant
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    format!(
        "{:02x}{:02x}{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
        bytes[0], bytes[1], bytes[2], bytes[3],
        bytes[4], bytes[5], bytes[6], bytes[7],
        bytes[8], bytes[9], bytes[10], bytes[11],
        bytes[12], bytes[13], bytes[14], bytes[15]
    )
}
