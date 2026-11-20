import SwiftUI

/// The web's Add dialog (owners and managers) or Request dialog (everyone else).
/// Defaults follow lib/mediafiles/title-defaults.ts: saved title defaults first,
/// then the folder's own pick rules.
struct AddSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let kind: Kind
    let tmdb: String
    let name: String

    @State private var folders: [LibraryFolder] = []
    @State private var rules: [PickRule] = []
    @State private var folderID: Int?
    @State private var rulesID: Int?
    @State private var searchNow = false
    @State private var follow = true
    @State private var seasons = "all"
    @State private var loading = true
    @State private var sending = false
    @State private var error: String?

    var manage: Bool { app.me?.canManage ?? app.demo }
    var kindKey: String { kind == .movie ? "movie" : "show" }

    var body: some View {
        VStack(alignment: .leading, spacing: 34) {
            VStack(alignment: .leading, spacing: 8) {
                Text(manage ? "Add to library" : "Request").font(Typo.title1).foregroundStyle(Theme.text)
                Text(name).font(Typo.body).foregroundStyle(Theme.textMuted)
            }
            if loading { LoadingView() }
            else if manage { addForm } else { requestForm }
            if let error { Text(error).font(Typo.callout).foregroundStyle(Theme.fail) }
            HStack(spacing: 20) {
                Spacer()
                Button("Cancel") { dismiss() }.buttonStyle(PillButtonStyle(kind: .plain))
                Button {
                    Task { await submit() }
                } label: {
                    if sending { ProgressView() } else { Text(manage ? "Add" : "Send request") }
                }
                .buttonStyle(PillButtonStyle(kind: .accent))
                .disabled(sending || (manage && folderID == nil))
            }
            .focusSection()
        }
        .padding(60)
        .frame(width: 1300)
        .background(RoundedRectangle(cornerRadius: Theme.rModal, style: .continuous).fill(Theme.surface1))
        .task { await load() }
        .onChange(of: folderID) { _, new in
            // Picking another folder brings that folder's own pick rules, as on the web.
            if let f = folders.first(where: { $0.id == new }), let r = f.default_pick_rules_id, rules.contains(where: { $0.id == r }) { rulesID = r }
        }
    }

    var addForm: some View {
        VStack(alignment: .leading, spacing: 28) {
            choiceRow("Folder", folders.map { ($0.id, $0.display) }, $folderID)
            choiceRow("Pick rules", rules.map { ($0.id, $0.name) }, $rulesID)
            if kind == .show {
                choiceRow("Seasons", [("all", "All"), ("future", "Future"), ("latest", "Latest"), ("none", "None")], Binding(get: { Optional(seasons) }, set: { seasons = $0 ?? "all" }))
            }
            HStack(spacing: 20) {
                toggle("Search for missing now", $searchNow)
                toggle("Follow", $follow)
            }
            .focusSection()
            if let f = folders.first(where: { $0.id == folderID }), f.new_title_tags?.contains("tiramisu") == true {
                Label("Streams through Tiramisu: it appears in Jellyfin without downloading.", systemImage: "play.tv")
                    .font(Typo.callout).foregroundStyle(Theme.textMuted)
            }
        }
    }

    var requestForm: some View {
        VStack(alignment: .leading, spacing: 28) {
            Text(kind == .show ? "Ask for the whole show. The owner gets your request." : "The owner gets your request.")
                .font(Typo.body).foregroundStyle(Theme.textMuted)
            toggle("Follow", $follow)
        }
    }

    func choiceRow<T: Hashable>(_ label: String, _ options: [(T, String)], _ selection: Binding<T?>) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(label).font(Typo.subhead).foregroundStyle(Theme.textMuted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(options, id: \.0) { opt in
                        Button { selection.wrappedValue = opt.0 } label: {
                            HStack(spacing: 10) {
                                if selection.wrappedValue == opt.0 { Image(systemName: "checkmark") }
                                Text(opt.1)
                            }
                        }
                        .buttonStyle(PillButtonStyle(kind: selection.wrappedValue == opt.0 ? .primary : .plain))
                    }
                }
                .padding(.vertical, 14).padding(.horizontal, 6)
            }
            .scrollClipDisabled()
        }
        .focusSection()
    }

    func toggle(_ label: String, _ value: Binding<Bool>) -> some View {
        Button { value.wrappedValue.toggle() } label: {
            Label(label, systemImage: value.wrappedValue ? "checkmark.square.fill" : "square")
        }
        .buttonStyle(PillButtonStyle(kind: .plain))
    }

    func load() async {
        defer { loading = false }
        guard manage else { return }
        do {
            async let f: LibraryFolders = app.api.get("/library-folders")
            async let r: PickRules = app.api.get("/pick-rules")
            async let td: TitleDefaults? = try? app.api.get("/settings/title-defaults")
            folders = try await f.library_folders.filter { $0.kind == kindKey }
            let allRules = try await r.pick_rules
            rules = allRules.filter { ($0.kinds ?? []).isEmpty || $0.kinds!.contains(kindKey) }
            let defaults = kind == .movie ? (await td)?.movie : (await td)?.show
            folderID = folders.first(where: { $0.id == defaults?.library_folder_id })?.id
                ?? folders.first(where: { $0.health == "ok" })?.id ?? folders.first?.id
            pickRulesFor(defaults)
            searchNow = defaults?.search_on_add ?? (kind == .movie)
        } catch { self.error = error.localizedDescription }
    }

    func pickRulesFor(_ defaults: KindDefaults?) {
        if let saved = defaults?.pick_rules_id, saved != 0, rules.contains(where: { $0.id == saved }) { rulesID = saved; return }
        if let f = folders.first(where: { $0.id == folderID }), let r = f.default_pick_rules_id, rules.contains(where: { $0.id == r }) { rulesID = r; return }
        rulesID = rules.first?.id
    }

    struct AddMovieBody: Encodable {
        let id: String; let follow: Bool; let library_folder_id: Int; let pick_rules_id: Int?; let search_on_add: Bool; let keep_up_to_date: Bool
    }
    struct AddShowBody: Encodable {
        let id: String; let follow: Bool; let library_folder_id: Int; let pick_rules_id: Int?; let search_on_add: Bool; let keep_up_to_date: Bool; let seasons: String
    }
    struct AskMovieBody: Encodable { let id: String; let follow: Bool }
    struct AskShowBody: Encodable { let kind = "show"; let id: String; let seasons: [Int] = []; let follow: Bool }

    func submit() async {
        sending = true
        defer { sending = false }
        do {
            let id = "tmdb:\(tmdb)"
            if manage {
                guard let folderID else { return }
                if kind == .movie {
                    let _: AddedTitle = try await app.api.post("/titles", AddMovieBody(id: id, follow: follow, library_folder_id: folderID, pick_rules_id: rulesID, search_on_add: searchNow, keep_up_to_date: true))
                } else {
                    let _: AddedTitle = try await app.api.post("/shows", AddShowBody(id: id, follow: follow, library_folder_id: folderID, pick_rules_id: rulesID, search_on_add: searchNow, keep_up_to_date: true, seasons: seasons))
                }
                app.show("Added \(name) to your library.")
            } else {
                if kind == .movie {
                    let _: AskResult = try await app.api.post("/asks", AskMovieBody(id: id, follow: follow))
                } else {
                    let _: AskResult = try await app.api.post("/asks", AskShowBody(id: id, follow: follow))
                }
                app.show("Request sent for \(name).")
            }
            app.libraryVersion += 1
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
