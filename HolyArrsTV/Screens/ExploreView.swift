import SwiftUI

/// Explore and New & Popular: the type switch, the hero and server-built rows.
struct ExploreView: View {
    enum Mode { case explore, popular }
    @EnvironmentObject var app: AppState
    let mode: Mode
    @State private var type = "all"
    @State private var hero: [Item] = []
    @State private var rows: [ExploreRow] = []
    @State private var rail: [RailDay] = []
    @State private var page = 0
    @State private var more = false
    @State private var loadingMore = false
    @State private var error: String?
    @State private var loaded = false

    var body: some View {
        ScrollView(.vertical) {
            LazyVStack(alignment: .leading, spacing: 56) {
                SegmentBar(options: [("all", "All", "square.grid.2x2"), ("movies", "Movies", "film"), ("shows", "TV shows", "tv")], selection: $type)
                    .frame(maxWidth: .infinity)
                if !hero.isEmpty { HeroCarousel(items: hero).padding(.horizontal, Theme.gutter) }
                if !rail.isEmpty { railShelf }
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in rowView(row) }
                if let error { ErrorCard(message: error) { Task { await reload() } } }
                else if !loaded { LoadingView() }
                if more {
                    // Reaching the end loads the next page, like the web's infinite scroll.
                    Color.clear.frame(height: 1).onAppear { Task { await loadMore() } }
                    LoadingView()
                }
            }
            .padding(.vertical, 50)
        }
        .scrollClipDisabled()
        .task(id: type) { await reload() }
    }

    @ViewBuilder func rowView(_ row: ExploreRow) -> some View {
        if !row.items.isEmpty {
            Shelf(title: row.title ?? "", subtitle: row.subtitle) {
                if row.ranked {
                    ForEach(Array(row.items.enumerated()), id: \.offset) { i, item in
                        PosterCard(name: item.name, year: nil, poster: item.poster, route: item.route, width: 230, rank: item.rank ?? i + 1)
                    }
                } else {
                    ForEach(Array(row.items.enumerated()), id: \.offset) { _, item in WideCard(item: item) }
                }
            }
        }
    }

    var railShelf: some View {
        Shelf(title: "This week") {
            let flat = rail.flatMap { d in d.items.map { (d.day, $0) } }
            ForEach(Array(flat.enumerated()), id: \.offset) { _, pair in
                WideCard(item: pair.1, width: 440, subtitle: [dayName(pair.0), pair.1.what].compactMap { $0 }.joined(separator: " · "))
            }
        }
    }

    func dayName(_ day: String) -> String? {
        guard let d = parseDate(day) else { return nil }
        let f = DateFormatter(); f.dateFormat = "EEE d MMM"
        return f.string(from: d)
    }

    func reload() async {
        loaded = false; error = nil; page = 0; more = false
        do {
            switch mode {
            case .explore:
                var head: ExploreHead = try await app.api.get("/explore/head?type=\(type)")
                var tries = 0
                // The server may still be building the rows: ask again like the web does.
                while head.building == true && (head.head ?? []).isEmpty && tries < 40 {
                    try await Task.sleep(nanoseconds: 3_000_000_000)
                    head = try await app.api.get("/explore/head?type=\(type)")
                    tries += 1
                }
                hero = head.hero ?? []
                rows = (head.head ?? []).filter { $0.type != "top_services" }
                rail = []
                more = true
            case .popular:
                let n: ExploreNew = try await app.api.get("/explore/new?type=\(type)&page=0")
                hero = n.hero ?? []
                rows = n.rows ?? []
                rail = n.rail ?? []
                more = n.more ?? false
            }
            loaded = true
        } catch { self.error = error.localizedDescription; loaded = true }
    }

    func loadMore() async {
        guard more, !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        do {
            switch mode {
            case .explore:
                let t: ExploreTail = try await app.api.get("/explore/tail?type=\(type)&page=\(page)")
                rows += (t.rows ?? []).filter { $0.type != "top_services" }
                more = t.more ?? false
            case .popular:
                let n: ExploreNew = try await app.api.get("/explore/new?type=\(type)&page=\(page + 1)")
                rows += n.rows ?? []
                more = (n.more ?? false) && page < 19
            }
            page += 1
            if app.demo { more = false }
        } catch { more = false }
    }
}

/// The big hero: backdrop, logo, "year · genres", overview, More info and +.
/// It turns every 9 s, but holds still while focus is inside it so a resting
/// thumb on the touch surface never fights the carousel.
struct HeroCarousel: View {
    @EnvironmentObject var app: AppState
    let items: [Item]
    @State private var index = 0
    @State private var logos: [String: String] = [:]
    @State private var adding: Item?
    @FocusState private var focus: Int?

    var body: some View {
        let item = items[min(index, items.count - 1)]
        ZStack(alignment: .bottomLeading) {
            RemoteImage(raw: item.backdrop, size: "w1280", name: item.name)
                .id(item.id)
                .transition(.opacity)
            LinearGradient(colors: [.black.opacity(0.0), .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)
            LinearGradient(colors: [.black.opacity(0.55), .clear], startPoint: .leading, endPoint: .center)
            VStack(alignment: .leading, spacing: 18) {
                if let logo = logos[item.id] ?? item.card_logo {
                    RemoteImage(raw: logo, showInitials: false, contentMode: .fit)
                        .frame(maxWidth: 720, maxHeight: 130, alignment: .bottomLeading)
                } else {
                    Text(item.name).font(Typo.geist(44, .bold)).foregroundStyle(.white)
                }
                Text(([item.year.map(String.init)] + (item.genres ?? []).prefix(3).map { Optional($0) }).compactMap { $0 }.joined(separator: " · "))
                    .font(Typo.geist(17, .semibold)).foregroundStyle(.white)
                if let o = item.overview {
                    Text(o).font(Typo.geist(15)).foregroundStyle(.white.opacity(0.9)).lineLimit(3).frame(maxWidth: 880, alignment: .leading)
                }
                HStack(spacing: 18) {
                    Button("More info") { app.open(item.route) }
                        .buttonStyle(PillButtonStyle(kind: .glass)).focused($focus, equals: 0)
                    Button { adding = item } label: { Image(systemName: item.in_library == true ? "checkmark" : "plus") }
                        .buttonStyle(PillButtonStyle(kind: .glass, round: true)).focused($focus, equals: 1)
                    Spacer()
                    HStack(spacing: 10) {
                        ForEach(items.indices, id: \.self) { i in
                            Button { withAnimation(.easeInOut(duration: 0.9)) { index = i } } label: {
                                Capsule().fill(i == index ? Color.white : .white.opacity(0.4)).frame(width: i == index ? 34 : 12, height: 12)
                            }
                            .buttonStyle(SidebarButtonStyle())
                            .focused($focus, equals: 10 + i)
                        }
                    }
                }
                .focusSection()
            }
            .padding(.horizontal, 56).padding(.top, 56).padding(.bottom, 64)
        }
        .frame(height: 800)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: Theme.rPanel * 1.4, style: .continuous))
        .task(id: item.id) { await loadLogo(item) }
        .task(id: focus == nil) {
            guard focus == nil, items.count > 1 else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 9_000_000_000)
                if Task.isCancelled || focus != nil { break }
                withAnimation(.easeInOut(duration: 0.9)) { index = (index + 1) % items.count }
            }
        }
        .onChange(of: focus) { _, f in if let f, f >= 10 { withAnimation(.easeInOut(duration: 0.9)) { index = f - 10 } } }
        .sheet(item: $adding) { it in AddSheet(kind: it.kind, tmdb: it.tmdb_id.value, name: it.name) }
    }

    func loadLogo(_ item: Item) async {
        guard logos[item.id] == nil, item.card_logo == nil else { return }
        if let d: Details = try? await app.api.get("/metadata/\(item.kind.rawValue)/\(item.tmdb_id.value)"), let l = d.logo {
            logos[item.id] = l
        }
    }
}
