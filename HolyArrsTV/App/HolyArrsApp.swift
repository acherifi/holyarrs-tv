import SwiftUI

@main
struct HolyArrsApp: App {
    @StateObject private var app = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .task {
                    await app.loadMe()
                    app.applyStartScreen()
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppState
    var body: some View {
        ZStack(alignment: .top) {
            Theme.bg.ignoresSafeArea()
            if app.configured { Shell() } else { SettingsView(firstRun: true) }
            if let toast = app.toast {
                Text(toast).font(Typo.headline).foregroundStyle(Theme.text)
                    .padding(.horizontal, 30).padding(.vertical, 18)
                    .background(Capsule().fill(Theme.surface1).shadow(color: .black.opacity(0.25), radius: 20, y: 8))
                    .padding(.top, 40)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.24), value: app.toast)
    }
}

extension AppState {
    /// `-screen <name>` opens a given screen at launch (used for simulator screenshots).
    func applyStartScreen() {
        guard let s = startScreen else { return }
        switch s {
        case "explore": section = .explore
        case "popular": section = .popular
        case "movies": section = .movies
        case "shows": section = .shows
        case "search": section = .search
        case "requests": section = .requests
        case "calendar": section = .calendar
        case "downloads": section = .downloads
        case "settings": section = .settings
        case "title-movie": section = .explore; open(.title(kind: .movie, tmdb: "1218925"))
        case "title-show": section = .shows; open(.title(kind: .show, tmdb: "424"))
        case "add-sheet": section = .explore; open(.title(kind: .movie, tmdb: "1218925"))
        case "person": section = .explore; open(.person(id: "182287", name: "John Barrowman"))
        default: section = .home
        }
    }
}
