import Foundation
import Network

// MARK: - Discovery Service

final class DiscoveryService: NSObject {
    private var browser: NWBrowser?
    private var listeners: [String: NWListener] = [:]

    var onDeviceDiscovered: ((DiscoveredDevice) -> Void)?
    var onDeviceLost: ((DiscoveredDevice) -> Void)?
    var onServiceReady: (() -> Void)?
    var onServiceError: ((Error) -> Void)?

    private var discoveredDevices: [String: DiscoveredDevice] = [:]

    // MARK: - Discovery (Browse)

    func startDiscovery(serviceType: String) {
        let parameters = NWParameters()
        parameters.includePeerToPeer = true

        let browserDescriptor = NWBrowser.Descriptor.bonjour(
            type: serviceType,
            domain: "local"
        )

        browser = NWBrowser(for: browserDescriptor, using: parameters)
        browser?.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.onServiceReady?()
            case .failed(let error):
                self?.onServiceError?(error)
            case .cancelled:
                break
            default:
                break
            }
        }

        browser?.browseResultsChangedHandler = { [weak self] results, changes in
            self?.handleBrowseResults(results, changes: changes)
        }

        browser?.start(queue: .global(qos: .background))
    }

    func stopDiscovery() {
        browser?.cancel()
        browser = nil
        discoveredDevices.removeAll()
        listeners.values.forEach { $0.cancel() }
        listeners.removeAll()
    }

    private func handleBrowseResults(
        _ results: Set<NWBrowser.Result>,
        changes: Set<NWBrowser.Result.Change>
    ) {
        for change in changes {
            switch change {
            case .added(let result):
                resolveEndpoint(result)
            case .removed(let result):
                handleDeviceRemoved(result)
            case .changed(let old, let new, _):
                resolveEndpoint(new)
                if old.endpoint != new.endpoint {
                    handleDeviceRemoved(old)
                }
            @unknown default:
                break
            }
        }
    }

    private func resolveEndpoint(_ result: NWBrowser.Result) {
        let endpoint = result.endpoint

        guard case let .service(name, type, domain, _) = endpoint else {
            return
        }

        let parameters = NWParameters()
        parameters.includePeerToPeer = true

        let connection = NWConnection(to: endpoint, using: parameters)
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                if let txtData = result.metadata?.asData(),
                   let txtRecord = Dictionary(
                       uniqueKeysWithValues: txtData.withUnsafeBytes { ptr in
                           guard let base = ptr.baseAddress else { return [(String, String)]() }
                           return (0..<txtData.count).compactMap { offset -> (String, String)? in
                               // Bonjour TXT record key=value parsing
                               let kv = String(cString: base.assumingMemoryBound(to: UInt8.self).advanced(by: offset))
                               let parts = kv.split(separator: "=", maxSplits: 1)
                               guard parts.count == 2 else { return nil }
                               return (String(parts[0]), String(parts[1]))
                           }
                       } as [String: String]
                   ) {
                    let device = DiscoveredDevice(
                        id: name,
                        name: txtRecord["device_name"] ?? name,
                        deviceType: DeviceType(rawValue: txtRecord["device_type"] ?? "DEVICE_UNKNOWN") ?? .unknown,
                        host: endpoint.debugDescription,
                        port: UInt16(txtRecord["port"] ?? "0") ?? 0,
                        txtRecord: txtRecord,
                        osVersion: txtRecord["os_version"] ?? "",
                        appVersion: txtRecord["app_version"] ?? "",
                        protocolVersion: UInt32(txtRecord["protocol_version"] ?? "2") ?? 2,
                        capabilities: txtRecord["capabilities"]?.split(separator: ",").map(String.init) ?? [],
                        rssi: Int32(result.interfaces.first?.debugDescription.hashValue ?? 0) // approximated
                    )
                    self?.discoveredDevices[name] = device
                    self?.onDeviceDiscovered?(device)
                }
                connection.cancel()
            case .failed:
                connection.cancel()
            default:
                break
            }
        }
        connection.start(queue: .global(qos: .background))
    }

    private func handleDeviceRemoved(_ result: NWBrowser.Result) {
        guard case let .service(name, _, _, _) = result.endpoint else { return }
        if let device = discoveredDevices.removeValue(forKey: name) {
            onDeviceLost?(device)
        }
    }

    // MARK: - Advertising

    func startAdvertising(
        serviceType: String,
        port: UInt16 = 0,
        txtRecord: [String: String]
    ) throws {
        let tcpOptions = NWProtocolTCP.Options()
        tcpOptions.enableKeepalive = true
        tcpOptions.keepaliveIdle = 30

        let parameters = NWParameters(tls: nil, tcp: tcpOptions)
        parameters.includePeerToPeer = true

        let txtData = txtRecord.txtRecordData()

        let listener: NWListener
        if port > 0 {
            listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
        } else {
            listener = try NWListener(using: parameters, on: .any)
        }

        listener.service = NWListener.Service(
            type: serviceType,
            txtRecord: txtData
        )

        listener.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                if let port = listener.port?.rawValue {
                    print("Advertising on port \(port)")
                }
                self?.onServiceReady?()
            case .failed(let error):
                self?.onServiceError?(error)
            default:
                break
            }
        }

        listener.newConnectionHandler = { [weak self] connection in
            self?.handleIncomingConnection(connection)
        }

        listener.start(queue: .global(qos: .background))

        let key = "\(serviceType):\(listener.port?.rawValue ?? 0)"
        listeners[key]?.cancel()
        listeners[key] = listener
    }

    func stopAdvertising(serviceType: String? = nil) {
        if let type = serviceType {
            listeners.filter { $0.key.hasPrefix(type) }.values.forEach { $0.cancel() }
            listeners = listeners.filter { !$0.key.hasPrefix(type) }
        } else {
            listeners.values.forEach { $0.cancel() }
            listeners.removeAll()
        }
    }

    private func handleIncomingConnection(_ connection: NWConnection) {
        // Connection will be handled by TransferService or StreamService
        connection.receiveMessage { [weak self] data, context, isComplete, error in
            if let data = data {
                // Parse StreamSyncMessage from data
                print("Received incoming connection data: \(data.count) bytes")
            }
            connection.cancel()
        }
        connection.start(queue: .global(qos: .background))
    }

    deinit {
        stopDiscovery()
        stopAdvertising()
    }
}

// MARK: - TXT Record Helpers

extension Dictionary where Key == String, Value == String {
    func txtRecordData() -> Data {
        var records = Data()

        for (key, value) in self {
            let kv = "\(key)=\(value)"
            if let kvData = kv.data(using: .utf8) {
                var length = UInt8(kvData.count)
                records.append(&length, count: 1)
                records.append(kvData)
            }
        }

        return records
    }
}
