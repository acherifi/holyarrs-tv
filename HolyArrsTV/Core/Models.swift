import Foundation

/// A value the API sends either as a number or as a string (tmdb ids, person ids).
struct Flex: Codable, Hashable, CustomStringConvertible {
    let value: String
    init(_ v: String) { value = v }
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let s = try? c.decode(String.self) { value = s }
        else if let i = try? c.decode(Int.self) { value = String(i) }
        else if let d = try? c.decode(Double.self) { value = String(Int(d)) }
        else { value = "" }
    }
    func encode(to encoder: Encoder) throws { var c = encoder.singleValueContainer(); try c.encode(value) }
    var description: String { value }
}

enum Kind: String, Codable, Hashable {
    case movie, show, artist
    init(from decoder: Decoder) throws {
        let s = (try? decoder.singleValueContainer().decode(String.self)) ?? "movie"
        switch s { case "show", "tv", "series": self = .show; case "artist", "music": self = .artist; default: self = .movie }
    }
    var word: String { self == .movie ? "Movie" : self == .show ? "Show" : "Artist" }
}

// MARK: Account

struct Me: Codable {
    var id: Int?
    var name: String?
    var role: String?
    var perms: [String]?
    var canManage: Bool { perms?.contains("manage_library") ?? false || role == "owner" }
    var canAsk: Bool { perms?.contains("ask") ?? false }
}

// MARK: Discover

struct AskBrief: Codable, Hashable {
    var id: Int?
    var state: String?
    var state_label: String?
    var mine: Bool?
}

struct Fresh: Codable, Hashable { var kind: String?; var text: String?; var rank: Int? }

/// `ItemView`: one title card anywhere in Explore, New & Popular, Similar, search.
struct Item: Codable, Hashable, Identifiable {
    var tmdb_id: Flex
    var kind: Kind
    var name: String
    var year: Int?
    var poster: String?
    var backdrop: String?
    var overview: String?
    var genres: [String]?
    var in_library: Bool?
    var title_id: Int?
    var ask: AskBrief?
    var fresh: Fresh?
    var trailer_key: String?
    var card_art: String?
    var card_logo: String?
    var rank: Int?
    var what: String?
    var upcoming: Bool?
    var id: String { "\(kind.rawValue)-\(tmdb_id.value)" }
    var route: Route { .title(kind: kind, tmdb: tmdb_id.value) }
}

struct ExploreRow: Codable, Identifiable {
    var id: String
    var type: String?
    var title: String?
    var subtitle: String?
    var wide: Bool?
    var items: [Item]
    var ranked: Bool { type == "np_top_shows" || type == "np_top_movies" }
}

struct ExploreHead: Codable {
    var building: Bool?
    var hero: [Item]?
    var head: [ExploreRow]?
    var tail_rows: Int?
}

struct ExploreTail: Codable { var page: Int?; var more: Bool?; var rows: [ExploreRow]? }

struct RailDay: Codable { var day: String; var items: [Item] }

struct ExploreNew: Codable {
    var page: Int?
    var more: Bool?
    var building: Bool?
    var rows: [ExploreRow]?
    var hero: [Item]?
    var rail: [RailDay]?
}

// MARK: Home

struct HomeCard: Codable, Identifiable, Hashable {
    var key: String
    var kind: Kind
    var title_id: Int?
    var name: String
    var year: Int?
    var line: String?
    var backdrop: String?
    var poster: String?
    var logo: String?
    var season: Int?
    var at: String?
    var id: String { key }
}

struct LibraryCounts: Codable { var titles: Int?; var parts: Int?; var items: Int?; var on_disk: Int?; var missing: Int?; var downloading: Int?; var size: Int64? }

struct Home: Codable {
    var rows: [String]?
    var owner: Bool?
    var arrived: [HomeCard]?
    var new_seasons: [HomeCard]?
    var following: [HomeCard]?
    var soon: [HomeCard]?
    var library: [String: LibraryCounts]?
}

struct HealthItem: Codable { var name: String?; var state: String? }
struct Health: Codable { var ok: Bool?; var sources: [HealthItem]?; var downloaders: [HealthItem]? }

// MARK: Library

struct Slot: Codable, Hashable { var id: Int?; var state: String?; var state_label: String? }

struct SeasonInfo: Codable, Hashable {
    var number: Int
    var name: String?
    var episodes: Int?
    var aired: Int?
    var in_library: Int?
    var missing: Int?
}

struct ShowInfo: Codable, Hashable { var status: String?; var network: String?; var seasons: [SeasonInfo]? }

/// `TitleView`: a title in the Library.
struct LibraryTitle: Codable, Identifiable, Hashable {
    var id: Int
    var kind: Kind
    var name: String
    var year: Int?
    var overview: String?
    var ids: [String: Flex]?
    var artwork: [String]?
    var backdrop: String?
    var logo: String?
    var genres: [String]?
    var runtime_minutes: Int?
    var score: Double?
    var added_at: String?
    var size_bytes: Int64?
    var library_folder_id: Int?
    var pick_rules_name: String?
    var slots: [Slot]?
    var show: ShowInfo?
    var tmdb: String? { ids?["tmdb"]?.value }
    var poster: String? { artwork?.first }

    /// The web's card status: have, downloading, wanted (missing) or not out yet.
    var status: String {
        if kind == .show, let s = show?.seasons?.filter({ $0.number > 0 }) {
            let have = s.reduce(0) { $0 + ($1.in_library ?? 0) }
            let missing = s.reduce(0) { $0 + ($1.missing ?? 0) }
            if missing == 0 && have > 0 { return "have" }
            return have > 0 ? "partial" : "missing"
        }
        let states = (slots ?? []).compactMap(\.state)
        if states.contains(where: { ["in_library", "better_wanted", "no_hardlink"].contains($0) }) { return "have" }
        if states.contains("downloading") { return "downloading" }
        if states.contains("not_out_yet") { return "not_out_yet" }
        return "missing"
    }
}

struct TitlesGrid: Codable { var titles: [LibraryTitle] }

struct Episode: Codable, Identifiable, Hashable {
    var id: Int?
    var season: Int?
    var episode: Int?
    var number: Int?
    var name: String?
    var overview: String?
    var air_day: String?
    var runtime: Int?
    var still: String?
    var slots: [Slot]?
    var num: Int { episode ?? number ?? 0 }
    var have: Bool { (slots ?? []).contains { ["in_library", "better_wanted", "no_hardlink"].contains($0.state ?? "") } }
    var stableID: String { "\(season ?? 0)-\(num)" }
}
struct Episodes: Codable { var episodes: [Episode] }

// MARK: Title details

struct Person: Codable, Hashable, Identifiable {
    var name: String
    var role: String?
    var character: String?
    var profile: String?
    var pid: Flex?
    var department: String?
    var id: String { (pid?.value ?? "") + name + (character ?? "") }
    enum CodingKeys: String, CodingKey { case name, role, character, profile, pid = "id", department }
}

struct TMDBSeason: Codable, Hashable { var number: Int; var episode_count: Int?; var year: Int? }
struct Video: Codable, Hashable { var key: String; var name: String?; var type: String? }

struct Details: Codable {
    var tmdb_id: Flex
    var kind: Kind
    var name: String
    var year: Int?
    var poster: String?
    var backdrop: String?
    var overview: String?
    var genres: [String]?
    var logo: String?
    var runtime_minutes: Int?
    var episode_runtime: Int?
    var rating: String?
    var score: Double?
    var people: [Person]?
    var status: String?
    var tagline: String?
    var studios: [String]?
    var seasons: [TMDBSeason]?
    var similar: [Item]?
    var in_library: Bool?
    var title_id: Int?
    var ask: AskBrief?
    var never_add: Bool?
}

struct Score: Codable, Hashable { var source: String; var value: Double; var max: Double? }
struct Extras: Codable { var logo: String?; var backgrounds: [String]?; var scores: [Score]? }

struct Follow: Codable { var following: Bool? }

struct WatchLink: Codable { var server: String?; var kind: String?; var url: String }
struct WatchLinks: Codable { var links: [WatchLink]? }

// MARK: Adding

struct LibraryFolder: Codable, Identifiable, Hashable {
    var id: Int
    var path: String
    var kind: String
    var label: String?
    var default_pick_rules_id: Int?
    var health: String?
    var new_title_tags: [String]?
    var display: String { (label?.isEmpty == false ? label! : nil) ?? path }
}
struct LibraryFolders: Codable { var library_folders: [LibraryFolder] }

struct PickRule: Codable, Identifiable, Hashable { var id: Int; var name: String; var kinds: [String]? }
struct PickRules: Codable { var pick_rules: [PickRule] }

struct KindDefaults: Codable { var library_folder_id: Int?; var pick_rules_id: Int?; var keep_up_to_date: Bool?; var search_on_add: Bool? }
struct TitleDefaults: Codable { var movie: KindDefaults?; var show: KindDefaults? }

struct AddedTitle: Codable { var title: LibraryTitle? }

// MARK: Requests

struct NamedUser: Codable, Hashable { var id: Int?; var name: String?; var display_name: String? }
struct AskStep: Codable, Hashable { var kind: String?; var text: String? }

struct Ask: Codable, Identifiable, Hashable {
    var id: Int
    var kind: Kind
    var tmdb_id: Flex?
    var name: String
    var year: Int?
    var poster: String?
    var state: String
    var state_label: String?
    var season_words: String?
    var title_id: Int?
    var asked_by: NamedUser?
    var asked_at: String?
    var note: String?
    var step: AskStep?
    var problem: String?
    var adding: Bool?
}
struct Asks: Codable { var asks: [Ask] }
struct AskResult: Codable { var ask: Ask? }

// MARK: Calendar

struct CalendarRow: Codable, Identifiable, Hashable {
    var title_id: Int?
    var name: String
    var year: Int?
    var kind: String
    var date: String
    var tmdb: String?
    var season: Int?
    var episode: Int?
    var episode_name: String?
    var network: String?
    var poster: String?
    var backdrop: String?
    var still: String?
    var banner: String?
    var logo: String?
    var id: String { "\(title_id ?? 0)-\(kind)-\(date)-\(season ?? 0)-\(episode ?? 0)" }
    var day: String { String(date.prefix(10)) }
}
struct CalendarResponse: Codable { var dates: [CalendarRow]? }

// MARK: Downloads

struct DownloadRow: Codable, Identifiable, Hashable {
    var rowID: Flex?
    var state: String?
    var label: String?
    var progress: Double?
    var speed: Double?
    var eta: Int?
    var phase: String?
    var name: String?
    var title: String?
    var year: Int?
    var size: Int64?
    var downloader: String?
    var title_id: Int?
    var hash: String?
    var id: String { (hash ?? "") + (name ?? "") + (rowID?.value ?? "") }
    enum CodingKeys: String, CodingKey { case rowID = "id", state, label, progress, speed, eta, phase, name, title, year, size, downloader, title_id, hash }
}
struct DownloadTotals: Codable { var speed: Double?; var up_speed: Double? }
struct DownloadsView: Codable { var downloads: [DownloadRow]?; var totals: DownloadTotals? }

// MARK: Search & people

struct LookupMovie: Codable, Hashable {
    var name: String
    var year: Int?
    var overview: String?
    var ids: [String: Flex]?
    var poster: String?
    var backdrop: String?
    var title_id: Int?
    var item: Item? {
        guard let t = ids?["tmdb"]?.value else { return nil }
        return Item(tmdb_id: Flex(t), kind: .movie, name: name, year: year, poster: poster, backdrop: backdrop, overview: overview, in_library: title_id != nil, title_id: title_id)
    }
}
struct LookupShow: Codable, Hashable {
    var name: String
    var year: Int?
    var overview: String?
    var ids: [String: Flex]?
    var poster: String?
    var backdrop: String?
    var title_id: Int?
    var item: Item? {
        guard let t = ids?["tmdb"]?.value else { return nil }
        return Item(tmdb_id: Flex(t), kind: .show, name: name, year: year, poster: poster, backdrop: backdrop, overview: overview, in_library: title_id != nil, title_id: title_id)
    }
}
struct MovieLookup: Codable { var movies: [LookupMovie]? }
struct ShowLookup: Codable { var shows: [LookupShow]? }
struct PeopleSearch: Codable { var people: [Person]? }

struct PersonInfo: Codable {
    var id: Flex?
    var name: String
    var department: String?
    var biography: String?
    var birthday: String?
    var birthplace: String?
    var profile: String?
}
struct PersonCredit: Codable, Hashable {
    var name: String
    var year: Int?
    var poster: String?
    var backdrop: String?
    var ids: [String: Flex]?
    var kind: Kind?
    var title_id: Int?
    var characters: [String]?
    var item: Item? {
        guard let t = ids?["tmdb"]?.value else { return nil }
        return Item(tmdb_id: Flex(t), kind: kind ?? .movie, name: name, year: year, poster: poster, backdrop: backdrop, in_library: title_id != nil, title_id: title_id)
    }
}
struct PersonPageData: Codable { var person: PersonInfo; var movies: [PersonCredit]? }

// MARK: Navigation

enum Route: Hashable {
    case title(kind: Kind, tmdb: String)
    case libraryTitle(id: Int)
    case person(id: String, name: String)
}
