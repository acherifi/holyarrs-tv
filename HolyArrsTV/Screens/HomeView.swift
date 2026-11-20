import SwiftUI

/// Home: greeting, the overview tiles and the rows the server orders.
struct HomeView: View {
    @EnvironmentObject var app: AppState
    @State private var home: Home?
    @State private var health: Health?
    @State private var asks: [Ask] = []
    @State private var error: String?

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 50) {
                header
                if let home { tiles(home); rows(home) }
                else if let error { ErrorCard(message: error) { Task { await load() } } }
                else { LoadingView() }
            }
            .padding(.vertical, 60)
        }
        .scrollClipDisabled()
        .task(id: app.libraryVersion) { await load() }
    }

    func load() async {
        let api = app.api
        do {
            async let h: Home = api.get("/home")
            async let hl: Health? = try? api.get("/health")
            async let a: Asks? = try? api.get("/asks?limit=200")
            home = try await h
            health = await hl
            asks = await a?.asks ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    var greeting: String {
        let h = Calendar.current.component(.hour, from: Date())
        let part = h < 5 ? "Good evening" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening"
        if let n = app.me?.name, !n.isEmpty { return "\(part), \(n.prefix(1).uppercased() + n.dropFirst())" }
        return part
    }

    var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 22) {
                Text(greeting).font(Typo.largeTitle).foregroundStyle(Theme.text)
                if let n = newCount, n > 0 {
                    Text("New today: \(n) titles.").font(Typo.callout).foregroundStyle(Theme.textMuted)
                }
            }
            Spacer()
            HStack(spacing: 16) {
                Button { app.section = .search } label: { Label("Search", systemImage: "magnifyingglass") }
                    .buttonStyle(PillButtonStyle(kind: .plain))
                Button { app.section = .explore } label: { Label("Explore", systemImage: "safari") }
                    .buttonStyle(PillButtonStyle(kind: .plain))
            }
        }
        .padding(.horizontal, Theme.gutter)
        .focusSection()
    }

    var newCount: Int? {
        guard let home else { return nil }
        let cutoff = Date().addingTimeInterval(-14 * 86400)
        let fresh = (home.arrived ?? []).filter { (parseDate($0.at) ?? .distantPast) > cutoff }.count
        return fresh + (home.new_seasons?.count ?? 0)
    }

    // MARK: Tiles

    func tiles(_ home: Home) -> some View {
        let lib = home.library ?? [:]
        let titles = lib.values.reduce(0) { $0 + ($1.titles ?? 0) }
        let size = lib.values.reduce(Int64(0)) { $0 + ($1.size ?? 0) }
        let movies = lib["movie"]?.titles ?? 0, shows = lib["show"]?.titles ?? 0, artists = lib["artist"]?.titles ?? 0
        let problems = (health?.sources ?? []).filter { !["ok", "enabled"].contains($0.state ?? "") }.count
            + (health?.downloaders ?? []).filter { $0.state != "ok" }.count
        let mine = asks.filter { $0.state != "available" }
        let turned = mine.filter { $0.state == "turned_down" }.count
        let week = (home.soon ?? []).filter { (parseDate($0.at) ?? .distantFuture) < Date().addingTimeInterval(7 * 86400) }
        let cols = [GridItem(.flexible(), spacing: 26), GridItem(.flexible(), spacing: 26), GridItem(.flexible(), spacing: 26), GridItem(.flexible(), spacing: 26)]

        return LazyVGrid(columns: cols, alignment: .leading, spacing: 26) {
            if let hero = home.arrived?.first {
                Button { app.open(hero.title_id.map { .libraryTitle(id: $0) } ?? .title(kind: hero.kind, tmdb: "")) } label: {
                    ZStack(alignment: .bottomLeading) {
                        RemoteImage(raw: hero.backdrop, size: "w780", name: hero.name)
                        LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .center, endPoint: .bottom)
                        VStack(alignment: .leading, spacing: 6) {
                            if let logo = hero.logo {
                                RemoteImage(raw: logo, showInitials: false, contentMode: .fit).frame(maxWidth: 260, maxHeight: 64, alignment: .leading)
                            } else { Text(hero.name).font(Typo.title2).foregroundStyle(.white) }
                            Text(hero.line ?? "").font(Typo.footnote).foregroundStyle(.white.opacity(0.9))
                        }
                        .padding(26)
                        Label("New today", systemImage: "sparkles").font(Typo.caption).foregroundStyle(.white)
                            .padding(.horizontal, 12).padding(.vertical, 6).background(Capsule().fill(.black.opacity(0.55)))
                            .padding(18).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    }
                    .frame(height: 232)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.rPanel, style: .continuous))
                    .modifier(FocusRing(radius: Theme.rPanel))
                }
                .buttonStyle(CardButtonStyle())
            }
            if app.me?.canManage ?? true {
                Tile(icon: "exclamationmark.triangle.fill", color: Theme.tileAmber, title: "Needs attention",
                     value: "\(problems)", valueColor: problems > 0 ? Theme.wait : Theme.text,
                     line: problems == 0 ? "Everything is working" : "\(problems) \(problems == 1 ? "source has" : "sources have") a problem") { app.section = .downloads }
            }
            Tile(icon: "checklist", color: Theme.tileOrange, title: "Your asks", value: "\(mine.count)",
                 line: turned > 0 ? "turned down" : "waiting or on the way") { app.section = .requests }
            Tile(icon: "calendar", color: Theme.tilePurple, title: "Calendar", value: nil,
                 line: "Air dates and releases", bigLine: "What's next") { app.section = .calendar }
            Tile(icon: "books.vertical.fill", color: Theme.tileIndigo, title: "Library", value: "\(titles)",
                 line: "\(movies) movies · \(shows) show\(shows == 1 ? "" : "s") · \(artists) artists") { app.section = .movies }
            Tile(icon: "archivebox.fill", color: Theme.tileCyan, title: "On disk", value: size.bytes, line: "Across your library folders") { app.section = .downloads }
            Tile(icon: "calendar.badge.clock", color: Theme.tileTeal, title: "Coming this week", value: "\(week.count)",
                 line: week.isEmpty ? "Popular releases landing this week" : week.prefix(2).map(\.name).joined(separator: ", ")) { app.section = .calendar }
            Tile(icon: "sparkles", color: Theme.tilePink, title: "New for you", value: "\(newCount ?? 0)", line: "arrived in the last two weeks") { app.section = .movies }
        }
        .padding(.horizontal, Theme.gutter)
        .focusSection()
    }

    // MARK: Rows

    @ViewBuilder func rows(_ home: Home) -> some View {
        let order = home.rows ?? ["arrived", "new_season", "following", "soon"]
        ForEach(order, id: \.self) { key in
            switch key {
            case "arrived": cardRow(arrivedTitle(home.arrived ?? []), "New in the last two weeks", home.arrived ?? [])
            case "new_season": cardRow("New seasons", nil, home.new_seasons ?? [])
            case "following": cardRow("Following", "Titles you follow", home.following ?? [])
            case "soon": cardRow("Coming soon", "The next 30 days", home.soon ?? [])
            default: EmptyView()
            }
        }
    }

    func arrivedTitle(_ cards: [HomeCard]) -> String {
        guard let newest = cards.compactMap({ parseDate($0.at) }).max() else { return "Recently added" }
        let age = Date().timeIntervalSince(newest) / 86400
        return age < 1 ? "New today" : age < 7 ? "New this week" : age < 14 ? "Just arrived" : "Recently added"
    }

    @ViewBuilder func cardRow(_ title: String, _ subtitle: String?, _ cards: [HomeCard]) -> some View {
        if !cards.isEmpty {
            Shelf(title: title, subtitle: subtitle) {
                ForEach(cards) { c in
                    let item = Item(tmdb_id: Flex(""), kind: c.kind, name: c.name, year: c.year, poster: c.poster, backdrop: c.backdrop,
                                    in_library: nil, title_id: c.title_id, card_art: c.backdrop, card_logo: c.logo)
                    HomeWideCard(card: c, item: item)
                }
            }
        }
    }
}

struct HomeWideCard: View {
    @EnvironmentObject var app: AppState
    let card: HomeCard
    let item: Item
    var body: some View {
        Button { if let id = card.title_id { app.open(.libraryTitle(id: id)) } } label: {
            VStack(alignment: .leading, spacing: 10) {
                ZStack(alignment: .bottomLeading) {
                    RemoteImage(raw: card.backdrop, size: "w780", name: card.name)
                    LinearGradient(colors: [.clear, .black.opacity(0.5)], startPoint: .center, endPoint: .bottom)
                    if let logo = card.logo {
                        RemoteImage(raw: logo, showInitials: false, contentMode: .fit)
                            .frame(maxWidth: 260, maxHeight: 84, alignment: .bottomLeading).padding(24)
                    } else {
                        Text(card.name).font(Typo.title2).foregroundStyle(.white).padding(24)
                    }
                }
                .frame(width: 560, height: 315)
                .clipShape(RoundedRectangle(cornerRadius: Theme.rCard, style: .continuous))
                .modifier(FocusRing(radius: Theme.rCard))
                VStack(alignment: .leading, spacing: 2) {
                    Text(card.name).font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                    Text([card.kind.word, card.line].compactMap { $0 }.joined(separator: " · "))
                        .font(Typo.footnote).foregroundStyle(Theme.textMuted).lineLimit(1)
                }
                .frame(width: 560, alignment: .leading)
            }
        }
        .buttonStyle(CardButtonStyle())
    }
}

/// One overview tile: coloured icon, title, chevron, big number, small line.
struct Tile: View {
    let icon: String
    let color: Color
    let title: String
    let value: String?
    var valueColor: Color = Theme.text
    let line: String
    var bigLine: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    Image(systemName: icon).font(.system(size: 20, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 10).fill(color))
                    Text(title).font(Typo.headline).foregroundStyle(Theme.text)
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 20, weight: .semibold)).foregroundStyle(Theme.textFaint)
                }
                Spacer(minLength: 0)
                if let value {
                    Text(value).font(Typo.geist(40, .bold)).foregroundStyle(valueColor).lineLimit(1).minimumScaleFactor(0.5)
                }
                if let bigLine { Text(bigLine).font(Typo.title2).foregroundStyle(Theme.text) }
                Text(line).font(Typo.footnote).foregroundStyle(Theme.textMuted).lineLimit(1)
            }
            .padding(26)
            .frame(maxWidth: .infinity, minHeight: 232, maxHeight: 232, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: Theme.rPanel, style: .continuous).fill(Theme.surface1))
            .overlay(RoundedRectangle(cornerRadius: Theme.rPanel, style: .continuous).strokeBorder(Theme.line, lineWidth: 2))
            .modifier(FocusRing(radius: Theme.rPanel))
        }
        .buttonStyle(CardButtonStyle())
    }
}

func parseDate(_ s: String?) -> Date? {
    guard let raw = s else { return nil }
    // The API sends nanoseconds, which ISO8601DateFormatter can't read: drop them.
    let s = raw.replacingOccurrences(of: #"\.\d+"#, with: "", options: .regularExpression)
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime]
    if let d = f.date(from: s) { return d }
    let day = DateFormatter()
    day.dateFormat = "yyyy-MM-dd"
    day.timeZone = .current
    return day.date(from: String(s.prefix(10)))
}
