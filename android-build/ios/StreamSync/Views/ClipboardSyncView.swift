import SwiftUI

// MARK: - Clipboard Sync View

struct ClipboardSyncView: View {
    @EnvironmentObject private var appState: AppState
    @State private var clipboardHistory: [ClipboardEntry] = []
    @State private var showingClearConfirmation = false

    struct ClipboardEntry: Identifiable {
        let id = UUID()
        let text: String
        let timestamp: Date
        let sourceDevice: String

        var formattedTime: String {
            let formatter = RelativeDateTimeFormatter()
            formatter.unitsStyle = .abbreviated
            return formatter.localizedString(for: timestamp, relativeTo: Date())
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Status Header
                statusHeader
                    .padding()
                    .background(Color(.secondarySystemGroupedBackground))

                if clipboardHistory.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(clipboardHistory) { entry in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.text)
                                    .font(.body)
                                    .lineLimit(3)
                                    .textSelection(.enabled)

                                HStack {
                                    Text("from \(entry.sourceDevice)")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Text(entry.formattedTime)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .onDelete { indexSet in
                            clipboardHistory.remove(atOffsets: indexSet)
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Clipboard Sync")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !clipboardHistory.isEmpty {
                        Button("Clear") {
                            showingClearConfirmation = true
                        }
                    }
                }
            }
            .alert("Clear History", isPresented: $showingClearConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Clear", role: .destructive) {
                    clipboardHistory.removeAll()
                }
            } message: {
                Text("This will clear all clipboard history entries.")
            }
            .onAppear {
                // Set up clipboard listener
                appState.clipboardService.onClipboardReceived = { text in
                    let entry = ClipboardEntry(
                        text: text,
                        timestamp: Date(),
                        sourceDevice: "Remote Device"
                    )
                    clipboardHistory.insert(entry, at: 0)

                    // Keep last 50 entries
                    if clipboardHistory.count > 50 {
                        clipboardHistory = Array(clipboardHistory.prefix(50))
                    }
                }
            }
        }
    }

    private var statusHeader: some View {
        HStack {
            Image(systemName: appState.clipboardSyncEnabled
                  ? "doc.on.clipboard.fill"
                  : "doc.on.clipboard")
                .font(.title2)
                .foregroundColor(appState.clipboardSyncEnabled ? .green : .secondary)

            VStack(alignment: .leading, spacing: 2) {
                Text(appState.clipboardSyncEnabled ? "Syncing" : "Disabled")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(appState.clipboardSyncEnabled
                     ? "Clipboard shared with connected devices"
                     : "Enable clipboard sync to share across devices")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Toggle("", isOn: Binding(
                get: { appState.clipboardSyncEnabled },
                set: { newValue in
                    if newValue { appState.startClipboardSync() }
                    else { appState.stopClipboardSync() }
                }
            ))
            .labelsHidden()
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "doc.on.clipboard")
                .font(.system(size: 60))
                .foregroundColor(.secondary)

            Text(appState.clipboardSyncEnabled
                 ? "No clipboard activity yet"
                 : "Clipboard sync is disabled")
                .font(.title3)
                .fontWeight(.medium)

            Text(appState.clipboardSyncEnabled
                 ? "Clipboard entries from connected devices will appear here"
                 : "Enable clipboard sync in Settings or the Dashboard to start sharing")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            if !appState.clipboardSyncEnabled {
                Button(action: { appState.startClipboardSync() }) {
                    Label("Enable Clipboard Sync", systemImage: "doc.on.clipboard.fill")
                }
                .buttonStyle(.borderedProminent)
            }

            Spacer()
        }
    }
}
