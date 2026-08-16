import SwiftUI

// MARK: - Dashboard View

struct DashboardView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Status Header
                    statusHeader
                        .padding(.horizontal)

                    // Quick Actions
                    quickActionsGrid
                        .padding(.horizontal)

                    // Active Transfers Summary
                    if !appState.activeTransfers.isEmpty {
                        activeTransfersSection
                            .padding(.horizontal)
                    }

                    // Recent Devices
                    if !appState.discoveredDevices.isEmpty {
                        recentDevicesSection
                            .padding(.horizontal)
                    }

                    // Clipboard Sync Status
                    clipboardSyncCard
                        .padding(.horizontal)
                }
                .padding(.vertical)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("StreamSync")
            .onAppear {
                if !appState.isDiscovering {
                    appState.startDiscovery()
                }
            }
        }
    }

    // MARK: - Status Header

    private var statusHeader: some View {
        VStack(spacing: 8) {
            HStack {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.title2)
                    .foregroundColor(appState.isDiscovering ? .green : .secondary)

                Text(appState.isDiscovering ? "Discovering devices..." : "Discovery paused")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Spacer()

                if appState.isDiscovering {
                    ProgressView()
                        .scaleEffect(0.8)
                }
            }

            HStack {
                Label("\(appState.discoveredDevices.count) device\(appState.discoveredDevices.count != 1 ? "s" : "") nearby",
                      systemImage: "eye")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Spacer()

                if appState.activeTransfers.filter({ $0.isActive }).count > 0 {
                    Label("\(appState.activeTransfers.filter({ $0.isActive }).count) active transfer\(appState.activeTransfers.filter({ $0.isActive }).count != 1 ? "s" : "")",
                          systemImage: "arrow.up.arrow.down")
                        .font(.caption)
                        .foregroundColor(.blue)
                }
            }
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Quick Actions

    private var quickActionsGrid: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Quick Actions", systemImage: "bolt.fill")
                .font(.headline)
                .foregroundColor(.primary)

            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                quickActionButton(
                    icon: "arrow.up.doc.fill",
                    title: "Send File",
                    color: .blue,
                    action: { /* open file picker */ }
                )

                quickActionButton(
                    icon: "play.rectangle.fill",
                    title: "Stream",
                    color: .red,
                    action: { /* open stream */ }
                )

                quickActionButton(
                    icon: "doc.on.clipboard.fill",
                    title: "Clipboard",
                    color: .green,
                    action: { /* toggle clipboard sync */ }
                )

                quickActionButton(
                    icon: "display",
                    title: "Mirror",
                    color: .orange,
                    action: { /* start screen mirror */ }
                )
            }
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func quickActionButton(icon: String, title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(color)
                    .frame(width: 44, height: 44)
                    .background(color.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                Text(title)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
            }
        }
    }

    // MARK: - Active Transfers

    private var activeTransfersSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Active Transfers", systemImage: "arrow.up.arrow.down")
                    .font(.headline)
                Spacer()
                NavigationLink("See All", destination: TransferView())
                    .font(.caption)
            }

            ForEach(appState.activeTransfers.filter { $0.isActive }.prefix(3)) { session in
                TransferRowView(session: session)
            }
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Recent Devices

    private var recentDevicesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Nearby Devices", systemImage: "antenna.radiowaves.left.and.right")
                    .font(.headline)
                Spacer()
                NavigationLink("See All", destination: DevicesView())
                    .font(.caption)
            }

            ForEach(appState.discoveredDevices.prefix(5)) { device in
                DeviceRowView(device: device)
            }
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Clipboard Sync

    private var clipboardSyncCard: some View {
        HStack {
            Image(systemName: appState.clipboardSyncEnabled
                  ? "doc.on.clipboard.fill"
                  : "doc.on.clipboard")
                .font(.title2)
                .foregroundColor(appState.clipboardSyncEnabled ? .green : .secondary)

            VStack(alignment: .leading, spacing: 2) {
                Text("Clipboard Sync")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(appState.clipboardSyncEnabled
                     ? "Syncing clipboard across devices"
                     : "Tap to enable clipboard sharing")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Toggle("", isOn: Binding(
                get: { appState.clipboardSyncEnabled },
                set: { newValue in
                    if newValue {
                        appState.startClipboardSync()
                    } else {
                        appState.stopClipboardSync()
                    }
                }
            ))
            .labelsHidden()
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - Device Row View

struct DeviceRowView: View {
    let device: DiscoveredDevice

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: deviceTypeIcon)
                .font(.title3)
                .foregroundColor(.blue)
                .frame(width: 36, height: 36)
                .background(Color.blue.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 2) {
                Text(device.name)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)

                HStack(spacing: 4) {
                    Text(device.deviceType.deviceTypeName)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    if !device.osVersion.isEmpty {
                        Text("•")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Text(device.osVersion)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Spacer()

            HStack(spacing: 2) {
                ForEach(0..<4) { i in
                    Image(systemName: i < device.rssiLevel ? "circle.fill" : "circle")
                        .font(.system(size: 6))
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

// MARK: - Transfer Row View

struct TransferRowView: View {
    @ObservedObject var session: TransferSession

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Image(systemName: session.files.first?.fileIcon ?? "doc")
                    .foregroundColor(.blue)

                VStack(alignment: .leading, spacing: 2) {
                    Text(session.files.first?.filename ?? "Transfer")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .lineLimit(1)

                    Text(session.formattedTransferred + " / " + session.formattedTotal)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Text(session.formattedSpeed)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            ProgressView(value: Double(session.progressPercent) / 100.0)
                .tint(session.status == .failed ? .red : .blue)
        }
        .padding(.vertical, 4)
    }
}
