import SwiftUI

// MARK: - Devices View

struct DevicesView: View {
    @EnvironmentObject private var appState: AppState
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Discovery Control
                discoveryControl
                    .padding()
                    .background(Color(.secondarySystemGroupedBackground))

                if filteredDevices.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(filteredDevices) { device in
                            NavigationLink(destination: deviceDetailView(device)) {
                                DeviceListRowView(device: device)
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Devices")
            .searchable(text: $searchText, prompt: "Search devices...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        if appState.isDiscovering {
                            appState.stopDiscovery()
                        } else {
                            appState.startDiscovery()
                        }
                    }) {
                        Image(systemName: appState.isDiscovering
                              ? "antenna.radiowaves.left.and.right"
                              : "antenna.radiowaves.left.and.right.slash")
                            .foregroundColor(appState.isDiscovering ? .blue : .secondary)
                    }
                }
            }
        }
    }

    private var discoveryControl: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(appState.isDiscovering ? "Scanning for devices..." : "Discovery paused")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text("\(appState.discoveredDevices.count) device\(appState.discoveredDevices.count != 1 ? "s" : "") found")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            if appState.isDiscovering {
                ProgressView()
                    .scaleEffect(0.8)
            }

            Toggle("", isOn: Binding(
                get: { appState.isDiscovering },
                set: { newValue in
                    if newValue { appState.startDiscovery() }
                    else { appState.stopDiscovery() }
                }
            ))
            .labelsHidden()
            .padding(.leading, 8)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: appState.isDiscovering
                  ? "antenna.radiowaves.left.and.right"
                  : "wifi.slash")
                .font(.system(size: 60))
                .foregroundColor(.secondary)

            Text(appState.isDiscovering
                 ? "Looking for nearby devices..."
                 : "No devices found")
                .font(.title3)
                .fontWeight(.medium)

            Text(appState.isDiscovering
                 ? "Make sure StreamSync is running on your other devices"
                 : "Enable discovery to find nearby devices")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Spacer()
        }
    }

    private var filteredDevices: [DiscoveredDevice] {
        if searchText.isEmpty {
            return appState.discoveredDevices
        }
        return appState.discoveredDevices.filter { device in
            device.name.localizedCaseInsensitiveContains(searchText) ||
            device.deviceType.deviceTypeName.localizedCaseInsensitiveContains(searchText)
        }
    }

    private func deviceDetailView(_ device: DiscoveredDevice) -> some View {
        List {
            // Device Info
            Section {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: deviceTypeIcon(device))
                            .font(.system(size: 48))
                            .foregroundColor(.blue)

                        Text(device.name)
                            .font(.title2)
                            .fontWeight(.bold)

                        Text(device.deviceType.deviceTypeName)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
                .listRowBackground(Color.clear)
            }

            // Capabilities
            Section("Capabilities") {
                CapabilityRow(name: "File Transfer", available: device.supportsFileTransfer)
                CapabilityRow(name: "Content Streaming", available: device.supportsStreaming)
                CapabilityRow(name: "Clipboard Sync", available: device.supportsClipboardSync)
                CapabilityRow(name: "Screen Mirror", available: device.supportsScreenMirror)
            }

            // Details
            Section("Details") {
                DetailRow(label: "OS Version", value: device.osVersion.isEmpty ? "Unknown" : device.osVersion)
                DetailRow(label: "App Version", value: device.appVersion.isEmpty ? "Unknown" : device.appVersion)
                DetailRow(label: "Protocol", value: "v\(device.protocolVersion)")
                DetailRow(label: "Signal", value: "\(device.rssi) dBm")
            }

            // Actions
            Section("Actions") {
                if device.supportsFileTransfer {
                    Button(action: { /* open file transfer */ }) {
                        Label("Send File", systemImage: "arrow.up.doc.fill")
                    }
                }

                if device.supportsStreaming {
                    Button(action: { /* open streaming */ }) {
                        Label("Stream Content", systemImage: "play.rectangle.fill")
                    }
                }

                if device.supportsClipboardSync {
                    Button(action: { /* toggle clipboard sync */ }) {
                        Label("Sync Clipboard", systemImage: "doc.on.clipboard.fill")
                    }
                }

                Button(action: { /* send text */ }) {
                    Label("Send Text", systemImage: "text.alignleft")
                }

                Button(role: .destructive, action: { /* disconnect */ }) {
                    Label("Disconnect", systemImage: "xmark.circle")
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(device.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func deviceTypeIcon(_ device: DiscoveredDevice) -> String {
        switch device.deviceType {
        case .iOS:     return "iphone"
        case .android: return "iphone.slash"
        case .windows: return "desktopcomputer"
        case .macOS:   return "macbook"
        case .linux:   return "terminal"
        case .web:     return "globe"
        case .tv:      return "tv"
        default:       return "questionmark.circle"
        }
    }
}

// MARK: - Device List Row

struct DeviceListRowView: View {
    let device: DiscoveredDevice

    var body: some View {
        HStack(spacing: 12) {
            // Device Icon
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.blue.opacity(0.1))
                    .frame(width: 48, height: 48)

                Image(systemName: deviceTypeIcon)
                    .font(.title3)
                    .foregroundColor(.blue)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(device.name)
                    .font(.body)
                    .fontWeight(.medium)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    Text(device.deviceType.deviceTypeName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.secondary.opacity(0.1))
                        .clipShape(Capsule())

                    if device.supportsFileTransfer {
                        Image(systemName: "arrow.up.doc")
                            .font(.caption2)
                            .foregroundColor(.green)
                    }
                    if device.supportsStreaming {
                        Image(systemName: "play.rectangle")
                            .font(.caption2)
                            .foregroundColor(.red)
                    }
                    if device.supportsClipboardSync {
                        Image(systemName: "doc.on.clipboard")
                            .font(.caption2)
                            .foregroundColor(.orange)
                    }
                }
            }

            Spacer()

            // Signal indicator
            HStack(spacing: 2) {
                ForEach(0..<4) { i in
                    Image(systemName: i < device.rssiLevel ? "circle.fill" : "circle")
                        .font(.system(size: 5))
                        .foregroundColor(i < device.rssiLevel ? .green : .gray.opacity(0.3))
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var deviceTypeIcon: String {
        switch device.deviceType {
        case .iOS:     return "iphone"
        case .android: return "iphone.slash"
        case .windows: return "desktopcomputer"
        case .macOS:   return "macbook"
        case .linux:   return "terminal"
        case .web:     return "globe"
        case .tv:      return "tv"
        default:       return "questionmark.circle"
        }
    }
}

// MARK: - Supporting Views

struct CapabilityRow: View {
    let name: String
    let available: Bool

    var body: some View {
        HStack {
            Text(name)
                .foregroundColor(.primary)
            Spacer()
            Image(systemName: available ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundColor(available ? .green : .secondary)
        }
    }
}

struct DetailRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .foregroundColor(.primary)
        }
    }
}
