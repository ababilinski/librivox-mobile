import LibriVoxCore
import SwiftUI

@main
struct LibriVoxMobileApp: App {
    @State private var appModel = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appModel)
                .preferredColorScheme(appModel.settings.themeMode.colorScheme)
                .onOpenURL { url in
                    appModel.handleOpenURL(url)
                }
        }
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
