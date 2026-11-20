import SwiftUI

/// Search: the web's "Shows, movies and people" box with All / People / Movies / TV shows.
struct SearchPage: View {
    @EnvironmentObject var app: AppState
    @State private var query = ""
    @State private var scope = "all"
    @State private var movies: [Item] = []
    @State private var shows: [Item] = []
    @State private var people: [Person] = []
    @State private var searching = false
    @State private var searched = ""

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 44) {
                VStack(spacing: 26) {
                    HStack(spacing: 16) {
                        Image(systemName: "magnifyingglass").font(.system(size: 28)).foregroundStyle(Theme.textMuted)
                        TextField("Shows, movies and people", text: $query)
                            .font(Typo.geist(17))
                            .onSubmit { Task { await run() } }
                    }
                    .frame(width: 1000)
                    SegmentBar(options: [("all", "All", "square.grid.2x2"), ("people", "People", "person.2"), ("movies", "Movies", "film"), ("shows", "TV shows", "tv")], selection: $scope)
                }
                .frame(maxWidth: .infinity)
                .focusSection()

                if searching { LoadingView() }
                else if !searched.isEmpty {
                    let total = movies.count + shows.count + people.count
                    Text("\(total) results").font(Typo.callout).foregroundStyle(Theme.textMuted).padding(.horizontal, Theme.gutter)
                    if scope == "all" {
                        let top = Array((movies.prefix(3) + shows.prefix(2)))
                        if !top.isEmpty { Shelf(title: "Top matches") { ForEach(top) { WideCard(item: $0, width: 470) } } }
                    }
                    if scope == "all" || scope == "people", !people.isEmpty {
                        Shelf(title: "People") {
                            ForEach(people) { p in
                                PosterCard(name: p.name, year: nil, poster: p.profile, route: .person(id: p.pid?.value ?? "", name: p.name), width: 230)
                            }
                        }
                    }
                    if scope == "all" || scope == "movies", !movies.isEmpty {
                        Shelf(title: "Movies") { ForEach(movies) { WideCard(item: $0, width: 470) } }
                    }
                    if scope == "all" || scope == "shows", !shows.isEmpty {
                        Shelf(title: "TV shows") { ForEach(shows) { WideCard(item: $0, width: 470) } }
                    }
                    if total == 0 { EmptyCard(icon: "magnifyingglass", title: "No results", line: "Try another word.").padding(.horizontal, Theme.gutter) }
                }
            }
            .padding(.vertical, 60)
        }
        .scrollClipDisabled()
        .task {
            if app.demo && query.isEmpty { query = "alien"; await run() }
        }
    }

    func run() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        searching = true
        defer { searching = false; searched = q }
        let enc = q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? q
        let api = app.api
        async let m: MovieLookup? = try? api.get("/titles/lookup?term=\(enc)")
        async let s: ShowLookup? = try? api.get("/shows/lookup?term=\(enc)")
        async let p: PeopleSearch? = try? api.get("/people/search?q=\(enc)&page=1")
        movies = dedupe((await m)?.movies?.compactMap(\.item) ?? [])
        shows = dedupe((await s)?.shows?.compactMap(\.item) ?? [])
        people = Array(((await p)?.people ?? []).prefix(20))
    }

    func dedupe(_ items: [Item]) -> [Item] {
        var seen = Set<String>()
        return items.filter { seen.insert($0.id).inserted }
    }
}
