import LibriVoxCore
import SwiftUI

enum AppTab: String, CaseIterable, Identifiable {
    case home
    case library
    case discover
    case settings

    var id: String { rawValue }
}

struct RootView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        if !app.hasCompletedOnboarding {
            OnboardingView()
        } else {
            MainTabView()
        }
    }
}

struct MainTabView: View {
    @Environment(AppModel.self) private var app
    @State private var selection: AppTab = .home

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack {
                HomeView()
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(AppTab.home)

            NavigationStack {
                LibraryView()
            }
            .tabItem { Label("Library", systemImage: "books.vertical") }
            .tag(AppTab.library)

            NavigationStack {
                DiscoverView()
            }
            .tabItem { Label("Discover", systemImage: "magnifyingglass") }
            .tag(AppTab.discover)

            NavigationStack {
                SettingsView()
            }
            .tabItem { Label("Settings", systemImage: "gearshape") }
            .tag(AppTab.settings)
        }
        .safeAreaInset(edge: .bottom) {
            if app.defaultListeningBook != nil {
                MiniPlayerView()
                    .padding(.horizontal)
                    .padding(.bottom, 8)
            }
        }
        .sheet(isPresented: Bindable(app).showingNowPlaying) {
            NowPlayingView()
        }
        .onChange(of: app.settings) { _, _ in
            app.persistSettings()
        }
        .task {
            await app.refresh()
        }
    }
}
