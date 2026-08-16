import Foundation

// MARK: - File Info

struct FileInfo: Identifiable, Codable {
    let fileID: String
    var filename: String
    var fileSize: UInt64
    var mimeType: String
    var fileHash: Data?
    var relativePath: String?
    var metadata: [String: String]

    var id: String { fileID }

    init(
        fileID: String = UUID().uuidString,
        filename: String,
        fileSize: UInt64,
        mimeType: String = "application/octet-stream",
        fileHash: Data? = nil,
        relativePath: String? = nil,
        metadata: [String: String] = [:]
    ) {
        self.fileID = fileID
        self.filename = filename
        self.fileSize = fileSize
        self.mimeType = mimeType
        self.fileHash = fileHash
        self.relativePath = relativePath
        self.metadata = metadata
    }

    var formattedSize: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(fileSize))
    }

    var fileExtension: String {
        (filename as NSString).pathExtension
    }

    var fileIcon: String {
        switch mimeType {
        case let mime where mime.hasPrefix("image/"):
            return "photo"
        case let mime where mime.hasPrefix("video/"):
            return "video"
        case let mime where mime.hasPrefix("audio/"):
            return "music.note"
        case let mime where mime.hasPrefix("text/"):
            return "doc.text"
        case "application/pdf":
            return "doc.richtext"
        case let mime where mime.contains("zip") || mime.contains("compress"):
            return "archivebox"
        default:
            return "doc"
        }
    }
}

// MARK: - Transfer Session

final class TransferSession: Identifiable, ObservableObject {
    let id: String
    let transferType: TransferType
    let senderID: String
    let targetID: String
    let encryptionScheme: EncryptionScheme
    let compressionType: CompressionType

    @Published var files: [FileInfo]
    @Published var status: TransferStatus
    @Published var bytesTransferred: UInt64
    @Published var totalBytes: UInt64
    @Published var progressPercent: UInt32
    @Published var speedMbps: Double
    @Published var estimatedRemainingMs: UInt64
    @Published var errorMessage: String?
    @Published var textPayload: String?
    @Published var urlPayload: String?
    @Published var streamType: StreamType?

    let createdAt: Date
    var completedAt: Date?
    var elapsedMs: UInt64 {
        guard let completedAt = completedAt else {
            return UInt64(Date().timeIntervalSince(createdAt) * 1000)
        }
        return UInt64(completedAt.timeIntervalSince(createdAt) * 1000)
    }

    var formattedSpeed: String {
        if speedMbps >= 1.0 {
            return String(format: "%.1f MB/s", speedMbps)
        } else {
            return String(format: "%.0f KB/s", speedMbps * 1024)
        }
    }

    var formattedProgress: String {
        if totalBytes > 0 {
            let percent = Double(bytesTransferred) / Double(totalBytes) * 100
            return String(format: "%.1f%%", percent)
        }
        return "0%"
    }

    var formattedTransferred: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(bytesTransferred))
    }

    var formattedTotal: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(totalBytes))
    }

    var isActive: Bool {
        status == .transferring || status == .pending
    }

    var isFinished: Bool {
        status == .completed || status == .failed || status == .cancelled
    }

    init(
        id: String = UUID().uuidString,
        transferType: TransferType,
        senderID: String,
        targetID: String,
        encryptionScheme: EncryptionScheme = .aes256GCM,
        compressionType: CompressionType = .gzip,
        files: [FileInfo] = [],
        status: TransferStatus = .pending,
        bytesTransferred: UInt64 = 0,
        totalBytes: UInt64 = 0,
        progressPercent: UInt32 = 0,
        speedMbps: Double = 0,
        estimatedRemainingMs: UInt64 = 0,
        errorMessage: String? = nil,
        textPayload: String? = nil,
        urlPayload: String? = nil,
        streamType: StreamType? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.transferType = transferType
        self.senderID = senderID
        self.targetID = targetID
        self.encryptionScheme = encryptionScheme
        self.compressionType = compressionType
        self.files = files
        self.status = status
        self.bytesTransferred = bytesTransferred
        self.totalBytes = totalBytes
        self.progressPercent = progressPercent
        self.speedMbps = speedMbps
        self.estimatedRemainingMs = estimatedRemainingMs
        self.errorMessage = errorMessage
        self.textPayload = textPayload
        self.urlPayload = urlPayload
        self.streamType = streamType
        self.createdAt = createdAt
    }
}
