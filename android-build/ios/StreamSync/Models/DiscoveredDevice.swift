import Foundation
import Network

// MARK: - Discovered Device

struct DiscoveredDevice: Identifiable, Codable, Equatable {
    let id: String
    let name: String
    let deviceType: DeviceType
    let host: String
    let port: UInt16
    let txtRecord: [String: String]
    let osVersion: String
    let appVersion: String
    let protocolVersion: UInt32
    let capabilities: [String]
    let rssi: Int32
    let lastSeen: Date

    init(
        id: String,
        name: String,
        deviceType: DeviceType,
        host: String,
        port: UInt16,
        txtRecord: [String: String] = [:],
        osVersion: String = "",
        appVersion: String = "",
        protocolVersion: UInt32 = 2,
        capabilities: [String] = [],
        rssi: Int32 = 0,
        lastSeen: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.deviceType = deviceType
        self.host = host
        self.port = port
        self.txtRecord = txtRecord
        self.osVersion = osVersion
        self.appVersion = appVersion
        self.protocolVersion = protocolVersion
        self.capabilities = capabilities
        self.rssi = rssi
        self.lastSeen = lastSeen
    }

    static func == (lhs: DiscoveredDevice, rhs: DiscoveredDevice) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    var rssiLevel: Int {
        if rssi >= -50 { return 4 }
        if rssi >= -60 { return 3 }
        if rssi >= -70 { return 2 }
        if rssi >= -80 { return 1 }
        return 0
    }

    var rssiIcon: String {
        switch rssiLevel {
        case 4: return "wifi"
        case 3: return "wifi"
        case 2: return "wifi"
        case 1: return "wifi.slash"
        default: return "wifi.slash"
        }
    }

    var isIOS: Bool { deviceType == .iOS }
    var isAndroid: Bool { deviceType == .android }
    var isDesktop: Bool {
        deviceType == .windows || deviceType == .macOS || deviceType == .linux
    }

    var supportsFileTransfer: Bool {
        capabilities.contains("file_transfer")
    }

    var supportsStreaming: Bool {
        capabilities.contains("streaming")
    }

    var supportsClipboardSync: Bool {
        capabilities.contains("clipboard_sync")
    }

    var supportsScreenMirror: Bool {
        capabilities.contains("screen_mirror")
    }
}
