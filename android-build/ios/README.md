# StreamSync iOS Companion App

Peer-to-peer file transfer & content streaming for iOS, mirroring the Android StreamSync app.

## Architecture

```
StreamSync/
├── StreamSyncApp.swift        # App entry point + AppState (ObservableObject)
├── ContentView.swift          # Main tab view (Dashboard, Devices, Transfers, Streams, Settings)
├── Models/
│   ├── DeviceIdentity.swift    # Device identity, capabilities, device info models
│   ├── DiscoveredDevice.swift  # mDNS discovered device model
│   ├── TransferSession.swift   # File transfer session (ObservableObject)
│   └── StreamSession.swift     # Streaming session model (ObservableObject)
├── Services/
│   ├── DiscoveryService.swift  # Bonjour/mDNS using Network framework (NWBrowser/NWListener)
│   ├── TransferService.swift   # WebSocket-based file transfer over NWConnection
│   ├── StreamService.swift     # AVPlayer-based audio/video streaming
│   ├── CryptoService.swift     # AES-256-GCM, ChaCha20-Poly1305, SHA-256, Ed25519 key exchange
│   └── ClipboardService.swift  # UIPasteboard monitoring and sync
├── Views/
│   ├── DashboardView.swift     # Main dashboard with status, quick actions, recent transfers
│   ├── DevicesView.swift       # Discovered devices list with details & capabilities
│   ├── TransferView.swift      # File transfer UI with progress, pause/resume/cancel
│   ├── StreamView.swift        # AVPlayer-based streaming with controls
│   ├── SettingsView.swift      # App settings (discovery, transfer, stream, encryption)
│   └── ClipboardSyncView.swift # Clipboard sync status & history
├── Info.plist                  # Bonjour services, background modes, permissions
└── README.md                   # This file
```

## Key Technologies

| Technology | Usage |
|---|---|
| **SwiftUI** | Full MVVM UI architecture with `ObservableObject` and `@Published` |
| **Network Framework** | `NWBrowser`/`NWListener` for Bonjour/mDNS discovery; `NWConnection` for WebSocket data transfer |
| **AVFoundation/AVKit** | `AVPlayer` for streaming audio and video content |
| **CryptoKit** | AES-256-GCM, ChaCha20-Poly1305 encryption, SHA-256 hashing, Ed25519 key agreement |
| **UIKit** | `UIPasteboard` for clipboard sync; `UIDevice` for device identity |
| **Combine** | Reactive state management throughout the app |

## Requirements

- iOS 16.0+
- Xcode 15.0+
- Swift 5.9+

## Build Instructions

### Option 1: Xcode

1. Open Xcode
2. Select **File → New → Project → iOS → App**
3. Set name to `StreamSync`, interface to **SwiftUI**, language to **Swift**
4. Replace the generated files with the files in this directory
5. Copy `Info.plist` to the project root (or ensure your Info tab has the same keys)
6. Configure signing with your Apple Developer account
7. Build & Run (⌘R)

### Option 2: Manual Project Setup

1. **Create Xcode project:**
   ```bash
   xcode-select --install  # if not installed
   mkdir -p StreamSync.xcodeproj
   ```

2. **Add source files:**
   - Drag the `StreamSync/` folder into Xcode's Project Navigator
   - Ensure all files are added to the `StreamSync` target

3. **Configure Info.plist:**
   - Ensure `Info.plist` includes:
     - `NSBonjourServices`: `_streamsync._tcp`, `_streamsync._udp`
     - `NSLocalNetworkUsageDescription`: user-facing string
     - `UIBackgroundModes`: `bonjour-service`, `audio`, `fetch`, `processing`

4. **Build settings:**
   - Deployment Target: iOS 16.0
   - Swift Language Version: Swift 5
   - Bundle Identifier: `com.streamsync.ios`

## Protocol Compatibility

Implements StreamSync Protocol v2.0 (defined in `protocol/streamsync.proto`):
- **Device Discovery**: Bonjour/mDNS with TXT record exchange
- **File Transfer**: Chunked transfer with pause/resume/cancel, progress reporting
- **Content Streaming**: AVPlayer-based playback with stream control commands
- **Clipboard Sync**: Real-time clipboard sharing via UIPasteboard polling
- **Encryption**: AES-256-GCM and ChaCha20-Poly1305 with Ed25519 key exchange
- **Screen Mirroring**: Frame reception (receive side; capture on Android/desktop)

## Key Design Decisions

1. **MVVM with ObservableObject**: Every session model is an `ObservableObject` so SwiftUI views auto-update on state changes
2. **Network Framework over URLSession WebSocket**: Lower-level control for Bonjour integration and peer-to-peer scenarios
3. **CryptoKit over CommonCrypto**: Modern Swift API with automatic secure key management
4. **Dark mode by default**: `.preferredColorScheme(.dark)` set in the app entry point
5. **iPad adaptive layout**: All views use `NavigationStack` + `List` with `.insetGrouped` style for native iPad split-view support
6. **Background modes**: Bonjour service, audio, fetch, and processing for background discovery and streaming

## AppState — Central Coordinator

`AppState` (in `StreamSyncApp.swift`) is the single `@EnvironmentObject` that wires together all services:
- Starts/stops discovery
- Manages transfer and stream lifecycles
- Toggles clipboard sync
- Holds app settings

Services communicate back via closures (`onDeviceDiscovered`, `onTransferUpdate`, `onStreamUpdate`, etc.) dispatched to `DispatchQueue.main`.

## Troubleshooting

- **No devices discovered**: Ensure both devices are on the same Wi-Fi network; check that Bonjour/mDNS is not blocked by a firewall
- **Transfer fails**: Check encryption scheme compatibility between devices; try disabling compression
- **Stream buffering**: Adjust buffer duration in Settings; verify network bandwidth
- **Clipboard not syncing**: Ensure clipboard sync is enabled on both devices; check that both devices support the `clipboard_sync` capability
