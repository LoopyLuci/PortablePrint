import Foundation
import UIKit
import Combine

// MARK: - Clipboard Service

final class ClipboardService {
    // MARK: - Callbacks

    var onClipboardReceived: ((String) -> Void)?
    var onClipboardImageReceived: ((UIImage) -> Void)?
    var onClipboardError: ((Error) -> Void)?

    // MARK: - Properties

    private var lastChangeCount: Int = UIPasteboard.general.changeCount
    private var monitoringTimer: Timer?
    private var isMonitoring = false
    private let checkInterval: TimeInterval = 0.5

    var currentClipboardText: String? {
        UIPasteboard.general.string
    }

    var currentClipboardImage: UIImage? {
        UIPasteboard.general.image
    }

    var currentClipboardURL: URL? {
        UIPasteboard.general.url
    }

    // MARK: - Monitoring

    func startMonitoring() {
        guard !isMonitoring else { return }
        isMonitoring = true
        lastChangeCount = UIPasteboard.general.changeCount

        monitoringTimer = Timer.scheduledTimer(
            timeInterval: checkInterval,
            target: self,
            selector: #selector(checkClipboard),
            userInfo: nil,
            repeats: true
        )
    }

    func stopMonitoring() {
        monitoringTimer?.invalidate()
        monitoringTimer = nil
        isMonitoring = false
    }

    @objc private func checkClipboard() {
        let currentChangeCount = UIPasteboard.general.changeCount
        guard currentChangeCount != lastChangeCount else { return }

        lastChangeCount = currentChangeCount

        // Check for text content
        if let text = UIPasteboard.general.string, !text.isEmpty {
            onClipboardReceived?(text)
        }

        // Check for image content
        if let image = UIPasteboard.general.image {
            onClipboardImageReceived?(image)
        }
    }

    // MARK: - Set Clipboard

    func setText(_ text: String) {
        UIPasteboard.general.string = text
        lastChangeCount = UIPasteboard.general.changeCount
    }

    func setImage(_ image: UIImage) {
        UIPasteboard.general.image = image
        lastChangeCount = UIPasteboard.general.changeCount
    }

    func setURL(_ url: URL) {
        UIPasteboard.general.url = url
        lastChangeCount = UIPasteboard.general.changeCount
    }

    // MARK: - Rich Content

    func setRichText(html: String, plainText: String) {
        UIPasteboard.general.items = [
            [
                "public.html": html,
                "public.utf8-plain-text": plainText
            ]
        ]
        lastChangeCount = UIPasteboard.general.changeCount
    }

    // MARK: - Clear

    func clear() {
        UIPasteboard.general.items = []
        lastChangeCount = UIPasteboard.general.changeCount
    }

    deinit {
        stopMonitoring()
    }
}
