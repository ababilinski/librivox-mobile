import LibriVoxCore
import SwiftUI
import UIKit

@main
struct LibriVoxMobileApp: App {
    @UIApplicationDelegateAdaptor(LibriVoxMobileAppDelegate.self) private var appDelegate
    @State private var appModel = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appModel)
                .environment(\.libriVoxAccent, LibriVoxBrand.accent)
                .tint(.primary)
                .preferredColorScheme(appModel.settings.themeMode.colorScheme)
                .onOpenURL { url in
                    appModel.handleOpenURL(url)
                }
        }
    }
}

private final class LibriVoxMobileAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        GoogleCastBridge.configureIfAvailable()
        return true
    }
}

private extension AppThemeMode {
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}
