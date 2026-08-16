# StreamSync 

**Instant File Transfer & Content Streaming — Any Device, Anywhere**

StreamSync is a next-generation cross-platform peer-to-peer communication suite that enables **instant file transfer** and **content streaming** between any devices on your local network. No cloud, no servers, no data limits — just your devices talking directly.

![Platforms](https://img.shields.io/badge/platform-Android%20|%20iOS%20|%20Windows%20|%20macOS%20|%20Linux-4A90D9)
![Protocol](https://img.shields.io/badge/protocol-StreamSync%20v2-34C759)
![License](https://img.shields.io/badge/license-MIT-F6F8FA)

---

## ✨ Features

### 📤 Instant File Transfer
- **Zero-config discovery** — Devices find each other automatically via mDNS
- **Chunked transfer** with **resume support** — Large files split into 64KB chunks
- **End-to-end encryption** — AES-256-GCM for all transfers
- **Up to 1Gbps speeds** on local networks
- **Background transfers** — Continue sending/receiving even with app minimized

### 📺 Content Streaming
- **Video streaming** — Cast movies and shows to any device
- **Audio streaming** — Play music on remote speakers
- **Screen mirroring** — Share your screen in real-time
- **Camera feed** — Use your phone as a wireless webcam
- **Adaptive quality** — Auto-negotiates resolution based on network conditions

### 📋 Clipboard Sync
- **Universal clipboard** — Copy on one device, paste on another
- **Real-time sync** — Changes propagate instantly
- **History** — Access recent clipboard entries

### 🔒 Privacy & Security
- **No cloud servers** — Direct peer-to-peer connections only
- **End-to-end encryption** — Your data never leaves your network unencrypted
- **No accounts required** — Just install and go
- **Open source** — Fully auditable protocol and implementation

---

## 🏗 Architecture

```
streamsync/
├── protocol/              # StreamSync Protocol v2.0 (protobuf)
│   └── streamsync.proto
├── android/               # Android App (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/streamsync/android/
├── ios/                   # iOS App (Swift + SwiftUI)
│   └── StreamSync/
├── desktop-rust/          # Desktop Companion (Rust + Tauri)
│   └── src-tauri/src/
├── desktop-python/        # Desktop Companion (Python CLI + GUI)
│   └── streamsync_cli/
└── shared/                # Shared Libraries
    └── rust/              # Rust core protocol library
```

### Protocol Stack

| Layer | Technology |
|-------|-----------|
| **Discovery** | mDNS/DNS-SD (Android NSD, Apple Bonjour, Avahi/Zeroconf) |
| **Transport** | WebSocket (secure) over TCP |
| **Serialization** | JSON (lightweight) → Protobuf (production) |
| **Encryption** | AES-256-GCM / ChaCha20-Poly1305 |
| **Streaming** | Chunked HLS-like adaptive bitrate |

---

## 📱 Android App

The flagship StreamSync experience.

**Stack:** Kotlin, Jetpack Compose, Ktor WebSocket, ExoPlayer, Android NSD

### Key Components

| Component | Description |
|-----------|-------------|
| `DiscoveryService` | mDNS discovery using Android NSD |
| `TransferService` | Chunked file transfer with WebSocket + encryption |
| `StreamService` | Content streaming via ExoPlayer |
| `ClipboardSyncService` | Cross-device clipboard synchronization |
| `ProtocolHandler` | Message serialization, encryption, chunking |

### Screens

| Screen | Description |
|--------|-------------|
| **Dashboard** | Quick stats, action cards, recent transfers |
| **Devices** | Discovered devices with capabilities |
| **Transfers** | All/active/completed transfers with progress |
| **Stream** | Stream targets and active streams |
| **Clipboard** | Clipboard sync history |
| **Settings** | Device name, encryption, quality preferences |

### Building

```bash
cd android
./gradlew assembleDebug
```

Requires Android SDK 34, Kotlin 1.9.22, Gradle 8.5.

---

## 📱 iOS Companion App

**Stack:** Swift, SwiftUI, Network Framework (NWConnection/NWListener), AVPlayer

### Key Components

| Component | Description |
|-----------|-------------|
| `DiscoveryService` | Bonjour/mDNS discovery via Network framework |
| `TransferService` | WebSocket file transfer with NWConnection |
| `StreamService` | Content streaming via AVPlayer |
| `CryptoService` | AES-256-GCM encryption/decryption |
| `ClipboardService` | Clipboard sync via UIPasteboard |

### Building

```bash
cd ios/StreamSync
xcodebuild -project StreamSync.xcodeproj -scheme StreamSync build
```

Requires Xcode 15+, iOS 16+ deployment target.

---

## 🖥 Desktop Companion (Rust/Tauri)

**Stack:** Rust, Tauri, WebSocket, mDNS

### Key Features
- Full GUI with file drag-and-drop
- Device discovery via mDNS
- File transfer with progress visualization
- Content streaming receiver
- Clipboard sync
- System tray integration

### Building

```bash
cd desktop-rust
cargo tauri build
```

Requires Rust 1.75+, Node.js 18+.

---

## 🖥 Desktop Companion (Python)

**Stack:** Python, Click (CLI), PyQt6 (GUI), zeroconf, websockets

### CLI Usage

```bash
# Scan for devices
streamsync discover

# Send a file
streamsync send photo.jpg --device "Living Room TV"

# Receive files
streamsync receive ~/Downloads/

# Stream a video
streamsync stream movie.mp4

# Sync clipboard
streamsync clipboard daemon
```

### GUI Mode

```bash
streamsync gui
```

Auto-launches PyQt6 GUI (falls back to Textual TUI).

### Building

```bash
cd desktop-python
pip install -e .
```

Requires Python 3.10+, zeroconf, websockets, cryptography, pyperclip.

---

## 📜 StreamSync Protocol v2.0

The protocol is defined in [`protocol/streamsync.proto`](protocol/streamsync.proto).

### Message Flow

```
1. Discovery (mDNS)
   ────────────────
   Device A: HELLO (announce presence)
   Device B: HELLO_ACK (acknowledge)

2. Transfer Setup (WebSocket)
   ─────────────────────────
   Device A → Device B: TRANSFER_REQUEST
   Device B → Device A: TRANSFER_RESPONSE (accepted/declined)

3. Data Transfer
   ─────────────
   Device A → Device B: TRANSFER_CHUNK (0..N)
   Device A → Device B: TRANSFER_CHUNK (with progress updates)

4. Completion
   ──────────
   Device A → Device B: TRANSFER_COMPLETE (with hash verification)
```

### Discovery Attributes

| Attribute | Description |
|-----------|-------------|
| `device_id` | Unique device identifier |
| `device_name` | Human-readable device name |
| `device_type` | ANDROID, IOS, DESKTOP_WINDOWS, etc. |
| `capabilities` | Comma-separated feature list |
| `protocol_version` | Protocol version for compatibility |

---

## 🚀 Getting Started

### Quick Start

1. **Install on all devices:**
   - Android: Build from source or install APK
   - iOS: Build from source
   - Desktop: Install Rust or Python version

2. **Connect:**
   - Ensure all devices are on the same Wi-Fi network
   - Open StreamSync on each device
   - Devices discover each other automatically

3. **Transfer:**
   - Tap a device to see available actions
   - Choose Send Files, Send URL, Send Text, or Stream
   - Watch real-time transfer progress

4. **Stream:**
   - Open the Stream tab
   - Choose content type (Video, Audio, Screen Mirror)
   - Select target device
   - Control playback remotely

---

## 🛡 Security

- **Encryption:** All transfers encrypted with AES-256-GCM
- **Key exchange:** Ed25519 public keys exchanged during pairing
- **Verification:** SHA-256 hash verification on completed transfers
- **No persistence:** Encryption keys never stored to disk

---

## 🔧 Configuration

### Android Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Device Name | Model Name | Visible to other devices |
| Auto-accept | Off | Automatically accept incoming files |
| Encryption | On | AES-256-GCM for all transfers |
| Stream Quality | Auto | Adaptive resolution and bitrate |
| Clipboard Sync | On | Sync clipboard across devices |
| Background Discovery | On | Find devices in background |

---

## 📊 Performance

| Scenario | Speed | Notes |
|----------|-------|-------|
| Small file (< 1MB) | < 1 second | Instant transfer |
| Large file (1GB) | ~8 seconds | Gigabit WiFi |
| Video stream (1080p) | < 200ms latency | Adaptive bitrate |
| Screen mirror | ~100ms latency | Delta-frame encoding |
| Clipboard sync | < 500ms | Real-time propagation |

---

## 🔮 Roadmap

- [ ] **Folder transfer** — Send entire directory structures
- [ ] **Stream recording** — Save streams to local storage
- [ ] **Remote playback control** — Full remote control of playback
- [ ] **Android Auto** — Stream to car display
- [ ] **Chromecast support** — Cast to Chromecast devices
- [ ] **End-to-end encrypted messaging** — Real-time chat
- [ ] **Internet mode** — Relay for devices not on same network
- [ ] **Wake-on-LAN** — Wake sleeping devices

---

## 📝 License

MIT License — See [LICENSE](LICENSE) for details.

---

## 🤝 Contributing

StreamSync is open source and welcomes contributions. Check the issues tab for open tasks, or propose new features via pull requests.

---

> **StreamSync — Your data, your devices, your network. No clouds, no limits.**
