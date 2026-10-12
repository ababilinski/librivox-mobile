import AVKit
import LibriVoxCore
import MediaPlayer
import SwiftUI
import UIKit

#if canImport(GoogleCast)
import GoogleCast
#endif

#if canImport(GroupActivities)
import GroupActivities
#endif

struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.prioritizesVideoDevices = false
        view.tintColor = .label
        view.activeTintColor = .systemBlue
        return view
    }

    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}

struct AudioOutputRoute: Equatable {
    var name: String
    var systemImage: String
    var isExternal: Bool

    static var current: AudioOutputRoute {
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        let routeName = outputs
            .map(\.portName)
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .joined(separator: ", ")
        let firstOutput = outputs.first
        return AudioOutputRoute(
            name: routeName.isEmpty ? "iPhone" : routeName,
            systemImage: firstOutput.map(systemImage(for:)) ?? "iphone",
            isExternal: outputs.contains(where: isExternalOutput)
        )
    }

    var shouldShowRouteControls: Bool {
        isExternal
    }

    private static func systemImage(for output: AVAudioSessionPortDescription) -> String {
        switch output.portType {
        case .airPlay:
            "airplayaudio"
        case .bluetoothA2DP, .bluetoothHFP, .bluetoothLE:
            "airpodspro"
        case .builtInReceiver:
            "iphone"
        case .builtInSpeaker:
            "speaker.wave.2.fill"
        case .headphones:
            "headphones"
        case .carAudio:
            "car.fill"
        case .HDMI:
            "tv"
        case .usbAudio:
            "cable.connector"
        default:
            "speaker.wave.2.fill"
        }
    }

    private static func isExternalOutput(_ output: AVAudioSessionPortDescription) -> Bool {
        switch output.portType {
        case .airPlay,
             .bluetoothA2DP,
             .bluetoothHFP,
             .bluetoothLE,
             .headphones,
             .carAudio,
             .HDMI,
             .usbAudio:
            true
        default:
            false
        }
    }
}

@MainActor
final class AudioOutputRouteMonitor: ObservableObject {
    @Published private(set) var route = AudioOutputRoute.current

    var shouldShowRouteControls: Bool {
        route.shouldShowRouteControls
    }

    func refresh() {
        route = .current
    }
}

struct SystemVolumeSlider: UIViewRepresentable {
    func makeUIView(context: Context) -> MPVolumeView {
        let view = MPVolumeView(frame: .zero)
        view.showsVolumeSlider = true
        view.tintColor = .label
        view.setVolumeThumbImage(UIImage(systemName: "circle.fill"), for: .normal)
        return view
    }

    func updateUIView(_ uiView: MPVolumeView, context: Context) {}
}

struct AudioOutputRoutePill: View {
    var route: AudioOutputRoute
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Label {
                Text(route.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            } icon: {
                Image(systemName: route.systemImage)
                    .font(.body.weight(.semibold))
            }
            .foregroundStyle(.secondary)
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
            .background(.thinMaterial, in: Capsule(style: .continuous))
            .overlay {
                Capsule(style: .continuous)
                    .stroke(.white.opacity(0.18), lineWidth: 0.8)
                    .allowsHitTesting(false)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Audio output")
        .accessibilityValue(route.name)
        .accessibilityHint("Opens output source and volume controls")
    }
}

struct AudioOutputCurrentRouteRow: View {
    var route: AudioOutputRoute

    var body: some View {
        HStack(spacing: 12) {
            Label {
                VStack(alignment: .leading, spacing: 3) {
                    Text(route.name)
                        .lineLimit(1)
                    Text(route.isExternal ? "Connected" : "This iPhone")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            } icon: {
                Image(systemName: route.systemImage)
                    .font(.title3.weight(.semibold))
                    .frame(width: 30)
            }
            Spacer()
            Image(systemName: "checkmark")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.tint)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Current output")
        .accessibilityValue(route.name)
        .accessibilityAddTraits(.isSelected)
        .accessibilityIdentifier("current-output-row")
    }
}

struct AirPlayBluetoothRouteRow: View {
    var route: AudioOutputRoute

    var body: some View {
        ZStack {
            HStack(spacing: 12) {
                Image(systemName: route.isExternal ? route.systemImage : "airplayaudio")
                    .font(.title3.weight(.semibold))
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text("AirPlay & Bluetooth")
                        .lineLimit(1)
                    Text(route.isExternal ? route.name : "Choose AirPods, Bluetooth, or AirPlay")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: route.isExternal ? "checkmark.circle.fill" : "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(route.isExternal ? .green : .secondary)
            }
            .contentShape(Rectangle())
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            AirPlayButton()
                .opacity(0.02)
                .frame(maxWidth: .infinity, minHeight: 54)
                .accessibilityIdentifier("airplay-bluetooth-route-row")
                .accessibilityLabel("AirPlay & Bluetooth")
                .accessibilityValue(route.isExternal ? route.name : "Choose a device")
                .accessibilityHint("Opens the system list of AirPlay and Bluetooth devices")
        }
    }
}

struct AudioOutputVolumeRow: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "speaker.fill")
                .foregroundStyle(.secondary)
                .frame(width: 24)
            SystemVolumeSlider()
                .frame(height: 32)
            Image(systemName: "speaker.wave.3.fill")
                .foregroundStyle(.secondary)
                .frame(width: 24)
        }
        .padding(.horizontal, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Volume")
        .accessibilityIdentifier("audio-output-volume")
    }
}

struct AudiobookSharePlayMessage: Codable, Identifiable, Sendable {
    enum Reason: String, Codable, Sendable {
        case snapshot
        case itemChanged
    }

    var id: UUID
    var senderID: UUID
    var reason: Reason
    var bookID: String
    var bookTitle: String
    var chapterID: String?
    var positionSeconds: Double
    var isPlaying: Bool

    init(
        id: UUID = UUID(),
        senderID: UUID = SharePlayDeviceIdentity.current,
        reason: Reason,
        bookID: String,
        bookTitle: String,
        chapterID: String?,
        positionSeconds: Double,
        isPlaying: Bool
    ) {
        self.id = id
        self.senderID = senderID
        self.reason = reason
        self.bookID = bookID
        self.bookTitle = bookTitle
        self.chapterID = chapterID
        self.positionSeconds = positionSeconds
        self.isPlaying = isPlaying
    }
}

enum SharePlayDeviceIdentity {
    private static let defaultsKey = "LibriVoxMobile.sharePlayDeviceID"

    static var current: UUID {
        if let value = UserDefaults.standard.string(forKey: defaultsKey),
           let uuid = UUID(uuidString: value) {
            return uuid
        }
        let uuid = UUID()
        UserDefaults.standard.set(uuid.uuidString, forKey: defaultsKey)
        return uuid
    }
}

#if canImport(GroupActivities)
@MainActor
@Observable
final class SharePlayCoordinator {
    var statusText = "Start Apple group listening"
    var isActive = false
    var participantsCount = 0

    private weak var app: AppModel?
    private var sessionsTask: Task<Void, Never>?
    private var messagesTask: Task<Void, Never>?
    private var stateTask: Task<Void, Never>?
    private var participantsTask: Task<Void, Never>?
    private var currentSession: GroupSession<AudiobookListeningActivity>?
    private var messenger: GroupSessionMessenger?
    private let groupStateObserver = GroupStateObserver()

    var isEligibleForGroupSession: Bool {
        groupStateObserver.isEligibleForGroupSession
    }

    func configure(app: AppModel) {
        self.app = app
        guard sessionsTask == nil else { return }
        sessionsTask = Task { [weak self] in
            for await session in AudiobookListeningActivity.sessions() {
                await self?.handle(session)
            }
        }
    }

    func startSharePlay() async -> String {
        if isActive {
            await sendCurrentSnapshot(reason: .snapshot)
            return "SharePlay is already connected"
        }
        guard let snapshot = app?.currentSharePlaySnapshot(reason: .snapshot) else {
            statusText = "Start a book before SharePlay"
            return statusText
        }

        let activity = AudiobookListeningActivity(message: snapshot)
        switch await activity.prepareForActivation() {
        case .activationPreferred:
            do {
                let didActivate = try await activity.activate()
                statusText = didActivate ? "Starting SharePlay..." : "SharePlay did not start"
            } catch {
                statusText = "SharePlay could not start"
                app?.recordRouteDiagnostic("SharePlay activation failed: \(error.localizedDescription)")
            }
        case .activationDisabled:
            statusText = "Invite people to start SharePlay"
        case .cancelled:
            statusText = "SharePlay cancelled"
        @unknown default:
            statusText = "SharePlay did not start"
        }
        return statusText
    }

    func makeSharingActivity() -> AudiobookListeningActivity? {
        guard let snapshot = app?.currentSharePlaySnapshot(reason: .snapshot) else {
            statusText = "Start a book before SharePlay"
            return nil
        }
        statusText = "Choose people to listen with"
        return AudiobookListeningActivity(message: snapshot)
    }

    func handleSharingResult(_ result: GroupActivitySharingResult) {
        switch result {
        case .success:
            statusText = "Starting SharePlay..."
            app?.recordRouteDiagnostic("SharePlay invitation started")
        case .cancelled:
            statusText = "SharePlay cancelled"
            app?.recordRouteDiagnostic("SharePlay invitation cancelled")
        @unknown default:
            statusText = "SharePlay did not start"
            app?.recordRouteDiagnostic("SharePlay invitation returned an unknown result")
        }
    }

    func leaveSharePlay() {
        currentSession?.leave()
        resetSession(status: "Start Apple group listening")
        app?.recordRouteDiagnostic("Left SharePlay session")
    }

    func send(_ message: AudiobookSharePlayMessage) async {
        guard isActive, let messenger else { return }
        do {
            try await messenger.send(message)
        } catch {
            app?.recordRouteDiagnostic("SharePlay send failed: \(error.localizedDescription)")
        }
    }

    private func handle(_ session: GroupSession<AudiobookListeningActivity>) async {
        resetSession(status: "Connecting SharePlay...")
        currentSession = session
        participantsCount = session.activeParticipants.count
        isActive = true
        statusText = "SharePlay connected"
        app?.recordRouteDiagnostic("SharePlay session connected")

        let messenger = GroupSessionMessenger(session: session, deliveryMode: .reliable)
        self.messenger = messenger

        if !session.isLocallyInitiated {
            await app?.applySharePlayMessage(session.activity.message)
        }
        app?.playback.coordinateWithSharePlaySession(session)
        session.join()

        messagesTask = Task { [weak self] in
            for await (message, context) in messenger.messages(of: AudiobookSharePlayMessage.self) {
                await self?.receive(message, from: context.source, localParticipant: session.localParticipant)
            }
        }

        stateTask = Task { [weak self] in
            for await state in session.$state.values {
                if case .invalidated(let reason) = state {
                    self?.app?.recordRouteDiagnostic("SharePlay invalidated: \(reason.localizedDescription)")
                    self?.resetSession(status: "Start Apple group listening")
                    break
                }
            }
        }

        participantsTask = Task { [weak self] in
            for await participants in session.$activeParticipants.values {
                self?.updateParticipants(participants.count)
            }
        }

        if session.isLocallyInitiated {
            await sendCurrentSnapshot(reason: .snapshot)
        }
    }

    private func receive(
        _ message: AudiobookSharePlayMessage,
        from source: Participant,
        localParticipant: Participant
    ) async {
        guard source != localParticipant, message.senderID != SharePlayDeviceIdentity.current else { return }
        app?.recordRouteDiagnostic("SharePlay received \(message.reason.rawValue) for \(message.bookTitle)")
        await app?.applySharePlayMessage(message)
    }

    private func sendCurrentSnapshot(reason: AudiobookSharePlayMessage.Reason) async {
        guard let snapshot = app?.currentSharePlaySnapshot(reason: reason) else { return }
        await send(snapshot)
    }

    private func updateParticipants(_ count: Int) {
        participantsCount = count
        statusText = count > 0 ? "SharePlay connected with \(count) other listener\(count == 1 ? "" : "s")" : "SharePlay connected"
    }

    private func resetSession(status: String) {
        messagesTask?.cancel()
        stateTask?.cancel()
        participantsTask?.cancel()
        messagesTask = nil
        stateTask = nil
        participantsTask = nil
        currentSession = nil
        messenger = nil
        participantsCount = 0
        isActive = false
        statusText = status
    }
}
#else
@MainActor
@Observable
final class SharePlayCoordinator {
    var statusText = "Apple group listening will appear here when enabled"
    var isActive = false
    var participantsCount = 0

    func configure(app: AppModel) {}
    func startSharePlay() async -> String { statusText }
    func leaveSharePlay() {}
    func send(_ message: AudiobookSharePlayMessage) async {}
}
#endif

enum GoogleCastBridge {
    @MainActor private static var didConfigure = false

    static var isAvailable: Bool {
        #if canImport(GoogleCast)
        true
        #else
        false
        #endif
    }

    @MainActor
    static func configureIfAvailable() {
        #if canImport(GoogleCast)
        guard !didConfigure else { return }
        let criteria = GCKDiscoveryCriteria(applicationID: kGCKDefaultMediaReceiverApplicationID)
        let options = GCKCastOptions(discoveryCriteria: criteria)
        GCKCastContext.setSharedInstanceWith(options)
        didConfigure = true
        #endif
    }

    @MainActor
    static var isConnected: Bool {
        #if canImport(GoogleCast)
        configureIfAvailable()
        return GCKCastContext.sharedInstance().sessionManager.currentCastSession != nil
        #else
        false
        #endif
    }
}

struct RoutePickerSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @StateObject private var routeMonitor = AudioOutputRouteMonitor()
    var book: AudioBook?

    var body: some View {
        NavigationStack {
            List {
                Section("Playing") {
                    if let book {
                        HStack(spacing: 14) {
                            BookCoverView(book: book, width: 56, height: 72, cornerRadius: 12, preferFullImage: true)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(book.title)
                                    .font(.headline)
                                    .lineLimit(2)
                                Text(book.author)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                    AudioOutputCurrentRouteRow(route: displayedRoute)
                    AudioOutputVolumeRow()
                }

                Section("All Devices") {
                    AirPlayBluetoothRouteRow(route: routeMonitor.route)
                    GoogleCastRouteRow()
                }

                Section("Listen Together") {
                    SharePlayRouteRow(book: book)
                }
            }
            .accessibilityIdentifier("audio-output-sheet")
            .glassPageBackground(book: book)
            .navigationTitle("Audio Output")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                routeMonitor.refresh()
            }
            .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.routeChangeNotification)) { _ in
                routeMonitor.refresh()
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }

    private var displayedRoute: AudioOutputRoute {
        if GoogleCastBridge.isConnected {
            return AudioOutputRoute(
                name: "Google Cast",
                systemImage: "dot.radiowaves.left.and.right",
                isExternal: true
            )
        }

        if app.sharePlay.isActive {
            return AudioOutputRoute(
                name: "SharePlay",
                systemImage: "shareplay",
                isExternal: true
            )
        }

        return routeMonitor.route
    }
}

struct GoogleCastRouteRow: View {
    var body: some View {
        #if canImport(GoogleCast)
        ZStack {
            HStack {
                Label {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Google Cast")
                        Text(GoogleCastBridge.isConnected ? "Connected" : "Chromecast and Google speakers")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                } icon: {
                    Image(systemName: "dot.radiowaves.left.and.right")
                }
                Spacer()
                Image(systemName: GoogleCastBridge.isConnected ? "checkmark.circle.fill" : "chevron.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(GoogleCastBridge.isConnected ? .green : .secondary)
            }
            .contentShape(Rectangle())
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            GoogleCastButton()
                .opacity(0.02)
                .frame(maxWidth: .infinity, minHeight: 48)
                .accessibilityLabel("Choose Google Cast device")
                .accessibilityValue(GoogleCastBridge.isConnected ? "Connected" : "Not connected")
                .accessibilityHint("Opens the Google Cast device list")
                .accessibilityIdentifier("google-cast-route-row")
        }
        #else
        RouteCapabilityRow(
            title: "Google Cast",
            subtitle: "Chromecast and Google speakers will appear here when enabled",
            systemImage: "dot.radiowaves.left.and.right",
            isEnabled: false
        )
        .accessibilityIdentifier("google-cast-route-row")
        #endif
    }
}

struct SharePlayRouteRow: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook?
    @State private var sharingPresentation: SharePlaySharingPresentation?
    @State private var isStarting = false

    var body: some View {
        #if canImport(GroupActivities)
        Button {
            if app.sharePlay.isActive {
                app.leaveSharePlay()
            } else if app.sharePlay.isEligibleForGroupSession {
                isStarting = true
                Task {
                    _ = await app.startSharePlayForCurrentBook()
                    isStarting = false
                }
            } else if let activity = app.sharePlay.makeSharingActivity() {
                sharingPresentation = SharePlaySharingPresentation(activity: activity)
            }
        } label: {
            Label {
                VStack(alignment: .leading, spacing: 3) {
                    Text(app.sharePlay.isActive ? "Leave SharePlay" : "SharePlay")
                    Text(app.sharePlay.statusText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            } icon: {
                Image(systemName: "shareplay")
            }
        }
        .disabled(isStarting || (!app.sharePlay.isActive && book == nil))
        .accessibilityIdentifier("shareplay-route-row")
        .sheet(item: $sharingPresentation) { presentation in
            SharePlaySharingControllerView(activity: presentation.activity) { result in
                app.sharePlay.handleSharingResult(result)
                sharingPresentation = nil
            }
            .interactiveDismissDisabled()
        }
        #else
        RouteCapabilityRow(
            title: "SharePlay",
            subtitle: "Apple group listening will appear here when enabled",
            systemImage: "shareplay",
            isEnabled: false
        )
        #endif
    }
}

#if canImport(GroupActivities)
struct AudiobookListeningActivity: GroupActivity {
    var message: AudiobookSharePlayMessage

    init(message: AudiobookSharePlayMessage) {
        self.message = message
    }

    var metadata: GroupActivityMetadata {
        var metadata = GroupActivityMetadata()
        metadata.title = message.bookTitle
        metadata.type = .generic
        return metadata
    }
}

private struct SharePlaySharingPresentation: Identifiable {
    let id = UUID()
    var activity: AudiobookListeningActivity
}

private struct SharePlaySharingControllerView: UIViewControllerRepresentable {
    var activity: AudiobookListeningActivity
    var onResult: @MainActor (GroupActivitySharingResult) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onResult: onResult)
    }

    func makeUIViewController(context: Context) -> GroupActivitySharingController {
        let controller = GroupActivitySharingController {
            activity
        }
        context.coordinator.observeResult(from: controller)
        return controller
    }

    func updateUIViewController(_ uiViewController: GroupActivitySharingController, context: Context) {}

    static func dismantleUIViewController(
        _ uiViewController: GroupActivitySharingController,
        coordinator: Coordinator
    ) {
        coordinator.resultTask?.cancel()
    }

    @MainActor
    final class Coordinator {
        var resultTask: Task<Void, Never>?
        private let onResult: @MainActor (GroupActivitySharingResult) -> Void

        init(onResult: @escaping @MainActor (GroupActivitySharingResult) -> Void) {
            self.onResult = onResult
        }

        func observeResult(from controller: GroupActivitySharingController) {
            resultTask = Task { @MainActor in
                let result = await controller.result
                guard !Task.isCancelled else { return }
                onResult(result)
            }
        }
    }
}
#endif

struct RouteCapabilityRow: View {
    var title: String
    var subtitle: String
    var systemImage: String
    var isEnabled: Bool

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        } icon: {
            Image(systemName: systemImage)
        }
        .foregroundStyle(isEnabled ? .primary : .secondary)
    }
}

#if canImport(GoogleCast)
private struct GoogleCastButton: UIViewRepresentable {
    func makeUIView(context: Context) -> GCKUICastButton {
        let button = GCKUICastButton(frame: .zero)
        button.tintColor = .label
        return button
    }

    func updateUIView(_ uiView: GCKUICastButton, context: Context) {}
}
#endif
