import SwiftUI

// MARK: - Settings View

struct SettingsView: View {
    @EnvironmentObject private var appState: AppState
    @State private var settings = AppSettings.default
    @State private var showingResetConfirmation = false

    var body: some View {
        NavigationStack {
            Form {
                // Device Section
                deviceSection

                // Discovery Section
                discoverySection

                // Transfer Section
                transferSection

                // Streaming Section
                streamingSection

                // Clipboard Section
                clipboardSection

                // Encryption Section
                encryptionSection

                // About Section
                aboutSection
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Reset") {
                        showingResetConfirmation = true
                    }
                    .foregroundColor(.red)
                }
            }
            .alert("Reset Settings", isPresented: $showingResetConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Reset", role: .destructive) {
                    appState.settings = AppSettings.default
                    settings = AppSettings.default
                }
            } message: {
                Text("This will reset all settings to their default values.")
            }
        }
    }

    // MARK: - Device Section

    private var deviceSection: some View {
        Section {
            HStack {
                Label("Device Name", systemImage: "iphone")
                Spacer()
                TextField("Name", text: $settings.deviceName)
                    .multilineTextAlignment(.trailing)
                    .onSubmit {
                        // Update device identity
                    }
            }

            HStack {
                Label("Model", systemImage: "hardware")
                Spacer()
                Text(UIDevice.current.model)
                    .foregroundColor(.secondary)
            }

            HStack {
                Label("iOS Version", systemImage: "gear")
                Spacer()
                Text(UIDevice.current.systemVersion)
                    .foregroundColor(.secondary)
            }

            HStack {
                Label("App Version", systemImage: "apps.iphone")
                Spacer()
                Text(appState.deviceIdentity.appVersion)
                    .foregroundColor(.secondary)
            }
        } header: {
            Label("Device", systemImage: "iphone")
        }
    }

    // MARK: - Discovery Section

    private var discoverySection: some View {
        Section {
            HStack {
                Label("Auto-discover", systemImage: "antenna.radiowaves.left.and.right")
                Spacer()
                Toggle("", isOn: Binding(
                    get: { appState.isDiscovering },
                    set: { newValue in
                        if newValue { appState.startDiscovery() }
                        else { appState.stopDiscovery() }
                    }
                ))
            }

            HStack {
                Label("Auto-accept pairing", systemImage: "person.badge.plus")
                Spacer()
                Toggle("", isOn: $settings.autoAcceptPairing)
            }

            HStack {
                Label("Auto-accept transfers", systemImage: "arrow.down.doc")
                Spacer()
                Toggle("", isOn: $settings.autoAcceptTransfers)
            }
        } header: {
            Label("Discovery", systemImage: "antenna.radiowaves.left.and.right")
        } footer: {
            Text("StreamSync uses Bonjour/mDNS to discover nearby devices on the same network.")
        }
    }

    // MARK: - Transfer Section

    private var transferSection: some View {
        Section {
            HStack {
                Label("Max chunk size", systemImage: "rectangle.split.2x2")
                Spacer()
                Text("\(settings.maxTransferChunkSize / 1024) KB")
                    .foregroundColor(.secondary)
            }

            Picker(selection: $settings.compressionType) {
                ForEach(CompressionType.allCases, id: \.self) { type in
                    Text(compressionName(type)).tag(type)
                }
            } label: {
                Label("Compression", systemImage: "rectangle.compress.vertical")
            }

            HStack {
                Label("Bandwidth limit", systemImage: "speedometer")
                Spacer()
                if settings.maxDownloadBandwidth == 0 {
                    Text("Unlimited")
                        .foregroundColor(.secondary)
                } else {
                    Text(String(format: "%.1f MB/s", settings.maxDownloadBandwidth))
                        .foregroundColor(.secondary)
                }
            }
        } header: {
            Label("Transfer", systemImage: "arrow.up.arrow.down")
        } footer: {
            Text("Larger chunk sizes improve speed but use more memory.")
        }
    }

    // MARK: - Streaming Section

    private var streamingSection: some View {
        Section {
            HStack {
                Label("Buffer duration", systemImage: "hourglass")
                Spacer()
                Text("\(Int(settings.streamBufferSeconds))s")
                    .foregroundColor(.secondary)
            }

            HStack {
                Label("Preferred resolution", systemImage: "rectangle.fill.on.rectangle.fill")
                Spacer()
                Text("Auto")
                    .foregroundColor(.secondary)
            }

            HStack {
                Label("Stream quality", systemImage: "sparkles")
                Spacer()
                Text("High")
                    .foregroundColor(.secondary)
            }
        } header: {
            Label("Streaming", systemImage: "play.rectangle")
        }
    }

    // MARK: - Clipboard Section

    private var clipboardSection: some View {
        Section {
            HStack {
                Label("Enable clipboard sync", systemImage: "doc.on.clipboard")
                Spacer()
                Toggle("", isOn: Binding(
                    get: { appState.clipboardSyncEnabled },
                    set: { newValue in
                        if newValue { appState.startClipboardSync() }
                        else { appState.stopClipboardSync() }
                    }
                ))
            }

            if appState.clipboardSyncEnabled {
                HStack {
                    Label("Current clipboard", systemImage: "text.alignleft")
                    Spacer()
                    Text(appState.clipboardService.currentClipboardText ?? "Empty")
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .frame(maxWidth: 150, alignment: .trailing)
                }
            }
        } header: {
            Label("Clipboard", systemImage: "doc.on.clipboard")
        } footer: {
            Text("When enabled, clipboard content is shared with connected devices in real-time.")
        }
    }

    // MARK: - Encryption Section

    private var encryptionSection: some View {
        Section {
            Picker(selection: $settings.encryptionScheme) {
                ForEach(EncryptionScheme.allCases, id: \.self) { scheme in
                    Text(encryptionName(scheme)).tag(scheme)
                }
            } label: {
                Label("Encryption", systemImage: "lock.shield")
            }
        } header: {
            Label("Security", systemImage: "lock.shield")
        } footer: {
            Text("All transfers are encrypted end-to-end. AES-256-GCM is recommended for maximum compatibility.")
        }
    }

    // MARK: - About Section

    private var aboutSection: some View {
        Section {
            HStack {
                Label("Version", systemImage: "info.circle")
                Spacer()
                Text(appState.deviceIdentity.appVersion)
                    .foregroundColor(.secondary)
            }

            HStack {
                Label("Protocol Version", systemImage: "network")
                Spacer()
                Text("v\(appState.deviceIdentity.protocolVersion)")
                    .foregroundColor(.secondary)
            }

            Link(destination: URL(string: "https://streamsync.app/privacy")!) {
                Label("Privacy Policy", systemImage: "hand.raised")
            }

            Link(destination: URL(string: "https://streamsync.app/terms")!) {
                Label("Terms of Service", systemImage: "doc.text")
            }
        } header: {
            Label("About", systemImage: "info.circle")
        } footer: {
            Text("StreamSync v2.0 — Peer-to-peer file transfer & content streaming")
        }
    }

    // MARK: - Helpers

    private func compressionName(_ type: CompressionType) -> String {
        switch type {
        case .none:    return "None"
        case .gzip:    return "GZip"
        case .zstd:    return "Zstandard"
        case .lz4:     return "LZ4"
        }
    }

    private func encryptionName(_ scheme: EncryptionScheme) -> String {
        switch scheme {
        case .none:              return "None"
        case .aes256GCM:        return "AES-256-GCM"
        case .chacha20Poly1305: return "ChaCha20-Poly1305"
        case .tls13:            return "TLS 1.3"
        }
    }
}
