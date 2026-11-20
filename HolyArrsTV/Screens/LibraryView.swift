import SwiftUI

/// Movies / TV shows: the Library poster wall with search, sort and the have/missing filter.
struct LibraryView: View {
    @EnvironmentObject var app: AppState
    let kind: Kind
    @State private var titles: [LibraryTitle] = []
    @State private var query = ""
    @State private var sort = "added"
    @State private var show = "all"
    @State private var error: String?
    @State private var loaded = false

    var shown: [LibraryTitle] {
        var list = titles.filter { $0.kind == kind }
        if !query.isEmpty { list = list.filter { $0.name.localizedCaseInsensitiveContains(query) } }
        switch show {
        case "have": list = list.filter { $0.status == "have" }
        case "missing": list = list.filter { $0.status != "have" }
        default: break
        }
        switch sort {
        case "name": list.sort { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        case "year": list.sort { ($0.year ?? 0) > ($1.year ?? 0) }
        case "size": list.sort { ($0.size_bytes ?? 0) > ($1.size_bytes ?? 0) }
        default: list.sort { ($0.added_at ?? "") > ($1.added_at ?? "") }
        }
        return list
    }

    var body: some View {
        let list = shown
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 30) {
                HStack(spacing: 20) {
                    HStack(spacing: 14) {
                        Image(systemName: "magnifyingglass").foregroundStyle(Theme.textMuted)
                        TextField("Search \(kind == .movie ? "movies" : "shows")", text: $query)
                            .font(Typo.body)
                        Text("\(list.count) \(kind == .movie ? "movies" : "shows")").font(Typo.footnote).foregroundStyle(Theme.textMuted)
                    }
                    .frame(maxWidth: 760)
                    Spacer()
                    SegmentBar(options: [("all", "All", nil), ("have", "Have", nil), ("missing", "Missing", nil)], selection: $show)
                    Menu {
                        Picker("Sort", selection: $sort) {
                            Text("Recently added").tag("added")
                            Text("Name").tag("name")
                            Text("Year").tag("year")
                            Text("Size").tag("size")
                        }
                    } label: { Label("Sort & filter", systemImage: "slider.horizontal.3") }
                    .buttonStyle(PillButtonStyle(kind: .plain))
                }
                .padding(.horizontal, Theme.gutter)
                .focusSection()

                if let error { ErrorCard(message: error) { Task { await load() } } }
                else if !loaded { LoadingView() }
                else if list.isEmpty {
                    EmptyCard(icon: kind == .movie ? "film" : "tv", title: "Nothing here yet",
                              line: "Add \(kind == .movie ? "movies" : "shows") from Explore or Search.").padding(.horizontal, Theme.gutter)
                } else {
                    LazyVGrid(columns: Array(repeating: GridItem(.fixed(212), spacing: 34), count: 7), alignment: .leading, spacing: 50) {
                        ForEach(list) { t in
                            PosterCard(name: t.name, year: t.year, poster: t.poster, route: .libraryTitle(id: t.id), width: 212,
                                       mark: t.status == "have" ? nil : t.status)
                        }
                    }
                    .padding(.horizontal, Theme.gutter)
                    .focusSection()
                }
            }
            .padding(.vertical, 50)
        }
        .scrollClipDisabled()
        .task(id: app.libraryVersion) { await load() }
    }

    func load() async {
        do {
            let g: TitlesGrid = try await app.api.get("/titles?grid=1")
            titles = g.titles
            error = nil
        } catch { self.error = error.localizedDescription }
        loaded = true
    }
}

/// `/library/{id}` pages: look the title up, then show the regular title page.
struct LibraryTitleResolver: View {
    @EnvironmentObject var app: AppState
    let id: Int
    @State private var title: LibraryTitle?
    @State private var error: String?
    var body: some View {
        Group {
            if let t = title, let tmdb = t.tmdb { TitlePage(kind: t.kind, tmdb: tmdb) }
            else if let error { ErrorCard(message: error) }
            else { LoadingView() }
        }
        .task {
            do { title = try await app.api.get("/titles/\(id)") }
            catch { self.error = error.localizedDescription }
        }
    }
}
