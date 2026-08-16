import SwiftUI
import AVKit

// MARK: - Stream View

struct StreamView: View {
    @EnvironmentObject private var appState: AppState
    @State private var showingStreamURLInput = false
    @State private var streamURLText = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if appState.activeStreams.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(appState.activeStreams) { session in
                            NavigationLink(destination: StreamPlayerView(session: session)) {
                                StreamSessionRowView(session: session)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    appState.streamService.stopStream(sessionID: session.id)
                                } label: {
                                    Label("Stop", systemImage: "stop.fill")
                                }
                            }
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                let session = appState.activeStreams[index]
                                appState.streamService.stopStream(sessionID: session.id)
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Streams")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingStreamURLInput = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingStreamURLInput) {
                streamURLInputSheet
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "play.rectangle")
                .font(.system(size: 60))
                .foregroundColor(.secondary)

            Text("No active streams")
                .font(.title3)
                .fontWeight(.medium)

            Text("Stream video, audio, or screen content from a connected device")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button(action: { showingStreamURLInput = true }) {
                Label("Open Stream", systemImage: "play.fill")
            }
            .buttonStyle(.borderedProminent)

            Spacer()
        }
    }

    private var streamURLInputSheet: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text("Enter a stream URL to play content from a connected device")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)

                TextField("Stream URL (e.g. http://192.168.1.100:8080/stream)", text: $streamURLText)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .keyboardType(.URL)

                if !appState.discoveredDevices.isEmpty {
                    Text("Or stream from a nearby device:")
                        .font(.subheadline)
                        .foregroundColor(.secondary)

                    ForEach(appState.discoveredDevices.filter { $0.supportsStreaming }) { device in
                        Button(action: {
                            startStreamWithDevice(device)
                            showingStreamURLInput = false
                        }) {
                            HStack {
                                Image(systemName: deviceTypeIcon(device))
                                    .foregroundColor(.blue)
                                Text(device.name)
                                    .foregroundColor(.primary)
                                Spacer()
                                Text("Stream")
                                    .font(.caption)
                                    .foregroundColor(.blue)
                            }
                            .padding()
                            .background(Color(.secondarySystemGroupedBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    }
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Start Streaming")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showingStreamURLInput = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Play") {
                        if let url = URL(string: streamURLText), !streamURLText.isEmpty {
                            startStreamWithURL(url)
                            showingStreamURLInput = false
                            streamURLText = ""
                        }
                    }
                    .disabled(streamURLText.isEmpty || URL(string: streamURLText) == nil)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func startStreamWithURL(_ url: URL) {
        let session = StreamSession(
            streamType: url.pathExtension == "mp3" || url.pathExtension == "wav" || url.pathExtension == "aac"
                ? .audio : .video,
            sessionID: UUID().uuidString,
            senderID: appState.deviceIdentity.deviceID,
            streamURL: url
        )

        appState.streamService.startStream(session: session, from: url)
    }

    private func startStreamWithDevice(_ device: DiscoveredDevice) {
        let session = StreamSession(
            streamType: .video,
            sessionID: UUID().uuidString,
            senderID: appState.deviceIdentity.deviceID
        )

        appState.streamService.connectToPeerStream(
            session: session,
            host: device.host,
            port: device.port
        )
    }

    private func deviceTypeIcon(_ device: DiscoveredDevice) -> String {
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

// MARK: - Stream Session Row

struct StreamSessionRowView: View {
    @ObservedObject var session: StreamSession

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(streamColor.opacity(0.1))
                    .frame(width: 48, height: 48)

                Image(systemName: session.streamTypeIcon)
                    .font(.title3)
                    .foregroundColor(streamColor)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(session.streamType == .video ? "Video Stream" : session.streamType == .audio ? "Audio Stream" : "Screen Stream")
                    .font(.subheadline)
                    .fontWeight(.medium)

                HStack(spacing: 4) {
                    Text(session.streamURL?.absoluteString ?? "Peer-to-peer stream")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Image(systemName: session.isPlaying ? "play.fill" : "pause.fill")
                    .font(.caption)
                    .foregroundColor(session.isPlaying ? .green : .orange)

                Text(session.formattedCurrentTime)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private var streamColor: Color {
        switch session.streamType {
        case .video:     return .red
        case .audio:     return .purple
        case .screen:    return .orange
        case .camera:    return .yellow
        case .microphone: return .pink
        case .file:      return .blue
        }
    }
}

// MARK: - Stream Player View

struct StreamPlayerView: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var session: StreamSession
    @State private var showControls = true
    @State private var controlsTimer: Timer?

    var body: some View {
        VStack(spacing: 0) {
            // Video Player Area
            ZStack {
                Color.black

                if let url = session.streamURL {
                    VideoPlayer(player: appState.streamService.players[session.id])
                        .ignoresSafeArea()
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(.system(size: 48))
                            .foregroundColor(.gray)

                        Text("Waiting for stream...")
                            .font(.headline)
                            .foregroundColor(.white)

                        Text("The remote device will start sending the stream shortly")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                }

                // Overlay Controls
                if showControls {
                    controlsOverlay
                }
            }
            .frame(maxHeight: .infinity)
            .onTapGesture {
                withAnimation { showControls.toggle() }
                if showControls {
                    controlsTimer?.invalidate()
                    controlsTimer = Timer.scheduledTimer(withTimeInterval: 4, repeats: false) { _ in
                        withAnimation { showControls = false }
                    }
                }
            }

            // Bottom Controls Bar
            bottomControlsBar
                .padding(.horizontal)
                .padding(.vertical, 8)
                .background(Color(.systemBackground))
        }
        .navigationTitle("Stream")
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear {
            controlsTimer?.invalidate()
        }
    }

    // MARK: - Overlay Controls

    private var controlsOverlay: some View {
        VStack {
            // Top gradient
            LinearGradient(
                gradient: Gradient(colors: [.black.opacity(0.6), .clear]),
                startPoint: .top,
                endPoint: .center
            )
            .frame(height: 80)

            Spacer()

            // Center Play/Pause
            Button(action: {
                if session.isPlaying {
                    appState.streamService.pause(sessionID: session.id)
                } else {
                    appState.streamService.play(sessionID: session.id)
                }
            }) {
                Image(systemName: session.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.white.opacity(0.8))
            }

            Spacer()

            // Bottom gradient
            LinearGradient(
                gradient: Gradient(colors: [.clear, .black.opacity(0.6)]),
                startPoint: .center,
                endPoint: .bottom
            )
            .frame(height: 80)
        }
    }

    // MARK: - Bottom Controls Bar

    private var bottomControlsBar: some View {
        VStack(spacing: 8) {
            // Progress Slider
            Slider(
                value: Binding(
                    get: { session.progress },
                    set: { newValue in
                        let dur = CMTimeGetSeconds(session.duration)
                        guard dur.isFinite, dur > 0 else { return }
                        let time = CMTime(seconds: dur * Double(newValue), preferredTimescale: 600)
                        appState.streamService.seek(sessionID: session.id, to: time)
                    }
                ),
                in: 0...1
            )
            .accentColor(.blue)

            // Time Labels
            HStack {
                Text(session.formattedCurrentTime)
                    .font(.caption)
                    .foregroundColor(.secondary)

                Spacer()

                Text(session.formattedDuration)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            // Control Buttons
            HStack(spacing: 24) {
                // Skip Back
                Button(action: {
                    let cur = CMTimeGetSeconds(session.currentTime)
                    let newTime = CMTime(seconds: max(0, cur - 15), preferredTimescale: 600)
                    appState.streamService.seek(sessionID: session.id, to: newTime)
                }) {
                    Image(systemName: "gobackward.15")
                        .font(.title2)
                }

                Spacer()

                // Play/Pause
                Button(action: {
                    if session.isPlaying {
                        appState.streamService.pause(sessionID: session.id)
                    } else {
                        appState.streamService.play(sessionID: session.id)
                    }
                }) {
                    Image(systemName: session.isPlaying ? "pause.fill" : "play.fill")
                        .font(.title)
                }

                Spacer()

                // Skip Forward
                Button(action: {
                    let cur = CMTimeGetSeconds(session.currentTime)
                    let dur = CMTimeGetSeconds(session.duration)
                    let newTime = CMTime(seconds: min(dur, cur + 15), preferredTimescale: 600)
                    appState.streamService.seek(sessionID: session.id, to: newTime)
                }) {
                    Image(systemName: "goforward.15")
                        .font(.title2)
                }
            }
            .padding(.horizontal, 20)

            // Volume & Mute
            HStack(spacing: 16) {
                Button(action: {
                    if session.isMuted {
                        appState.streamService.unmute(sessionID: session.id)
                    } else {
                        appState.streamService.mute(sessionID: session.id)
                    }
                }) {
                    Image(systemName: session.isMuted ? "speaker.slash.fill" : "speaker.fill")
                        .foregroundColor(.secondary)
                }

                Slider(
                    value: Binding(
                        get: { session.volume },
                        set: { appState.streamService.setVolume(sessionID: session.id, volume: $0) }
                    ),
                    in: 0...1
                )
                .accentColor(.blue)

                // Stream quality indicator
                HStack(spacing: 4) {
                    Image(systemName: session.connectionQuality.icon)
                        .font(.caption)
                        .foregroundColor(connectionColor)
                    Text(session.resolutionString)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    private var connectionColor: Color {
        switch session.connectionQuality {
        case .unknown, .poor:   return .red
        case .fair:             return .yellow
        case .good:             return .green
        case .excellent:        return .green
        }
    }
}
