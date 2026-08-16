import Foundation

// MARK: - Device Identity

struct DeviceIdentity: Identifiable, Codable, Equatable {
    let deviceID: String
    var deviceName: String
    var deviceType: DeviceType
    var osVersion: String
    var appVersion: String
    var protocolVersion: UInt32
    var capabilities: [Capability]
    var publicKey: Data?

    var id: String { deviceID }

    init(
        deviceID: String,
        deviceName: String,
        deviceType: DeviceType,
        osVersion: String,
        appVersion: String,
        protocolVersion: UInt32,
        capabilities: [Capability] = [],
        publicKey: Data? = nil
    ) {
        self.deviceID = deviceID
        self.deviceName = deviceName
        self.deviceType = deviceType
        self.osVersion = osVersion
        self.appVersion = appVersion
        self.protocolVersion = protocolVersion
        self.capabilities = capabilities
        self.publicKey = publicKey
    }

    var deviceTypeIcon: String {
        switch deviceType {
        case .android:      return "iphone.slash"
        case .iOS:          return "iphone"
        case .windows:      return "desktopcomputer"
        case .macOS:        return "macbook"
        case .linux:        return "terminal"
        case .web:          return "globe"
        case .tv:           return "tv"
        case .unknown:      return "questionmark.circle"
        }
    }

    var deviceTypeName: String {
        switch deviceType {
        case .android:      return "Android"
        case .iOS:          return "iPhone/iPad"
        case .windows:      return "Windows"
        case .macOS:        return "Mac"
        case .linux:        return "Linux"
        case .web:          return "Web Browser"
        case .tv:           return "TV"
        case .unknown:      return "Unknown"
        }
    }

    func supportsFeature(_ feature: String) -> Bool {
        capabilities.contains(where: { $0.feature == feature })
    }
}

// MARK: - Capability

struct Capability: Codable, Equatable {
    let feature: String
    let version: UInt32
    let params: [String: String]

    init(feature: String, version: UInt32, params: [String: String] = [:]) {
        self.feature = feature
        self.version = version
        self.params = params
    }
}

// MARK: - Connection Info

struct ConnectionInfo: Codable {
    let ipAddress: String
    let port: UInt16
    let connectionType: ConnectionType
    let rssi: Int32
    let bandwidthEstimateMbps: UInt32

    enum ConnectionType: String, Codable {
        case wifi = "WIFI"
        case cellular = "CELLULAR"
        case ethernet = "ETHERNET"
        case bluetooth = "BLUETOOTH"
        case usb = "USB"
        case hotspot = "HOTSPOT"
    }
}

// MARK: - Device Info (from DeviceListResponse)

struct DeviceInfo: Identifiable, Codable {
    let deviceID: String
    let deviceName: String
    let deviceType: DeviceType
    let isActive: Bool
    let signalStrength: UInt32
    let connection: ConnectionInfo?

    var id: String { deviceID }
}
