import SwiftUI

enum Section: String, CaseIterable, Identifiable {
    case home, explore, popular, movies, shows, calendar, requests, downloads, search, settings
    var id: String { rawValue }
    var label: String {
        switch self {
        case .home: "Home"
        case .explore: "Explore"
        case .popular: "New & Popular"
        case .movies: "Movies"
        case .shows: "TV shows"
        case .calendar: "Calendar"
        case .requests: "Requests"
        case .downloads: "Downloads"
        case .search: "Search"
        case .settings: "Settings"
        }
    }
    var icon: String {
        switch self {
        case .home: "house"
        case .explore: "safari"
        case .popular: "sparkles"
        case .movies: "film"
        case .shows: "tv"
        case .calendar: "calendar"
        case .requests: "checklist"
        case .downloads: "arrow.down.circle"
        case .search: "magnifyingglass"
        case .settings: "gearshape"
        }
    }
}

/// Everything the app shares: where HolyArrs is, who is signed in, Jellyfin hand-off.
@MainActor
final class AppState: ObservableObject {
    @AppStorage("server") var server = ""
    @AppStorage("apiKey") var apiKey = ""
    @AppStorage("jellyfinURL") var jellyfinURL = ""
    @AppStorage("jellyfinUserID") var jellyfinUserID = ""
    @AppStorage("jellyfinUserName") var jellyfinUserName = ""
    /// Which app the title page's play button hands off to.
    @AppStorage("player") var player: Player = .jellyfin
    /// The trailers sidecar (trailers/ in the repo). Empty: no trailers.
    @AppStorage("trailersURL") var trailersURL = ""
    @AppStorage("trailersToken") var trailersToken = ""
    @AppStorage("autoplayTrailers") var autoplayTrailers = true

    @Published var section: Section = .home
    @Published var paths: [Section: NavigationPath] = [:]
    @Published var me: Me?
    @Published var api: API
    @Published var toast: String?
    /// Bumped after an add, ask or follow so lists reload.
    @Published var libraryVersion = 0

    let demo: Bool
    let startScreen: String?

    init() {
        let args = UserDefaults.standard
        demo = args.bool(forKey: "demo")
        startScreen = args.string(forKey: "screen")
        let d = UserDefaults.standard
        api = API(server: d.string(forKey: "server") ?? "", key: d.string(forKey: "apiKey") ?? "", demo: args.bool(forKey: "demo"))
    }

    var configured: Bool { api.configured }

    func reconnect() {
        api = API(server: server, key: apiKey, demo: demo)
        me = nil
        paths = [:]
        Task { await loadMe() }
    }

    func loadMe() async {
        guard configured else { return }
        me = try? await api.get("/auth/me", Me.self)
    }

    func path(_ s: Section) -> Binding<NavigationPath> {
        Binding(get: { self.paths[s] ?? NavigationPath() }, set: { self.paths[s] = $0 })
    }

    func open(_ route: Route) {
        var p = paths[section] ?? NavigationPath()
        p.append(route)
        paths[section] = p
    }

    func show(_ message: String) {
        toast = message
        Task {
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if toast == message { toast = nil }
        }
    }

    // MARK: Jellyfin hand-off (Swiftfin deep link)

    /// Opens the title in Swiftfin: swiftfin://<serverId>/<userId>/item/<itemId>.
    /// HolyArrs gives the item and server ids (the web app's "Open in Jellyfin" link).
    func openInJellyfin(titleID: Int) async {
        do {
            let links: WatchLinks = try await api.get("/media-servers/watch-links?title=\(titleID)")
            guard let link = links.links?.first(where: { $0.kind == "jellyfin" }) ?? links.links?.first else {
                show("Jellyfin doesn't have this title yet.")
                return
            }
            let parts = JellyfinLink.parse(link.url)
            guard let item = parts.itemID, let serverID = parts.serverID else {
                show("Couldn't read the Jellyfin link.")
                return
            }
            if jellyfinURL.isEmpty, let web = parts.webBase { jellyfinURL = web }
            guard !jellyfinUserID.isEmpty else {
                show("Sign in to Jellyfin once in Settings so the app knows your Jellyfin user.")
                return
            }
            let deep = URL(string: "swiftfin://\(serverID)/\(jellyfinUserID)/item/\(item)")!
            if demo { show("Would open \(deep.absoluteString)"); return }
            let ok = await UIApplication.shared.open(deep)
            if !ok { show("Install Swiftfin from the App Store to play in Jellyfin.") }
        } catch {
            show(error.localizedDescription)
        }
    }

    // MARK: Infuse hand-off

    /// Starts playback in Infuse through its TMDB library link:
    /// infuse://movie/<tmdb>?play or infuse://series/<tmdb>?play (Infuse 8.4.7+).
    /// Infuse plays the title from whichever share has it (e.g. your Jellyfin server).
    func playInInfuse(kind: Kind, tmdb: String) async {
        guard !tmdb.isEmpty, kind != .artist else { show("Infuse can't play this title."); return }
        let deep = URL(string: "infuse://\(kind == .show ? "series" : "movie")/\(tmdb)?play")!
        if demo { show("Would open \(deep.absoluteString)"); return }
        let ok = await UIApplication.shared.open(deep)
        if !ok { show("Install Infuse from the App Store to play there.") }
    }
}

/// The app the play button opens.
enum Player: String, CaseIterable {
    case jellyfin, infuse
    var label: String { self == .jellyfin ? "Jellyfin" : "Infuse" }
}

enum JellyfinLink {
    /// Reads `{web}/web/#/details?id=X&serverId=Y`.
    static func parse(_ s: String) -> (itemID: String?, serverID: String?, webBase: String?) {
        func value(_ key: String) -> String? {
            guard let r = s.range(of: key + "=") else { return nil }
            let rest = s[r.upperBound...]
            return String(rest.prefix { $0 != "&" && $0 != "#" })
        }
        let web = s.range(of: "/web/").map { String(s[..<$0.lowerBound]) }
        return (value("id"), value("serverId"), web)
    }
}
