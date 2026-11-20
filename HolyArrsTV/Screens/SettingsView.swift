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

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 40) {
                HStack(spacing: 24) {
                    BrandMark().frame(width: 80, height: 80)
                    Text(firstRun ? "Welcome to HolyArrs" : "Settings").font(Typo.largeTitle).foregroundStyle(Theme.text)
                }
                panel("HolyArrs", "Your server's address and an API key (Settings → Sign-in → API keys in the web app, scopes read and write).") {
                    field("Address", "http://192.168.1.10:6262", $server)
                    field("API key", "hak_…", $key, secure: true)
                    HStack(spacing: 20) {
                        Button { Task { await connect() } } label: { testing ? AnyView(ProgressView()) : AnyView(Text("Connect")) }
                            .buttonStyle(PillButtonStyle(kind: .accent))
                        if let status { Text(status).font(Typo.callout).foregroundStyle(Theme.textMuted) }
                    }
                }
                if !firstRun {
                    panel("Jellyfin", "\"Open in Jellyfin\" opens Swiftfin on this Apple TV. Sign in once with the same Jellyfin user you use in Swiftfin; only the user's id is kept.") {
                        field("Jellyfin address", "https://jellyfin.example.com", $jfURL)
                        field("User name", "", $jfUser)
                        field("Password", "", $jfPassword, secure: true)
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
        .onAppear {
            server = app.server; key = app.apiKey; jfURL = app.jellyfinURL
            jfUser = app.jellyfinUserName
        }
    }

    func panel<C: View>(_ title: String, _ line: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 22) {
            Text(title).font(Typo.title2).foregroundStyle(Theme.text)
            Text(line).font(Typo.callout).foregroundStyle(Theme.textMuted)
            content()
        }
        .padding(40)
        .background(RoundedRectangle(cornerRadius: Theme.rPanel).fill(Theme.surface1))
        .overlay(RoundedRectangle(cornerRadius: Theme.rPanel).strokeBorder(Theme.line, lineWidth: 2))
        .focusSection()
    }

    func field(_ label: String, _ prompt: String, _ text: Binding<String>, secure: Bool = false) -> some View {
        HStack(spacing: 24) {
            Text(label).font(Typo.subhead).foregroundStyle(Theme.textMuted).frame(width: 260, alignment: .leading)
            if secure { SecureField(prompt, text: text).font(Typo.body) }
            else { TextField(prompt, text: text).font(Typo.body).textContentType(.URL).autocorrectionDisabled() }
        }
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
