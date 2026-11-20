import SwiftUI

// MARK: Requests

/// Requests: Waiting / Processing / Done, like the web's tabs.
struct RequestsView: View {
    @EnvironmentObject var app: AppState
    @State private var asks: [Ask] = []
    @State private var tab = "waiting"
    @State private var loaded = false
    @State private var error: String?

    func inTab(_ a: Ask, _ t: String) -> Bool {
        switch t {
        case "waiting": return a.state == "asked"
        case "processing": return a.state == "approved"
        default: return a.state == "available" || a.state == "turned_down"
        }
    }

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 34) {
                Text("Requests").font(Typo.largeTitle).foregroundStyle(Theme.text)
                SegmentBar(options: [("waiting", "Waiting  \(asks.filter { inTab($0, "waiting") }.count)", nil),
                                     ("processing", "Processing  \(asks.filter { inTab($0, "processing") }.count)", nil),
                                     ("done", "Done  \(asks.filter { inTab($0, "done") }.count)", nil)], selection: $tab)
                let list = asks.filter { inTab($0, tab) }
                if let error { ErrorCard(message: error) { Task { await load() } } }
                else if !loaded { LoadingView() }
                else if list.isEmpty { EmptyCard(icon: "checkmark", title: "You're all caught up.", line: "New requests show up here.") }
                else {
                    VStack(spacing: 0) {
                        ForEach(list) { a in AskRow(ask: a) }
                    }
                    .background(RoundedRectangle(cornerRadius: Theme.rPanel).strokeBorder(Theme.line, lineWidth: 2))
                    .focusSection()
                }
            }
            .padding(.horizontal, Theme.gutter).padding(.vertical, 60)
        }
        .task(id: app.libraryVersion) { await load() }
    }

    func load() async {
        do { asks = try await app.api.get("/asks?limit=200", Asks.self).asks; error = nil }
        catch { self.error = error.localizedDescription }
        loaded = true
    }
}

struct AskRow: View {
    @EnvironmentObject var app: AppState
    let ask: Ask
    var body: some View {
        Button {
            if let t = ask.tmdb_id?.value { app.open(.title(kind: ask.kind, tmdb: t)) }
        } label: {
            HStack(spacing: 26) {
                RemoteImage(raw: ask.poster, size: "w185", name: ask.name)
                    .frame(width: 80, height: 120).clipShape(RoundedRectangle(cornerRadius: Theme.rSm))
                VStack(alignment: .leading, spacing: 6) {
                    Text([ask.name, ask.year.map { "(\($0))" }].compactMap { $0 }.joined(separator: " ")).font(Typo.headline).foregroundStyle(Theme.text)
                    Text([ask.kind.word, ask.season_words, ask.asked_by?.name.map { "asked by \($0)" }].compactMap { $0 }.joined(separator: " · "))
                        .font(Typo.footnote).foregroundStyle(Theme.textMuted)
                    if let step = ask.step?.text ?? ask.problem { Text(step).font(Typo.footnote).foregroundStyle(Theme.textMuted) }
                }
                Spacer()
                StateChip(state: ask.state, label: ask.state_label ?? ask.state)
            }
            .padding(.horizontal, 26).padding(.vertical, 18)
        }
        .buttonStyle(RowButtonStyle())
    }
}

struct StateChip: View {
    let state: String
    let label: String
    var color: Color {
        switch state {
        case "available", "done", "seeding": Theme.ok
        case "turned_down", "failed": Theme.fail
        case "approved", "downloading": Theme.info
        default: Theme.wait
        }
    }
    var body: some View {
        HStack(spacing: 10) {
            Circle().fill(color).frame(width: 12, height: 12)
            Text(label).font(Typo.subhead).foregroundStyle(Theme.text)
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
        .background(Capsule().fill(Theme.surface2))
    }
}

struct RowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { RowBody(configuration: configuration) }
}
private struct RowBody: View {
    let configuration: ButtonStyleConfiguration
    @Environment(\.isFocused) private var focused
    var body: some View {
        configuration.label
            .background(RoundedRectangle(cornerRadius: Theme.rCard).fill(focused ? Theme.surface2 : .clear))
            .overlay(alignment: .bottom) { Rectangle().fill(Theme.line).frame(height: 1) }
            .scaleEffect(focused ? 1.01 : 1)
            .animation(.easeOut(duration: 0.15), value: focused)
    }
}

// MARK: Calendar

/// Calendar: a week at a time, with All / Movies / TV shows and My follows / Whole Library.
struct CalendarView: View {
    @EnvironmentObject var app: AppState
    @State private var weekStart = CalendarView.monday(Date())
    @State private var type = "all"
    @State private var rows: [CalendarRow] = []
    @State private var loaded = false

    static func monday(_ d: Date) -> Date {
        var cal = Calendar(identifier: .iso8601); cal.timeZone = .current
        return cal.date(from: cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: d)) ?? d
    }
    static let day: DateFormatter = { let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f }()

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 34) {
                Text("Calendar").font(Typo.largeTitle).foregroundStyle(Theme.text)
                HStack(spacing: 22) {
                    Button { move(-7) } label: { Image(systemName: "chevron.left") }.buttonStyle(PillButtonStyle(kind: .plain, round: true))
                    Text(rangeLabel).font(Typo.title2).foregroundStyle(Theme.text)
                    Button { move(7) } label: { Image(systemName: "chevron.right") }.buttonStyle(PillButtonStyle(kind: .plain, round: true))
                    Spacer()
                    SegmentBar(options: [("all", "All", nil), ("movies", "Movies", nil), ("shows", "TV shows", nil)], selection: $type)
                }
                .focusSection()
                if !loaded { LoadingView() }
                else if rows.isEmpty {
                    EmptyCard(icon: "calendar", title: "Nothing you follow here", line: "No dates in the Library this week.")
                } else {
                    ForEach(days, id: \.self) { day in
                        let items = rows.filter { $0.day == day }
                        if !items.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(dayLabel(day)).font(Typo.title3).foregroundStyle(Theme.text)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack(spacing: Theme.shelfGap) { ForEach(items) { CalendarCard(row: $0) } }.padding(.vertical, 26)
                                }
                                .scrollClipDisabled()
                            }
                            .focusSection()
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.gutter).padding(.vertical, 60)
        }
        .task(id: "\(weekStart)\(type)") { await load() }
    }

    var days: [String] { (0..<7).map { Self.day.string(from: weekStart.addingTimeInterval(Double($0) * 86400 + 3600)) } }

    var rangeLabel: String {
        let f = DateFormatter(); f.dateFormat = "MMM d"
        let end = weekStart.addingTimeInterval(6 * 86400 + 3600)
        let g = DateFormatter(); g.dateFormat = Calendar.current.isDate(weekStart, equalTo: end, toGranularity: .month) ? "d" : "MMM d"
        return "\(f.string(from: weekStart)) – \(g.string(from: end))"
    }

    func dayLabel(_ s: String) -> String {
        guard let d = Self.day.date(from: s) else { return s }
        if Calendar.current.isDateInToday(d) { return "Today" }
        if Calendar.current.isDateInTomorrow(d) { return "Tomorrow" }
        let f = DateFormatter(); f.dateFormat = "EEEE d MMMM"; return f.string(from: d)
    }

    func move(_ by: Int) { weekStart = weekStart.addingTimeInterval(Double(by) * 86400) }

    func load() async {
        loaded = false
        let from = days.first!, to = days.last!
        let r: CalendarResponse? = try? await app.api.get("/calendar?from=\(from)&to=\(to)&titles=\(type)")
        rows = r?.dates ?? []
        if app.demo, rows.isEmpty == false {
            // Demo data is from a fixed week: show it on this week's days.
            rows = rows.enumerated().map { i, r in var x = r; x.date = days[i % 7]; return x }
        }
        loaded = true
    }
}

struct CalendarCard: View {
    @EnvironmentObject var app: AppState
    let row: CalendarRow
    var what: String {
        switch row.kind {
        case "episode": return "S\(row.season ?? 0)E\(row.episode ?? 0)" + (row.episode_name.map { " · \($0)" } ?? "")
        case "cinema": return "In cinemas"
        case "digital": return "Digital release"
        case "physical": return "Disc release"
        default: return row.kind.capitalized
        }
    }
    var body: some View {
        Button { if let id = row.title_id { app.open(.libraryTitle(id: id)) } } label: {
            VStack(alignment: .leading, spacing: 10) {
                ZStack(alignment: .bottomLeading) {
                    RemoteImage(raw: row.still ?? row.banner ?? row.backdrop, size: "w780", name: row.name)
                    LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
                    if let logo = row.logo {
                        RemoteImage(raw: logo, showInitials: false, contentMode: .fit).frame(maxWidth: 200, maxHeight: 70, alignment: .bottomLeading).padding(20)
                    }
                }
                .frame(width: 470, height: 264)
                .clipShape(RoundedRectangle(cornerRadius: Theme.rCard, style: .continuous))
                .modifier(FocusRing(radius: Theme.rCard))
                Text(row.name).font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                Text(what).font(Typo.footnote).foregroundStyle(Theme.textMuted).lineLimit(1)
            }
            .frame(width: 470, alignment: .leading)
        }
        .buttonStyle(CardButtonStyle())
    }
}

// MARK: Downloads

/// Downloads: the list with progress, status and size (read-only on the TV).
struct DownloadsPage: View {
    @EnvironmentObject var app: AppState
    @State private var rows: [DownloadRow] = []
    @State private var tab = "all"
    @State private var loaded = false
    @State private var error: String?

    func inTab(_ r: DownloadRow) -> Bool {
        switch tab {
        case "waiting": return r.phase == "waiting"
        case "progress": return ["downloading", "stalled", "paused", "importing", "waiting_to_import"].contains(r.phase ?? "")
        case "problems": return ["failed", "vanished", "needs_a_decision"].contains(r.phase ?? "")
        case "done": return r.phase == "done"
        default: return true
        }
    }

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 30) {
                Text("Downloads").font(Typo.largeTitle).foregroundStyle(Theme.text)
                SegmentBar(options: [("all", "All", nil), ("waiting", "Waiting", nil), ("progress", "In progress", nil), ("problems", "Problems", nil), ("done", "Done", nil)], selection: $tab)
                if let error { ErrorCard(message: error) { Task { await load() } } }
                else if !loaded { LoadingView() }
                else {
                    let list = rows.filter(inTab)
                    if list.isEmpty { EmptyCard(icon: "arrow.down.circle", title: "Nothing here", line: "Downloads show up here.") }
                    LazyVStack(spacing: 0) {
                        ForEach(list) { DownloadLine(row: $0) }
                    }
                    .focusSection()
                }
            }
            .padding(.horizontal, Theme.gutter).padding(.vertical, 60)
        }
        .task {
            await load()
            while !Task.isCancelled && !app.demo {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                await load()
            }
        }
    }

    func load() async {
        do { rows = try await app.api.get("/downloads/view?limit=100", DownloadsView.self).downloads ?? []; error = nil }
        catch { self.error = error.localizedDescription }
        loaded = true
    }
}

struct DownloadLine: View {
    @EnvironmentObject var app: AppState
    let row: DownloadRow
    var body: some View {
        Button { if let id = row.title_id { app.open(.libraryTitle(id: id)) } } label: {
            HStack(spacing: 22) {
                Text(Placeholder.initials(row.title?.isEmpty == false ? row.title! : (row.name ?? "")))
                    .font(Typo.footnote).foregroundStyle(.white.opacity(0.85))
                    .frame(width: 46, height: 64)
                    .background(LinearGradient(colors: Placeholder.colors(for: row.name ?? ""), startPoint: .top, endPoint: .bottom))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                VStack(alignment: .leading, spacing: 4) {
                    Text(row.title?.isEmpty == false ? "\(row.title!)\(row.year.map { " (\($0))" } ?? "")" : (row.name ?? ""))
                        .font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                    Text(row.name ?? "").font(Typo.footnote).foregroundStyle(Theme.textMuted).lineLimit(1)
                }
                Spacer()
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.surface3)
                    Capsule().fill(Theme.ok.opacity(0.75)).frame(width: 220 * CGFloat(min(max(row.progress ?? 0, 0), 1)))
                    Text("\(Int((row.progress ?? 0) * 100))%").font(Typo.caption).foregroundStyle(.white).padding(.leading, 12)
                }
                .frame(width: 220, height: 30)
                StateChip(state: row.state ?? "", label: row.label ?? (row.phase ?? "").capitalized).frame(width: 230, alignment: .leading)
                Text((row.size ?? 0).bytes).font(Typo.subhead).foregroundStyle(Theme.textMuted).frame(width: 140, alignment: .trailing)
            }
            .padding(.horizontal, 22).padding(.vertical, 14)
        }
        .buttonStyle(RowButtonStyle())
    }
}

// MARK: Person

struct PersonPage: View {
    @EnvironmentObject var app: AppState
    let personID: String
    let name: String
    @State private var page: PersonPageData?
    @State private var error: String?


    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 44) {
                if let p = page {
                    HStack(alignment: .top, spacing: 50) {
                        RemoteImage(raw: p.person.profile, size: "w300", name: p.person.name)
                            .frame(width: 300, height: 450).clipShape(RoundedRectangle(cornerRadius: Theme.rPanel))
                        VStack(alignment: .leading, spacing: 18) {
                            Text(p.person.name).font(Typo.largeTitle).foregroundStyle(Theme.text)
                            Text([p.person.department, p.person.birthday.map { "Born \($0)" }, p.person.birthplace].compactMap { $0 }.joined(separator: " · "))
                                .font(Typo.callout).foregroundStyle(Theme.textMuted)
                            if let bio = p.person.biography, !bio.isEmpty { FocusableText(text: bio, lines: 7).padding(.leading, -18) }
                        }
                    }
                    .padding(.horizontal, Theme.gutter)
                    .focusSection()
                    let credits = (p.movies ?? []).compactMap(\.item)
                    if !credits.isEmpty {
                        Text("Known for").font(Typo.title2).foregroundStyle(Theme.text).padding(.horizontal, Theme.gutter)
                        LazyVGrid(columns: Array(repeating: GridItem(.fixed(212), spacing: 34), count: 7), alignment: .leading, spacing: 50) {
                            ForEach(Array(credits.enumerated()), id: \.offset) { _, it in
                                PosterCard(name: it.name, year: it.year, poster: it.poster, route: it.route, width: 212, mark: it.in_library == true ? "have" : nil)
                            }
                        }
                        .padding(.horizontal, Theme.gutter)
                        .focusSection()
                    }
                } else if let error { ErrorCard(message: error) }
                else { LoadingView() }
            }
            .padding(.vertical, 60)
        }
        .scrollClipDisabled()
        .task {
            do { page = try await app.api.get("/people/\(personID)?role=all&sort=popular&page_size=40") }
            catch { self.error = error.localizedDescription }
        }
    }
}
