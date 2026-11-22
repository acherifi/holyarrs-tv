import AVFoundation
import SwiftUI

/// `/title/{kind}/{tmdb}`: backdrop hero with logo, tagline and actions,
/// then the facts line, overview, credits, seasons, cast and More like this.
struct TitlePage: View {
    @EnvironmentObject var app: AppState
    let kind: Kind
    let tmdb: String
    @State private var d: Details?
    @State private var extras: Extras?
    @State private var following = false
    @State private var error: String?
    @State private var adding = false
    @State private var managing = false
    @State private var busy = false
    @State private var season = 1
    @State private var episodes: [Episode] = []
    @FocusState private var heroFocus: String?
    /// Netflix-style trailer behind the hero: muted, plays while focus is in the hero.
    @State private var trailer: AVPlayer?
    @State private var trailerOn = false
    @State private var trailerEnded = false
    @State private var muted = true
    /// The page opens on the hero, but `heroFocus` stays nil until focus first moves.
    @State private var inHero = true
    @State private var inBackground = false
    /// Autoplay off: the Trailer button plays it full screen instead.
    @State private var fullTrailer: URL?
    @State private var loadingTrailer = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 50) {
                if let d {
                    hero(d)
                    facts(d)
                    if kind == .show, let seasons = d.seasons?.filter({ $0.number > 0 }), !seasons.isEmpty { seasonsBlock(seasons) }
                    cast(d)
                    if let similar = d.similar, !similar.isEmpty {
                        Shelf(title: "More like this") {
                            ForEach(Array(similar.enumerated()), id: \.offset) { _, it in WideCard(item: it, width: 470) }
                        }
                    }
                } else if let error {
                    ErrorCard(message: error) { Task { await load() } }
                } else { LoadingView().frame(height: 700) }
            }
            .padding(.bottom, 80)
        }
        .scrollClipDisabled()
        .ignoresSafeArea(edges: .top)
        .task { await load() }
        .sheet(isPresented: $adding, onDismiss: { Task { await load() } }) {
            AddSheet(kind: kind, tmdb: tmdb, name: d?.name ?? "")
        }
        .task(id: d?.trailerKey) { await startTrailer() }
        .onChange(of: heroFocus) { _, f in inHero = f != nil; syncTrailer() }
        .onChange(of: adding) { syncTrailer() }
        .onChange(of: managing) { syncTrailer() }
        .onChange(of: scenePhase) { _, s in inBackground = s == .background; syncTrailer() }
        .onDisappear { stopTrailer() }
        .onPlayPauseCommand { if trailerOn { muted.toggle(); trailer?.isMuted = muted } }
        .onReceive(NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)) { n in
            if let item = n.object as? AVPlayerItem, item === trailer?.currentItem { trailerEnded = true; trailerOn = false }
        }
        .fullScreenCover(isPresented: Binding(get: { fullTrailer != nil }, set: { if !$0 { fullTrailer = nil } })) {
            if let url = fullTrailer {
                TrailerPlayer(url: url)
                    .ignoresSafeArea()
                    .onReceive(NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)) { _ in fullTrailer = nil }
            }
        }
        .sheet(isPresented: $managing, onDismiss: { Task { await load() } }) {
            if let d, let id = d.title_id { ManageSheet(kind: kind, titleID: id, name: d.name) }
        }
    }

    func load() async {
        let api = app.api, kind = self.kind, tmdb = self.tmdb
        do {
            async let det: Details = api.get("/metadata/\(kind.rawValue)/\(tmdb)")
            async let ex: Extras? = try? api.get("/extras/\(kind.rawValue)/\(tmdb)")
            let details = try await det
            d = details
            extras = await ex
            if let id = details.title_id {
                following = ((try? await app.api.get("/titles/\(id)/follow", Follow.self))?.following) ?? false
            } else {
                following = ((try? await app.api.get("/metadata/\(kind.rawValue)/\(tmdb)/follow", Follow.self))?.following) ?? false
            }
            if kind == .show, episodes.isEmpty {
                season = details.seasons?.first(where: { $0.number > 0 })?.number ?? 1
                await loadEpisodes()
            }
            if app.startScreen == "add-sheet" && !adding && details.title_id == nil { adding = true }
        } catch { self.error = error.localizedDescription }
    }

    func loadEpisodes() async {
        if let id = d?.title_id, let e: Episodes = try? await app.api.get("/titles/\(id)/episodes?season=\(season)") {
            episodes = e.episodes
        } else if let e: Episodes = try? await app.api.get("/metadata/show/\(tmdb)/seasons/\(season)") {
            episodes = e.episodes.map { var x = $0; x.season = season; return x }
        } else { episodes = [] }
    }

    // MARK: Hero

    func hero(_ d: Details) -> some View {
        ZStack(alignment: .bottomLeading) {
            RemoteImage(raw: extras?.backgrounds?.first ?? d.backdrop, size: "w1280", name: d.name)
            if let trailer {
                TrailerVideo(player: trailer)
                    .opacity(trailerOn ? 1 : 0)
                    .animation(.easeInOut(duration: 0.8), value: trailerOn)
            }
            LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .center, endPoint: .bottom)
            LinearGradient(colors: [.black.opacity(0.6), .clear], startPoint: .leading, endPoint: .center)
            VStack(alignment: .leading, spacing: 22) {
                if let logo = extras?.logo ?? d.logo {
                    RemoteImage(raw: logo, showInitials: false, contentMode: .fit)
                        .frame(maxWidth: 780, maxHeight: 170, alignment: .bottomLeading)
                } else {
                    Text(d.name).font(Typo.geist(48, .bold)).foregroundStyle(.white)
                }
                if let t = d.tagline, !t.isEmpty {
                    Text(t).font(Typo.geist(18)).italic().foregroundStyle(.white.opacity(0.92)).frame(maxWidth: 820, alignment: .leading)
                }
                actions(d)
            }
            .padding(60)
        }
        .frame(height: 900)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .bottomTrailing) {
            if trailerOn {
                Label(muted ? "Play/Pause for sound" : "Sound on", systemImage: muted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                    .font(Typo.callout).foregroundStyle(.white.opacity(0.85))
                    .padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Capsule().fill(.black.opacity(0.45)))
                    .padding(40)
                    .transition(.opacity)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.rPanel * 1.4, style: .continuous))
        .padding(.horizontal, Theme.gutter)
        .padding(.top, 50)
    }

    @ViewBuilder func actions(_ d: Details) -> some View {
        let manage = app.me?.canManage ?? app.demo
        HStack(spacing: 20) {
            if d.in_library == true {
                // In the library: the play button takes the main spot.
                if app.player == .infuse {
                    Button { Task { await app.playInInfuse(kind: kind, tmdb: d.tmdb_id.value) } } label: { Label("Play in Infuse", systemImage: "play.fill") }
                        .buttonStyle(PillButtonStyle(kind: .primary)).focused($heroFocus, equals: "main")
                } else if let id = d.title_id, manage {
                    Button { Task { await app.openInJellyfin(titleID: id) } } label: { Label("Open in Jellyfin", systemImage: "play.fill") }
                        .buttonStyle(PillButtonStyle(kind: .primary)).focused($heroFocus, equals: "main")
                } else {
                    Button { } label: { Label("In library", systemImage: "checkmark") }
                        .buttonStyle(PillButtonStyle(kind: .primary)).focused($heroFocus, equals: "main")
                }
                if d.title_id != nil, manage {
                    Button { managing = true } label: { Label("Manage", systemImage: "slider.horizontal.3") }
                        .buttonStyle(PillButtonStyle(kind: .glass)).focused($heroFocus, equals: "manage")
                }
            } else if let ask = d.ask, ask.state != "turned_down" {
                Button { } label: { Label(ask.state_label ?? "Asked", systemImage: "clock") }
                    .buttonStyle(PillButtonStyle(kind: .primary)).focused($heroFocus, equals: "main")
            } else if d.never_add != true {
                Button { adding = true } label: {
                    Label(manage ? "Add to library" : "Request", systemImage: "plus")
                }
                .buttonStyle(PillButtonStyle(kind: .primary)).focused($heroFocus, equals: "main")
            }
            if !app.autoplayTrailers, !app.trailersURL.isEmpty, d.trailerKey != nil {
                Button { Task { await playTrailer() } } label: {
                    if loadingTrailer { ProgressView() } else { Label("Trailer", systemImage: "film") }
                }
                .buttonStyle(PillButtonStyle(kind: .glass)).disabled(loadingTrailer).focused($heroFocus, equals: "trailer")
            }
            Button { Task { await toggleFollow(d) } } label: {
                Label(following ? "Following" : "Follow", systemImage: following ? "bell.fill" : "bell")
            }
            .buttonStyle(PillButtonStyle(kind: .glass)).disabled(busy).focused($heroFocus, equals: "follow")
        }
        .focusSection()
        .defaultFocus($heroFocus, "main")
    }

    // MARK: Trailer

    /// Plays only while focus is on the hero's buttons, no sheet is open and the app
    /// is in front; leaving the hero pauses it and shows the backdrop again.
    /// Only @State here: the async task reads them live, while an @Environment value
    /// would stay what it was when the task started (inactive at launch).
    var trailerShouldPlay: Bool { inHero && !adding && !managing && !inBackground && !trailerEnded }

    func startTrailer() async {
        stopTrailer()
        guard app.autoplayTrailers, !app.trailersURL.isEmpty, let key = d?.trailerKey else { return }
        try? await Task.sleep(for: .seconds(2))  // let the page settle first, as Netflix does
        guard !Task.isCancelled,
              let url = await Trailers.resolve(base: app.trailersURL, token: app.trailersToken, key: key),
              !Task.isCancelled else { return }
        let p = AVPlayer(url: url)
        p.isMuted = true
        muted = true
        trailerEnded = false
        trailer = p
        guard trailerShouldPlay else { return }  // focus left while it downloaded: start when it's back
        p.play()
        // Fade in on the first frames, not before, so the backdrop never flashes black.
        for _ in 0..<100 where p.timeControlStatus != .playing {
            try? await Task.sleep(for: .milliseconds(200))
            if Task.isCancelled { return }
        }
        if p.timeControlStatus == .playing && trailerShouldPlay { trailerOn = true }
    }

    /// The Trailer button: waits for the trailers service (it may still be downloading).
    func playTrailer() async {
        guard let key = d?.trailerKey else { return }
        loadingTrailer = true
        defer { loadingTrailer = false }
        if let url = await Trailers.resolve(base: app.trailersURL, token: app.trailersToken, key: key) {
            fullTrailer = url
        } else {
            app.show("The trailer isn't available. Check Settings → Trailers.")
        }
    }

    func syncTrailer() {
        guard let p = trailer else { return }
        if trailerShouldPlay { p.play(); trailerOn = true } else { p.pause(); trailerOn = false }
    }

    func stopTrailer() {
        trailer?.pause()
        trailer = nil
        trailerOn = false
    }

    func toggleFollow(_ d: Details) async {
        busy = true
        defer { busy = false }
        let path = d.title_id.map { "/titles/\($0)/follow" } ?? "/metadata/\(kind.rawValue)/\(tmdb)/follow"
        do {
            if following { try await app.api.delete(path) } else { try await app.api.put(path) }
            following.toggle()
            app.show(following ? "Following \(d.name)." : "Stopped following \(d.name).")
        } catch { app.show(error.localizedDescription) }
    }

    // MARK: Facts

    func facts(_ d: Details) -> some View {
        HStack(alignment: .top, spacing: 80) {
            VStack(alignment: .leading, spacing: 22) {
                HStack(spacing: 14) {
                    ForEach(Array(factBits(d).enumerated()), id: \.offset) { i, bit in
                        if i > 0 { Text("·").foregroundStyle(Theme.textFaint) }
                        bit
                    }
                }
                .font(Typo.geist(15, .medium)).foregroundStyle(Theme.textMuted)
                .lineLimit(1).minimumScaleFactor(0.6)
                if let o = d.overview, !o.isEmpty { FocusableText(text: o, lines: 5).padding(.leading, -18) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .leading, spacing: 14) {
                let directors = (d.people ?? []).filter { $0.role == "director" || $0.role == "creator" }.map(\.name)
                let stars = (d.people ?? []).filter { $0.role == "actor" }.prefix(3).map(\.name)
                if !directors.isEmpty { credit(kind == .show ? "Created by" : "Directed by", directors.joined(separator: ", ")) }
                if !stars.isEmpty { credit("Starring", stars.joined(separator: ", ")) }
                if let g = d.genres, !g.isEmpty { credit("Genres", g.joined(separator: ", ")) }
                if let s = d.studios?.first { credit(kind == .show ? "Network" : "Studio", s) }
            }
            .frame(width: 560, alignment: .leading)
        }
        .padding(.horizontal, Theme.gutter)
        .focusSection()
    }

    func credit(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label).font(Typo.subhead).foregroundStyle(Theme.textMuted)
            Text(value).font(Typo.subhead).foregroundStyle(Theme.text).lineLimit(2)
        }
    }

    func factBits(_ d: Details) -> [AnyView] {
        var bits: [AnyView] = []
        if let y = d.year { bits.append(AnyView(Text(String(y)))) }
        if kind == .show, let n = d.seasons?.filter({ $0.number > 0 }).count, n > 0 { bits.append(AnyView(Text("\(n) season\(n == 1 ? "" : "s")"))) }
        if kind == .movie, let m = d.runtime_minutes, m > 0 { bits.append(AnyView(Text(m >= 60 ? "\(m / 60) hr \(m % 60) min" : "\(m) min"))) }
        if let r = d.rating, !r.isEmpty {
            bits.append(AnyView(Text(r).font(Typo.footnote).padding(.horizontal, 8).padding(.vertical, 2)
                .overlay(RoundedRectangle(cornerRadius: 5).strokeBorder(Theme.textMuted, lineWidth: 1.5))))
        }
        for s in extras?.scores ?? [] {
            switch s.source {
            case "imdb":
                bits.append(AnyView(HStack(spacing: 8) {
                    Text("IMDb").font(.custom("Geist-Bold", fixedSize: 18)).foregroundStyle(.black)
                        .padding(.horizontal, 6).padding(.vertical, 1).background(RoundedRectangle(cornerRadius: 4).fill(Color(UIColor(hex: 0xF5C518))))
                    Text(String(format: "%.1f", s.value)).foregroundStyle(Theme.text)
                }))
            case "rt_critics":
                bits.append(AnyView(HStack(spacing: 6) { Text("🍅"); Text("\(Int(s.value))%").foregroundStyle(Theme.text); Text("Critics") }))
            case "rt_audience":
                bits.append(AnyView(HStack(spacing: 6) { Text("🍿"); Text("\(Int(s.value))%").foregroundStyle(Theme.text); Text("Audience") }))
            case "metacritic":
                bits.append(AnyView(HStack(spacing: 6) { Text("\(Int(s.value))").foregroundStyle(Theme.text); Text("Metacritic") }))
            case "letterboxd":
                bits.append(AnyView(HStack(spacing: 6) { Text(String(format: "%.1f", s.value)).foregroundStyle(Theme.text); Text("Letterboxd") }))
            default: break
            }
        }
        return bits
    }

    // MARK: Seasons

    func seasonsBlock(_ seasons: [TMDBSeason]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 24) {
                Text("Episodes").font(Typo.title2).foregroundStyle(Theme.text)
                SegmentBar(options: seasons.map { ($0.number, "Season \($0.number)", String?.none) }, selection: $season)
            }
            .padding(.horizontal, Theme.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: Theme.shelfGap) {
                    ForEach(episodes, id: \.stableID) { ep in EpisodeCard(ep: ep, inLibrary: d?.in_library == true) }
                }
                .padding(.horizontal, Theme.gutter).padding(.vertical, 28)
            }
            .scrollClipDisabled()
            .focusSection()
        }
        .onChange(of: season) { _, _ in Task { await loadEpisodes() } }
    }

    // MARK: Cast

    @ViewBuilder func cast(_ d: Details) -> some View {
        let actors = (d.people ?? []).filter { $0.role == "actor" }
        if !actors.isEmpty {
            Shelf(title: "Cast") {
                ForEach(actors.prefix(20)) { p in
                    Button { if let id = p.pid?.value { app.open(.person(id: id, name: p.name)) } } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            RemoteImage(raw: p.profile, size: "w185", name: p.name)
                                .frame(width: 190, height: 250)
                                .clipShape(RoundedRectangle(cornerRadius: Theme.rCard, style: .continuous))
                                .modifier(FocusRing(radius: Theme.rCard))
                            Text(p.name).font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                            Text(p.character ?? "").font(Typo.footnote).foregroundStyle(Theme.textMuted).lineLimit(1)
                        }
                        .frame(width: 190, alignment: .leading)
                    }
                    .buttonStyle(CardButtonStyle())
                }
            }
        }
    }
}

struct EpisodeCard: View {
    let ep: Episode
    let inLibrary: Bool
    @State private var open = false
    var body: some View {
        Button { open = true } label: {
            VStack(alignment: .leading, spacing: 10) {
                RemoteImage(raw: ep.still, size: "w780", name: ep.name ?? "")
                    .frame(width: 440, height: 248)
                    .overlay(alignment: .topTrailing) {
                        if inLibrary { StatusDot(state: ep.have ? "have" : "missing").padding(12) }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: Theme.rCard, style: .continuous))
                    .modifier(FocusRing(radius: Theme.rCard))
                Text("\(ep.num). \(ep.name ?? "Episode \(ep.num)")").font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                Text([ep.air_day, ep.runtime.map { "\($0) min" }].compactMap { $0 }.joined(separator: " · "))
                    .font(Typo.footnote).foregroundStyle(Theme.textMuted)
            }
            .frame(width: 440, alignment: .leading)
        }
        .buttonStyle(CardButtonStyle())
        .sheet(isPresented: $open) {
            VStack(alignment: .leading, spacing: 20) {
                Text("S\(ep.season ?? 0)E\(ep.num) · \(ep.name ?? "")").font(Typo.title2)
                Text(ep.overview ?? "No summary yet.").font(Typo.body)
            }
            .padding(60).frame(maxWidth: 1100)
        }
    }
}

/// Owners and managers: remove a title from the library, keeping or deleting its files.
/// Focus starts on Cancel so a stray click removes nothing.
struct ManageSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let kind: Kind
    let titleID: Int
    let name: String
    @State private var sending = false
    @State private var error: String?
    @FocusState private var focus: String?

    var noun: String { kind == .show ? "series" : "movie" }

    var body: some View {
        VStack(alignment: .leading, spacing: 34) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Manage").font(Typo.title1).foregroundStyle(Theme.text)
                Text(name).font(Typo.body).foregroundStyle(Theme.textMuted)
            }
            Text("Remove this \(noun) from HolyArrs. Keep the files to leave it playable in Jellyfin, or delete them to free the space.")
                .font(Typo.body).foregroundStyle(Theme.textMuted)
            if let error { Text(error).font(Typo.callout).foregroundStyle(Theme.fail) }
            HStack(spacing: 20) {
                Button { Task { await remove(deleteFiles: false) } } label: { Label("Remove, keep files", systemImage: "minus.circle") }
                    .buttonStyle(PillButtonStyle(kind: .plain))
                Button { Task { await remove(deleteFiles: true) } } label: { Label("Remove and delete files", systemImage: "trash") }
                    .buttonStyle(PillButtonStyle(kind: .plain))
                Spacer()
                if sending { ProgressView() }
                Button("Cancel") { dismiss() }
                    .buttonStyle(PillButtonStyle(kind: .accent)).focused($focus, equals: "cancel")
            }
            .disabled(sending)
            .focusSection()
        }
        .padding(60)
        .frame(width: 1300)
        .background(RoundedRectangle(cornerRadius: Theme.rModal, style: .continuous).fill(Theme.surface1))
        .defaultFocus($focus, "cancel")
    }

    func remove(deleteFiles: Bool) async {
        sending = true
        defer { sending = false }
        do {
            try await app.api.delete("/titles/\(titleID)?delete_files=\(deleteFiles)")
            app.show(deleteFiles ? "Removed \(name) and deleted its files." : "Removed \(name) from your library.")
            app.libraryVersion += 1
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
