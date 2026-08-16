import Foundation
import AVFoundation

// MARK: - Stream Session

final class StreamSession: Identifiable, ObservableObject {
    let id: String
    let streamType: StreamType
    let sessionID: String
    let senderID: String
    let streamURL: URL?

    @Published var isPlaying: Bool = false
    @Published var isPaused: Bool = false
    @Published var volume: Float = 1.0
    @Published var isMuted: Bool = false
    @Published var currentTime: CMTime = .zero
    @Published var duration: CMTime = .zero
    @Published var bitrateKbps: UInt32 = 0
    @Published var resolution: (width: UInt32, height: UInt32) = (0, 0)
    @Published var fps: UInt32 = 0
    @Published var codec: String = ""
    @Published var hasAudio: Bool = true
    @Published var connectionQuality: ConnectionQuality = .unknown

    enum ConnectionQuality: String, Codable {
        case unknown = "unknown"
        case poor = "poor"
        case fair = "fair"
        case good = "good"
        case excellent = "excellent"

        var icon: String {
            switch self {
            case .unknown:   return "questionmark.circle"
            case .poor:      return "antenna.radiowaves.left.and.right.slash"
            case .fair:      return "antenna.radiowaves.left.and.right"
            case .good:      return "wifi"
            case .excellent: return "wifi"
            }
        }
    }

    let startedAt: Date

    var formattedDuration: String {
        let totalSeconds = CMTimeGetSeconds(duration)
        guard totalSeconds.isFinite, totalSeconds > 0 else { return "00:00" }
        let hours = Int(totalSeconds) / 3600
        let minutes = (Int(totalSeconds) % 3600) / 60
        let seconds = Int(totalSeconds) % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%02d:%02d", minutes, seconds)
    }

    var formattedCurrentTime: String {
        let totalSeconds = CMTimeGetSeconds(currentTime)
        guard totalSeconds.isFinite, totalSeconds >= 0 else { return "00:00" }
        let hours = Int(totalSeconds) / 3600
        let minutes = (Int(totalSeconds) % 3600) / 60
        let seconds = Int(totalSeconds) % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%02d:%02d", minutes, seconds)
    }

    var progress: Double {
        let dur = CMTimeGetSeconds(duration)
        let cur = CMTimeGetSeconds(currentTime)
        guard dur.isFinite, dur > 0, cur.isFinite else { return 0 }
        return cur / dur
    }

    var resolutionString: String {
        guard resolution.width > 0, resolution.height > 0 else { return "N/A" }
        return "\(resolution.width)×\(resolution.height)"
    }

    var streamTypeIcon: String {
        switch streamType {
        case .video:      return "film"
        case .audio:      return "music.note"
        case .screen:     return "display"
        case .camera:     return "camera"
        case .microphone: return "mic"
        case .file:       return "doc"
        }
    }

    init(
        id: String = UUID().uuidString,
        streamType: StreamType,
        sessionID: String,
        senderID: String,
        streamURL: URL? = nil,
        bitrateKbps: UInt32 = 0,
        resolution: (width: UInt32, height: UInt32) = (0, 0),
        fps: UInt32 = 0,
        codec: String = "",
        hasAudio: Bool = true,
        startedAt: Date = Date()
    ) {
        self.id = id
        self.streamType = streamType
        self.sessionID = sessionID
        self.senderID = senderID
        self.streamURL = streamURL
        self.bitrateKbps = bitrateKbps
        self.resolution = resolution
        self.fps = fps
        self.codec = codec
        self.hasAudio = hasAudio
        self.startedAt = startedAt
    }
}
