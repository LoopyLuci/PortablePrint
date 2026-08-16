import SwiftUI
import Network

@main
struct StreamSyncApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .preferredColorScheme(.dark)
        }
    }
}

// MARK: - Application State

final class AppState: ObservableObject {
    @Published var isDiscovering = false
    @Published var discoveredDevices: [DiscoveredDevice] = []
    @Published var activeTransfers: [TransferSession] = []
    @Published var activeStreams: [StreamSession] = []
    @Published var clipboardSyncEnabled = false
    @Published var settings = AppSettings.default

    let discoveryService = DiscoveryService()
    let transferService = TransferService()
    let streamService = StreamService()
    let cryptoService = CryptoService()
    let clipboardService = ClipboardService()

    var deviceIdentity: DeviceIdentity {
        DeviceIdentity(
            deviceID: UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString,
            deviceName: UIDevice.current.name,
            deviceType: .iOS,
            osVersion: UIDevice.current.systemVersion,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
            protocolVersion: 2,
            capabilities: [
                Capability(feature: "file_transfer", version: 1, params: ["max_size": "4294967296"]),
                Capability(feature: "streaming", version: 1, params: ["video": "true", "audio": "true"]),
                Capability(feature: "clipboard_sync", version: 1, params: [:])
            ]
        )
    }

    private var cancellables: [NSObjectProtocol] = []

    init() {
        discoveryService.onDeviceDiscovered = { [weak self] device in
            DispatchQueue.main.async {
                if !(self?.discoveredDevices.contains(where: { $0.id == device.id }) ?? false) {
                    self?.discoveredDevices.append(device)
                }
            }
        }

        discoveryService.onDeviceLost = { [weak self] device in
            DispatchQueue.main.async {
                self?.discoveredDevices.removeAll(where: { $0.id == device.id })
            }
        }

        transferService.onTransferUpdate = { [weak self] session in
            DispatchQueue.main.async {
                if let idx = self?.activeTransfers.firstIndex(where: { $0.id == session.id }) {
                    self?.activeTransfers[idx] = session
                } else {
                    self?.activeTransfers.append(session)
                }
            }
        }

        streamService.onStreamUpdate = { [weak self] session in
            DispatchQueue.main.async {
                if let idx = self?.activeStreams.firstIndex(where: { $0.id == session.id }) {
                    self?.activeStreams[idx] = session
                } else {
                    self?.activeStreams.append(session)
                }
            }
        }

        clipboardService.onClipboardReceived = { [weak self] text in
            guard let self = self, self.clipboardSyncEnabled else { return }
            DispatchQueue.main.async {
                UIPasteboard.general.string = text
            }
        }
    }

    func startDiscovery() {
        discoveryService.startDiscovery(serviceType: "_streamsync._tcp")
        isDiscovering = true
    }

    func stopDiscovery() {
        discoveryService.stopDiscovery()
        isDiscovering = false
    }

    func startClipboardSync() {
        clipboardSyncEnabled = true
        clipboardService.startMonitoring()
    }

    func stopClipboardSync() {
        clipboardSyncEnabled = false
        clipboardService.stopMonitoring()
    }
}

// MARK: - App Settings

struct AppSettings {
    var deviceName: String
    var maxTransferChunkSize: UInt32 = 65536
    var encryptionScheme: EncryptionScheme = .aes256GCM
    var compressionType: CompressionType = .gzip
    var screenMirrorQuality: UInt32 = 80
    var autoAcceptTransfers: Bool = false
    var autoAcceptPairing: Bool = false
    var streamBufferSeconds: Double = 10.0
    var maxDownloadBandwidth: Double = 0 // 0 = unlimited

    static let `default` = AppSettings(
        deviceName: UIDevice.current.name
    )
}

// MARK: - Protocol Enums (mirrored from proto)

enum EncryptionScheme: String, Codable, CaseIterable {
    case none = "ENCRYPTION_NONE"
    case aes256GCM = "ENCRYPTION_AES_256_GCM"
    case chacha20Poly1305 = "ENCRYPTION_CHACHA20_POLY1305"
    case tls13 = "ENCRYPTION_TLS_1_3"
}

enum CompressionType: String, Codable, CaseIterable {
    case none = "COMPRESSION_NONE"
    case gzip = "COMPRESSION_GZIP"
    case zstd = "COMPRESSION_ZSTD"
    case lz4 = "COMPRESSION_LZ4"
}

enum TransferType: String, Codable, CaseIterable {
    case file = "TRANSFER_FILE"
    case stream = "TRANSFER_STREAM"
    case clipboard = "TRANSFER_CLIPBOARD"
    case url = "TRANSFER_URL"
    case text = "TRANSFER_TEXT"
    case contact = "TRANSFER_CONTACT"
    case screenMirror = "TRANSFER_SCREEN_MIRROR"
}

enum TransferStatus: String, Codable {
    case pending = "STATUS_PENDING"
    case transferring = "STATUS_TRANSFERRING"
    case paused = "STATUS_PAUSED"
    case completed = "STATUS_COMPLETED"
    case failed = "STATUS_FAILED"
    case cancelled = "STATUS_CANCELLED"
}

enum StreamType: String, Codable {
    case video = "STREAM_VIDEO"
    case audio = "STREAM_AUDIO"
    case screen = "STREAM_SCREEN"
    case camera = "STREAM_CAMERA"
    case microphone = "STREAM_MICROPHONE"
    case file = "STREAM_FILE"
}

enum DeviceType: String, Codable {
    case unknown = "DEVICE_UNKNOWN"
    case android = "DEVICE_ANDROID"
    case iOS = "DEVICE_IOS"
    case windows = "DEVICE_DESKTOP_WINDOWS"
    case macOS = "DEVICE_DESKTOP_MACOS"
    case linux = "DEVICE_DESKTOP_LINUX"
    case web = "DEVICE_WEB"
    case tv = "DEVICE_TV"
}
