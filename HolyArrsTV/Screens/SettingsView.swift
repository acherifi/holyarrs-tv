import SwiftUI

/// Connection settings: HolyArrs address + API key, and the Jellyfin user that
/// Swiftfin is signed in with (needed for its deep links).
struct SettingsView: View {
    @EnvironmentObject var app: AppState
    let firstRun: Bool
    @State private var server = ""
    @State private var key = ""
    @State private var status: String?
    @State private var testing = false
    @State private var jfURL = ""
    @State private var jfUser = ""
    @State private var jfPassword = ""
    @State private var jfStatus: String?
    /// The field whose editor is open.
    @State private var editing: SettingsField?
    @FocusState private var focus: SettingsField?

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 40) {
                HStack(spacing: 24) {
                    BrandMark().frame(width: 80, height: 80)
                    Text(firstRun ? "Welcome to HolyArrs" : "Settings").font(Typo.largeTitle).foregroundStyle(Theme.text)
                }
                panel("HolyArrs", "Your server's address and an API key (Settings → Sign-in → API keys in the web app, scopes read and write).") {
                    field(.address)
                    field(.apiKey)
                    HStack(spacing: 20) {
                        Button { Task { await connect() } } label: { testing ? AnyView(ProgressView()) : AnyView(Text("Connect")) }
                            .buttonStyle(PillButtonStyle(kind: .accent))
                        if let status { Text(status).font(Typo.callout).foregroundStyle(Theme.textMuted) }
                    }
                }
                if !firstRun {
                    panel("Playback", app.player == .infuse
                          ? "\"Play in Infuse\" starts the title in Infuse, from the share that has it (add your Jellyfin server in Infuse)."
                          : "\"Open in Jellyfin\" opens the title in Swiftfin. Sign in to Jellyfin below once.") {
                        SegmentBar(options: Player.allCases.map { ($0, $0.label, nil) }, selection: $app.player)
                    }
                    panel("Trailers", "Trailers play muted behind the title, Netflix-style, while you're on its buttons. Play/Pause turns the sound on. With autoplay off, a Trailer button plays it full screen instead. Needs the trailers service from the repo's trailers/ folder running next to HolyArrs.") {
                        field(.trailersAddress)
                        field(.trailersToken)
                        SegmentBar(options: [(true, "Autoplay on", nil), (false, "Autoplay off", nil)], selection: $app.autoplayTrailers)
                    }
                    panel("Jellyfin","\"Open in Jellyfin\" opens Swiftfin on this Apple TV. Sign in once with the same Jellyfin user you use in Swiftfin; only the user's id is kept.") {
                        field(.jellyfinAddress)
                        field(.jellyfinUser)
                        field(.jellyfinPassword)
                        HStack(spacing: 20) {
                            Button("Sign in to Jellyfin") { Task { await jellyfinSignIn() } }.buttonStyle(PillButtonStyle(kind: .accent))
                            Text(jfStatus ?? (app.jellyfinUserID.isEmpty ? "Not signed in" : "Signed in as \(app.jellyfinUserName)"))
                                .font(Typo.callout).foregroundStyle(Theme.textMuted)
                        }
                    }
                    if let me = app.me {
                        panel("Account", "Signed in to HolyArrs with an API key.") {
                            Text("\(me.name ?? "") · \((me.role ?? "").capitalized)").font(Typo.body).foregroundStyle(Theme.text)
                        }
                    }
                }
            }
            .frame(maxWidth: 1300, alignment: .leading)
            .padding(.horizontal, Theme.gutter).padding(.vertical, 70)
        }
        // Start on the address row, so a first run begins where the user has to type.
        .defaultFocus($focus, .address)
        .onAppear {
            server = app.server; key = app.apiKey; jfURL = app.jellyfinURL
            jfUser = app.jellyfinUserName
        }
        .fullScreenCover(item: $editing) { f in
            FieldEditor(field: f, text: binding(f))
        }
    }

    func binding(_ f: SettingsField) -> Binding<String> {
        switch f {
        case .address: $server
        case .apiKey: $key
        case .jellyfinAddress: $jfURL
        case .jellyfinUser: $jfUser
        case .jellyfinPassword: $jfPassword
        case .trailersAddress: $app.trailersURL
        case .trailersToken: $app.trailersToken
        }
    }

    func panel<C: View>(_ title: String, _ line: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 22) {
            Text(title).font(Typo.title2).foregroundStyle(Theme.text)
            Text(line).font(Typo.callout).foregroundStyle(Theme.textMuted)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(40)
        .background(RoundedRectangle(cornerRadius: Theme.rPanel).fill(Theme.surface1))
        .overlay(RoundedRectangle(cornerRadius: Theme.rPanel).strokeBorder(Theme.line, lineWidth: 2))
        .focusSection()
    }

    /// A full-width focusable row showing the field's value. Raw text fields are
    /// kept out of the focus chain: tvOS moves focus geometrically, and a field
    /// beside a label is easy to leave unreachable. Selecting the row opens an editor.
    func field(_ f: SettingsField) -> some View {
        Button { editing = f } label: {
            FieldRow(label: f.label, value: f.display(binding(f).wrappedValue), empty: binding(f).wrappedValue.isEmpty)
        }
        .buttonStyle(FieldRowButtonStyle())
        .focused($focus, equals: f)
    }

    func connect() async {
        testing = true
        defer { testing = false }
        let probe = API(server: server, key: key, demo: false)
        do {
            let me: Me = try await probe.get("/auth/me")
            app.server = server; app.apiKey = key
            app.reconnect()
            status = "Connected as \(me.name ?? "?")."
        } catch {
            status = error.localizedDescription
        }
    }

    struct AuthBody: Encodable { let Username: String; let Pw: String }
    struct AuthResult: Decodable { struct U: Decodable { let Id: String; let Name: String? }; let User: U }

    func jellyfinSignIn() async {
        var base = jfURL.trimmingCharacters(in: .whitespaces)
        while base.hasSuffix("/") { base.removeLast() }
        guard let url = URL(string: base + "/Users/AuthenticateByName") else { jfStatus = "Check the address."; return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("MediaBrowser Client=\"HolyArrs TV\", Device=\"Apple TV\", DeviceId=\"holyarrs-tv\", Version=\"0.1.0\"", forHTTPHeaderField: "Authorization")
        req.httpBody = try? JSONEncoder().encode(AuthBody(Username: jfUser, Pw: jfPassword))
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { jfStatus = "Jellyfin refused the sign-in."; return }
            let r = try JSONDecoder().decode(AuthResult.self, from: data)
            app.jellyfinURL = base
            app.jellyfinUserID = r.User.Id
            app.jellyfinUserName = r.User.Name ?? jfUser
            jfPassword = ""
            jfStatus = "Signed in as \(app.jellyfinUserName)."
        } catch { jfStatus = error.localizedDescription }
    }
}

// MARK: Fields

enum SettingsField: String, Identifiable {
    case address, apiKey, jellyfinAddress, jellyfinUser, jellyfinPassword, trailersAddress, trailersToken
    var id: String { rawValue }

    var label: String {
        switch self {
        case .address: "Address"
        case .apiKey: "API key"
        case .jellyfinAddress: "Jellyfin address"
        case .jellyfinUser: "User name"
        case .jellyfinPassword: "Password"
        case .trailersAddress: "Trailers address"
        case .trailersToken: "Token"
        }
    }
    var prompt: String {
        switch self {
        case .address: "http://192.168.1.10:6262"
        case .apiKey: "hak_…"
        case .jellyfinAddress: "https://jellyfin.example.com"
        case .jellyfinUser: "Jellyfin user name"
        case .jellyfinPassword: "Jellyfin password"
        case .trailersAddress: "http://192.168.1.10:8099"
        case .trailersToken: "Optional"
        }
    }
    var help: String {
        switch self {
        case .address: "The address of your HolyArrs server, with http:// or https:// and the port."
        case .apiKey: "Create one in the web app: Settings → Sign-in → API keys, scopes read and write."
        case .jellyfinAddress: "The address Swiftfin uses for your Jellyfin server."
        case .jellyfinUser: "The Jellyfin user you're signed in with in Swiftfin."
        case .jellyfinPassword: "Used once to sign in. Only the user's id is kept."
        case .trailersAddress: "The trailers service's address, with http:// and the port (8099 by default). Leave empty to turn trailers off."
        case .trailersToken: "Only if you set TRAILERS_TOKEN on the trailers service."
        }
    }
    var secure: Bool { self == .apiKey || self == .jellyfinPassword || self == .trailersToken }
    var isURL: Bool { self == .address || self == .jellyfinAddress || self == .trailersAddress }

    /// What the row shows: the value, a masked secret, or the placeholder.
    func display(_ value: String) -> String {
        if value.isEmpty { return prompt }
        switch self {
        case .apiKey, .trailersToken: return value.count > 8 ? "••••••••" + value.suffix(4) : "••••••••"
        case .jellyfinPassword: return "••••••••"
        default: return value
        }
    }
}

struct FieldRow: View {
    let label: String
    let value: String
    let empty: Bool
    @Environment(\.isFocused) private var focused

    var body: some View {
        HStack(spacing: 24) {
            Text(label).font(Typo.subhead)
                .foregroundStyle(focused ? Theme.text : Theme.textMuted)
                .frame(width: 260, alignment: .leading)
            Text(value).font(Typo.body)
                .foregroundStyle(empty ? Theme.textFaint : Theme.text)
                .lineLimit(1).truncationMode(.middle)
            Spacer(minLength: 0)
            Image(systemName: "pencil")
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(focused ? Theme.text : Theme.textFaint)
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity, minHeight: 80)
        .background(RoundedRectangle(cornerRadius: Theme.rBtn, style: .continuous)
            .fill(focused ? Theme.surface3 : Theme.surface2))
        .overlay(RoundedRectangle(cornerRadius: Theme.rBtn, style: .continuous)
            .strokeBorder(focused ? Color.white : Theme.line, lineWidth: focused ? 4 : 2))
    }
}

/// Lifts on focus like the other controls, without the system's tilt.
struct FieldRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { FieldRowBody(configuration: configuration) }
}

private struct FieldRowBody: View {
    let configuration: ButtonStyleConfiguration
    @Environment(\.isFocused) private var focused
    var body: some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.99 : (focused ? 1.02 : 1))
            .shadow(color: .black.opacity(focused ? 0.3 : 0), radius: 18, y: 8)
            .animation(.easeOut(duration: 0.15), value: focused)
    }
}

/// Full-screen editor for one field. The text field takes focus on appear, so one
/// click opens the keyboard. Menu or Cancel discards; Save or the keyboard's Done keeps it.
struct FieldEditor: View {
    let field: SettingsField
    @Binding var text: String
    @State private var draft = ""
    @FocusState private var inputFocused: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Theme.bg.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 36) {
                Text(field.label).font(Typo.largeTitle).foregroundStyle(Theme.text)
                Text(field.help).font(Typo.callout).foregroundStyle(Theme.textMuted)
                input
                    .font(Typo.body)
                    .focused($inputFocused)
                    .onSubmit(save)
                HStack(spacing: 24) {
                    Button("Save", action: save).buttonStyle(PillButtonStyle(kind: .accent))
                    Button("Cancel") { dismiss() }.buttonStyle(PillButtonStyle(kind: .plain))
                }
                .focusSection()
            }
            .frame(maxWidth: 1300, alignment: .leading)
            .padding(.horizontal, Theme.gutter)
        }
        .onAppear {
            draft = text
            inputFocused = true
        }
    }

    @ViewBuilder var input: some View {
        if field.secure {
            SecureField(field.prompt, text: $draft)
        } else {
            TextField(field.prompt, text: $draft)
                .textContentType(field.isURL ? .URL : .username)
                .keyboardType(field.isURL ? .URL : .default)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
    }

    func save() {
        text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        dismiss()
    }
}
