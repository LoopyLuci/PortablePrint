import SwiftUI
import UniformTypeIdentifiers

// MARK: - Transfer View

struct TransferView: View {
    @EnvironmentObject private var appState: AppState
    @State private var showingFilePicker = false
    @State private var selectedFilter: TransferFilter = .all

    enum TransferFilter: String, CaseIterable {
        case all = "All"
        case active = "Active"
        case completed = "Completed"
        case failed = "Failed"
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Filter Picker
                Picker("Filter", selection: $selectedFilter) {
                    ForEach(TransferFilter.allCases, id: \.self) { filter in
                        Text(filter.rawValue).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                if filteredTransfers.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(filteredTransfers) { session in
                            TransferSessionView(session: session)
                                .swipeActions(edge: .trailing) {
                                    if session.isActive {
                                        Button(role: .destructive) {
                                            appState.transferService.cancelTransfer(sessionID: session.id)
                                        } label: {
                                            Label("Cancel", systemImage: "xmark")
                                        }
                                    }
                                }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Transfers")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingFilePicker = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .fileImporter(
                isPresented: $showingFilePicker,
                allowedContentTypes: [.data],
                allowsMultipleSelection: true
            ) { result in
                handleFilePickerResult(result)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "arrow.up.arrow.down")
                .font(.system(size: 60))
                .foregroundColor(.secondary)

            Text(emptyStateTitle)
                .font(.title3)
                .fontWeight(.medium)

            Text(emptyStateDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            if appState.discoveredDevices.isEmpty {
                Button(action: { appState.startDiscovery() }) {
                    Label("Start Discovery", systemImage: "antenna.radiowaves.left.and.right")
                }
                .buttonStyle(.borderedProminent)
            }

            Spacer()
        }
    }

    private var emptyStateTitle: String {
        switch selectedFilter {
        case .all: return "No transfers yet"
        case .active: return "No active transfers"
        case .completed: return "No completed transfers"
        case .failed: return "No failed transfers"
        }
    }

    private var emptyStateDescription: String {
        switch selectedFilter {
        case .all: return "Select a file to send to a nearby device"
        case .active: return "Start a file transfer from a device"
        case .completed: return "Completed transfers will appear here"
        case .failed: return "No transfers have failed"
        }
    }

    private var filteredTransfers: [TransferSession] {
        switch selectedFilter {
        case .all:
            return appState.activeTransfers
        case .active:
            return appState.activeTransfers.filter { $0.isActive }
        case .completed:
            return appState.activeTransfers.filter { $0.status == .completed }
        case .failed:
            return appState.activeTransfers.filter { $0.status == .failed || $0.status == .cancelled }
        }
    }

    private func handleFilePickerResult(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            for url in urls {
                guard url.startAccessingSecurityScopedResource() else { continue }
                defer { url.stopAccessingSecurityScopedResource() }

                do {
                    let resourceValues = try url.resourceValues(forKeys: [.fileSizeKey, .contentTypeKey])
                    let fileSize = UInt64(resourceValues.fileSize ?? 0)
                    let mimeType = resourceValues.contentType?.preferredMIMEType ?? "application/octet-stream"

                    let fileInfo = FileInfo(
                        fileID: UUID().uuidString,
                        filename: url.lastPathComponent,
                        fileSize: fileSize,
                        mimeType: mimeType
                    )

                    if let targetDevice = appState.discoveredDevices.first {
                        let session = TransferSession(
                            transferType: .file,
                            senderID: appState.deviceIdentity.deviceID,
                            targetID: targetDevice.id,
                            files: [fileInfo],
                            totalBytes: fileSize
                        )

                        appState.transferService.connectToPeer(
                            host: targetDevice.host,
                            port: targetDevice.port,
                            session: session
                        )
                    }
                } catch {
                    print("Failed to read file: \(error)")
                }
            }
        case .failure(let error):
            print("File picker error: \(error)")
        }
    }
}

// MARK: - Transfer Session View

struct TransferSessionView: View {
    @ObservedObject var session: TransferSession

    var body: some View {
        VStack(spacing: 10) {
            // Header
            HStack {
                Image(systemName: session.files.first?.fileIcon ?? "doc")
                    .font(.title3)
                    .foregroundColor(statusColor)
                    .frame(width: 36, height: 36)
                    .background(statusColor.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                VStack(alignment: .leading, spacing: 2) {
                    Text(session.files.first?.filename ?? "Unknown file")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .lineLimit(1)

                    if session.isActive {
                        Text(session.formattedTransferred + " of " + session.formattedTotal)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else if session.status == .completed {
                        Text("Completed • \(session.formattedSpeed) avg")
                            .font(.caption)
                            .foregroundColor(.green)
                    } else if let error = session.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                }

                Spacer()

                // Status badge
                statusBadge
            }

            // Progress Bar (for active/completed)
            if session.isActive || session.status == .completed {
                ProgressView(value: Double(session.progressPercent) / 100.0)
                    .tint(statusColor)
            }

            // Speed & Controls (for active)
            if session.isActive {
                HStack {
                    Text(session.formattedSpeed)
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Spacer()

                    if session.status == .transferring {
                        if session.progressPercent > 0 && session.progressPercent < 100 {
                            Text("\(session.progressPercent)%")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.secondary)
                        }
                    }

                    // Control buttons
                    HStack(spacing: 12) {
                        if session.status == .transferring {
                            Button(action: { /* pause */ }) {
                                Image(systemName: "pause.fill")
                                    .font(.caption)
                            }
                        } else if session.status == .paused {
                            Button(action: { /* resume */ }) {
                                Image(systemName: "play.fill")
                                    .font(.caption)
                            }
                        }

                        Button(role: .destructive, action: { /* cancel */ }) {
                            Image(systemName: "xmark")
                                .font(.caption)
                        }
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var statusColor: Color {
        switch session.status {
        case .pending:      return .orange
        case .transferring: return .blue
        case .paused:       return .yellow
        case .completed:    return .green
        case .failed:       return .red
        case .cancelled:    return .gray
        }
    }

    private var statusBadge: some View {
        Text(statusText)
            .font(.caption2)
            .fontWeight(.medium)
            .foregroundColor(statusColor)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(statusColor.opacity(0.1))
            .clipShape(Capsule())
    }

    private var statusText: String {
        switch session.status {
        case .pending:      return "Pending"
        case .transferring: return "Transferring"
        case .paused:       return "Paused"
        case .completed:    return "Done"
        case .failed:       return "Failed"
        case .cancelled:    return "Cancelled"
        }
    }
}
