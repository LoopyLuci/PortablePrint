import Foundation
import Network
import Combine

// MARK: - Transfer Service

final class TransferService: NSObject {
    // MARK: - Callbacks

    var onTransferUpdate: ((TransferSession) -> Void)?
    var onTransferRequest: ((TransferSession) -> Void)?
    var onTransferComplete: ((TransferSession) -> Void)?
    var onTransferError: ((String, Error) -> Void)?

    // MARK: - Properties

    private var connections: [String: NWConnection] = [:]
    private var listeners: [UInt16: NWListener] = [:]
    private var activeSessions: [String: TransferSession] = [:]
    private let queue = DispatchQueue(label: "com.streamsync.transfer", qos: .utility)

    private let maxChunkSize: UInt32 = 65536

    // MARK: - Start Listening

    func startListening(on port: UInt16) throws {
        let tcpOptions = NWProtocolTCP.Options()
        tcpOptions.enableKeepalive = true
        tcpOptions.keepaliveIdle = 30

        let parameters = NWParameters(tls: nil, tcp: tcpOptions)
        parameters.includePeerToPeer = true

        let listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)

        listener.stateUpdateHandler = { state in
            switch state {
            case .ready:
                print("Transfer listener ready on port \(port)")
            case .failed(let error):
                print("Transfer listener failed: \(error)")
            default:
                break
            }
        }

        listener.newConnectionHandler = { [weak self] connection in
            self?.handleIncomingConnection(connection)
        }

        listener.start(queue: queue)

        if let existing = listeners[port] {
            existing.cancel()
        }
        listeners[port] = listener
    }

    func stopListening() {
        listeners.values.forEach { $0.cancel() }
        listeners.removeAll()
    }

    // MARK: - Connect to Peer

    func connectToPeer(host: String, port: UInt16, session: TransferSession) {
        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port)!
        )

        let tcpOptions = NWProtocolTCP.Options()
        tcpOptions.enableKeepalive = true
        tcpOptions.keepaliveIdle = 30

        let parameters = NWParameters(tls: nil, tcp: tcpOptions)
        parameters.includePeerToPeer = true

        let connection = NWConnection(to: endpoint, using: parameters)

        connections[session.id] = connection
        activeSessions[session.id] = session

        session.status = .pending

        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.sendTransferRequest(session, over: connection)
            case .failed(let error):
                session.status = .failed
                session.errorMessage = error.localizedDescription
                self?.onTransferError?(session.id, error)
            case .cancelled:
                session.status = .cancelled
            default:
                break
            }
        }

        connection.start(queue: queue)

        receiveMessages(connection, session: session)
    }

    // MARK: - Send Transfer Request

    private func sendTransferRequest(_ session: TransferSession, over connection: NWConnection) {
        var requestData = Data()
        requestData.append(contentsOf: [0x01]) // message type: TransferRequest
        requestData.append(session.id.data(using: .utf8) ?? Data())
        requestData.append(session.transferType.rawValue.data(using: .utf8) ?? Data())
        requestData.append(session.senderID.data(using: .utf8) ?? Data())
        requestData.append(session.targetID.data(using: .utf8) ?? Data())

        // Add file metadata if file transfer
        if !session.files.isEmpty {
            let encoder = JSONEncoder()
            if let filesData = try? encoder.encode(session.files) {
                var size = UInt32(filesData.count).littleEndian
                requestData.append(contentsOf: withUnsafeBytes(of: &size) { Data($0) })
                requestData.append(filesData)
            }
        }

        connection.send(
            content: requestData,
            completion: .contentProcessed({ [weak self] error in
                if let error = error {
                    session.status = .failed
                    session.errorMessage = error.localizedDescription
                    self?.onTransferError?(session.id, error)
                } else {
                    session.status = .transferring
                    self?.onTransferUpdate?(session)
                }
            })
        )
    }

    // MARK: - Incoming Connection

    private func handleIncomingConnection(_ connection: NWConnection) {
        connection.start(queue: queue)

        let session = TransferSession(
            transferType: .file,
            senderID: "",
            targetID: ""
        )
        connections[session.id] = connection
        activeSessions[session.id] = session

        receiveMessages(connection, session: session)
    }

    // MARK: - Receive Messages

    private func receiveMessages(_ connection: NWConnection, session: TransferSession) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 10 * 1024 * 1024) {
            [weak self] data, context, isComplete, error in
            guard let self = self else { return }

            if let data = data, !data.isEmpty {
                self.processReceivedData(data, session: session, connection: connection)
            }

            if isComplete {
                connection.cancel()
                self.connections.removeValue(forKey: session.id)
                return
            }

            if let error = error {
                session.status = .failed
                session.errorMessage = error.localizedDescription
                self.onTransferError?(session.id, error)
                return
            }

            // Continue receiving
            self.receiveMessages(connection, session: session)
        }
    }

    private func processReceivedData(_ data: Data, session: TransferSession, connection: NWConnection) {
        guard data.count >= 1 else { return }
        let messageType = data[0]

        switch messageType {
        case 0x01: // TransferRequest
            handleTransferRequest(data, session: session, connection: connection)
        case 0x02: // TransferResponse
            handleTransferResponse(data, session: session)
        case 0x03: // TransferChunk
            handleTransferChunk(data, session: session)
        case 0x04: // TransferComplete
            handleTransferComplete(session)
        case 0x05: // TransferError
            handleTransferError(data, session: session)
        default:
            print("Unknown transfer message type: \(messageType)")
        }
    }

    // MARK: - Message Handlers

    private func handleTransferRequest(_ data: Data, session: TransferSession, connection: NWConnection) {
        // Parse request (simplified)
        var offset = 1
        if let id = data.subdata(in: offset..<data.count).toStringUTF8() {
            // In real implementation, properly parse the protobuf fields
            session.status = .transferring
            onTransferRequest?(session)
        }
    }

    private func handleTransferResponse(_ data: Data, session: TransferSession) {
        // Parse response
        session.status = .transferring
        onTransferUpdate?(session)
    }

    private func handleTransferChunk(_ data: Data, session: TransferSession) {
        // Simplified parsing; real impl uses protobuf
        let headerSize = 33 // 1 type + 16 UUID + 8 offset + 4 length + 4 checksum
        guard data.count >= headerSize else { return }

        let chunkData = data.subdata(in: headerSize..<data.count)
        session.bytesTransferred += UInt64(chunkData.count)
        session.progressPercent = session.totalBytes > 0
            ? UInt32(Double(session.bytesTransferred) / Double(session.totalBytes) * 100)
            : 0
        session.speedMbps = Double(chunkData.count) / 1024.0 / 1024.0 // simplistic

        onTransferUpdate?(session)
    }

    private func handleTransferComplete(_ session: TransferSession) {
        session.status = .completed
        session.completedAt = Date()
        onTransferComplete?(session)
    }

    private func handleTransferError(_ data: Data, session: TransferSession) {
        session.status = .failed
        session.errorMessage = "Transfer error"
        onTransferError?(session.id, NSError(domain: "Transfer", code: -1))
    }

    // MARK: - Send Data

    func sendFileChunk(sessionID: String, chunkData: Data) {
        guard let connection = connections[sessionID],
              let session = activeSessions[sessionID] else { return }

        var message = Data()
        message.append(contentsOf: [0x03]) // TransferChunk
        message.append(chunkData)

        connection.send(content: message, completion: .contentProcessed({ [weak self] error in
            if let error = error {
                session.status = .failed
                session.errorMessage = error.localizedDescription
                self?.onTransferError?(sessionID, error)
            }
        }))
    }

    func cancelTransfer(sessionID: String) {
        guard let session = activeSessions[sessionID] else { return }
        session.status = .cancelled

        var message = Data()
        message.append(contentsOf: [0x06]) // TransferCancel
        connections[sessionID]?.send(content: message, completion: .contentProcessed(nil))
        connections[sessionID]?.cancel()
        connections.removeValue(forKey: sessionID)
        activeSessions.removeValue(forKey: sessionID)
        onTransferUpdate?(session)
    }

    func pauseTransfer(sessionID: String) {
        guard let session = activeSessions[sessionID] else { return }
        session.status = .paused

        var message = Data()
        message.append(contentsOf: [0x07]) // TransferPause
        connections[sessionID]?.send(content: message, completion: .contentProcessed(nil))
        onTransferUpdate?(session)
    }

    func resumeTransfer(sessionID: String) {
        guard let session = activeSessions[sessionID] else { return }
        session.status = .transferring

        var message = Data()
        message.append(contentsOf: [0x08]) // TransferResume
        connections[sessionID]?.send(content: message, completion: .contentProcessed(nil))
        onTransferUpdate?(session)
    }

    deinit {
        stopListening()
        connections.values.forEach { $0.cancel() }
        connections.removeAll()
    }
}

// MARK: - Data Extension

private extension Data {
    func toStringUTF8() -> String? {
        String(data: self, encoding: .utf8)
    }
}
