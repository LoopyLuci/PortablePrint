import Foundation
import AVFoundation
import AVKit
import Network
import Combine

// MARK: - Stream Service

final class StreamService: NSObject {
    // MARK: - Callbacks

    var onStreamUpdate: ((StreamSession) -> Void)?
    var onStreamError: ((String, Error) -> Void)?
    var onStreamStateChange: ((String, Bool) -> Void)? // sessionID, isPlaying

    // MARK: - Properties

    private var sessions: [String: StreamSession] = [:]
    private var players: [String: AVPlayer] = [:]
    private var connections: [String: NWConnection] = [:]
    private let queue = DispatchQueue(label: "com.streamsync.stream", qos: .userInitiated)
    private var timeObservers: [String: Any] = [:]

    // MARK: - Start Stream

    func startStream(
        session: StreamSession,
        from url: URL,
        headers: [String: String] = [:]
    ) {
        sessions[session.id] = session

        // Configure AVPlayer with the stream URL
        let asset: AVURLAsset

        if !headers.isEmpty {
            let options = ["AVURLAssetHTTPHeaderFieldsKey": headers]
            asset = AVURLAsset(url: url, options: options)
        } else {
            asset = AVURLAsset(url: url)
        }

        let playerItem = AVPlayerItem(asset: asset)
        let player = AVPlayer(playerItem: playerItem)
        players[session.id] = player

        // Observe duration
        playerItem.addObserver(self, forKeyPath: "duration", options: [.new, .initial], context: UnsafeMutableRawPointer(bitPattern: session.id.hashValue))

        // Periodic time observer
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        let observer = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) {
            [weak self, weak session] time in
            session?.currentTime = time
            self?.onStreamUpdate?(session!)
        }
        timeObservers[session.id] = observer

        session.isPlaying = true
        onStreamUpdate?(session)

        // Observe status
        playerItem.addObserver(self, forKeyPath: "status", options: [.new], context: UnsafeMutableRawPointer(bitPattern: session.id.hashValue))

        player.play()
    }

    // MARK: - KVO

    override func observeValue(
        forKeyPath keyPath: String?,
        of object: Any?,
        change: [NSKeyValueChangeKey: Any]?,
        context: UnsafeMutableRawPointer?
    ) {
        guard let sessionIDPtr = context,
              let sessionID = String(validatingUTF8: unsafeBitCast(sessionIDPtr, to: UnsafePointer<CChar>.self)) else {
            return
        }

        guard let session = sessions[sessionID] else { return }

        if keyPath == "duration", let playerItem = object as? AVPlayerItem {
            session.duration = playerItem.duration
            onStreamUpdate?(session)
        }

        if keyPath == "status", let playerItem = object as? AVPlayerItem {
            switch playerItem.status {
            case .readyToPlay:
                session.isPlaying = true
                onStreamStateChange?(sessionID, true)
            case .failed:
                if let error = playerItem.error {
                    session.isPlaying = false
                    onStreamError?(sessionID, error)
                }
            default:
                break
            }
        }
    }

    // MARK: - Stream Control

    func play(sessionID: String) {
        players[sessionID]?.play()
        sessions[sessionID]?.isPlaying = true
        sessions[sessionID]?.isPaused = false
        if let session = sessions[sessionID] {
            onStreamUpdate?(session)
            onStreamStateChange?(sessionID, true)
        }
    }

    func pause(sessionID: String) {
        players[sessionID]?.pause()
        sessions[sessionID]?.isPlaying = false
        sessions[sessionID]?.isPaused = true
        if let session = sessions[sessionID] {
            onStreamUpdate?(session)
            onStreamStateChange?(sessionID, false)
        }
    }

    func seek(sessionID: String, to time: CMTime) {
        players[sessionID]?.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
        sessions[sessionID]?.currentTime = time
        if let session = sessions[sessionID] {
            onStreamUpdate?(session)
        }
    }

    func setVolume(sessionID: String, volume: Float) {
        players[sessionID]?.volume = volume
        sessions[sessionID]?.volume = volume
        if let session = sessions[sessionID] {
            onStreamUpdate?(session)
        }
    }

    func mute(sessionID: String) {
        players[sessionID]?.isMuted = true
        sessions[sessionID]?.isMuted = true
        if let session = sessions[sessionID] {
            onStreamUpdate?(session)
        }
    }

    func unmute(sessionID: String) {
        players[sessionID]?.isMuted = false
        sessions[sessionID]?.isMuted = false
        if let session = sessions[sessionID] {
            onStreamUpdate?(session)
        }
    }

    func stopStream(sessionID: String) {
        if let observer = timeObservers.removeValue(forKey: sessionID) {
            players[sessionID]?.removeTimeObserver(observer)
        }
        players[sessionID]?.pause()
        players[sessionID]?.replaceCurrentItem(with: nil)
        players.removeValue(forKey: sessionID)
        sessions.removeValue(forKey: sessionID)
        connections[sessionID]?.cancel()
        connections.removeValue(forKey: sessionID)
    }

    func stopAllStreams() {
        for sessionID in sessions.keys {
            stopStream(sessionID: sessionID)
        }
    }

    // MARK: - Network Stream (WebSocket-based streaming from peer)

    func connectToPeerStream(
        session: StreamSession,
        host: String,
        port: UInt16
    ) {
        sessions[session.id] = session

        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port)!
        )

        let parameters = NWParameters(tls: nil, tcp: NWProtocolTCP.Options())
        parameters.includePeerToPeer = true

        let connection = NWConnection(to: endpoint, using: parameters)
        connections[session.id] = connection

        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                // Request stream start
                var startMsg = Data()
                startMsg.append(contentsOf: [0x10]) // StreamStart
                startMsg.append(session.id.data(using: .utf8) ?? Data())
                startMsg.append(session.sessionID.data(using: .utf8) ?? Data())

                connection.send(content: startMsg, completion: .contentProcessed(nil))
                self?.receiveStreamData(connection, session: session)
            case .failed(let error):
                session.isPlaying = false
                self?.onStreamError?(session.id, error)
            default:
                break
            }
        }

        connection.start(queue: queue)
    }

    private func receiveStreamData(_ connection: NWConnection, session: StreamSession) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 2 * 1024 * 1024) {
            [weak self] data, context, isComplete, error in
            guard let self = self else { return }

            if let data = data {
                // Handle stream packet - real impl would decode protobuf
                // and feed to AVPlayer via custom resource loader
                print("Received stream data: \(data.count) bytes")
            }

            if isComplete || error != nil {
                return
            }

            self.receiveStreamData(connection, session: session)
        }
    }

    // MARK: - Send Stream Control

    func sendControl(sessionID: String, command: UInt8, param: Int64) {
        guard let connection = connections[sessionID] else { return }

        var message = Data()
        message.append(contentsOf: [0x12]) // StreamControl
        message.append(command)
        var paramLE = param.littleEndian
        message.append(contentsOf: withUnsafeBytes(of: &paramLE) { Data($0) })

        connection.send(content: message, completion: .contentProcessed(nil))
    }

    deinit {
        stopAllStreams()
        connections.values.forEach { $0.cancel() }
    }
}
