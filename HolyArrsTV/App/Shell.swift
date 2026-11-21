import SwiftUI

/// The web app's frame: a sidebar on the left (collapsed to an icon rail while
/// you browse, expanded with labels when focus moves into it) and the page.
struct Shell: View {
    @EnvironmentObject var app: AppState
    @FocusState private var sidebarFocus: Section?
    /// The sidebar becomes focusable only after the page has taken focus, so
    /// tvOS (which starts at the top-leading focusable view) lands in the page.
    @State private var sidebarReady = false
    @Namespace private var ns
    @Environment(\.resetFocus) private var resetFocus
    static let rail: CGFloat = 128

    private var sidebarOpen: Bool { sidebarFocus != nil }

    var body: some View {
        // The page always sits right of the collapsed rail; the open sidebar
        // slides over it (like the web's overlay sidebar) instead of pushing it.
        ZStack(alignment: .leading) {
            ZStack {
                ForEach(Section.allCases) { s in
                    if s == app.section { page(s).transition(.opacity) }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.leading, Shell.rail)
            .focusSection()
            .prefersDefaultFocus(true, in: ns)

            sidebar
                .frame(width: sidebarOpen ? 400 : Shell.rail)
                .shadow(color: .black.opacity(sidebarOpen ? 0.35 : 0), radius: 40, x: 10)
                .focusSection()
                // Right always closes the sidebar, even when nothing focusable is level
                // with the focused item; Menu closes it instead of leaving the app.
                .onMoveCommand { if $0 == .right && sidebarOpen { closeSidebar() } }
                .onExitCommand(perform: sidebarOpen ? { closeSidebar() } : nil)
                // Entering the sidebar starts on the current section, not on the item
                // that happens to be level with where focus was in the page.
                .onChange(of: sidebarFocus) { old, new in
                    if old == nil, let new, new != app.section { sidebarFocus = app.section }
                }
        }
        .focusScope(ns)
        .animation(.easeOut(duration: 0.24), value: sidebarOpen)
        .ignoresSafeArea()
        .task {
            try? await Task.sleep(nanoseconds: 800_000_000)
            sidebarReady = true
        }
    }

    /// Sends focus back to the page: the page is the preferred default focus in `ns`,
    /// so re-evaluating it moves focus out of the sidebar.
    func closeSidebar() {
        sidebarFocus = nil
        resetFocus(in: ns)
    }

    @ViewBuilder func page(_ s: Section) -> some View {
        NavigationStack(path: app.path(s)) {
            screen(s)
                .navigationDestination(for: Route.self) { route in
                    Group {
                        switch route {
                        case let .title(kind, tmdb): TitlePage(kind: kind, tmdb: tmdb)
                        case let .libraryTitle(id): LibraryTitleResolver(id: id)
                        case let .person(id, name): PersonPage(personID: id, name: name)
                        }
                    }
                }
        }
        // No Menu handler here: on a pushed page the NavigationStack goes back, and on
        // a section's first page Menu leaves the app (Apple's convention). Left opens
        // the sidebar; Menu while it is open closes it (see the sidebar's onExitCommand).
    }

    @ViewBuilder func screen(_ s: Section) -> some View {
        switch s {
        case .home: HomeView()
        case .explore: ExploreView(mode: .explore)
        case .popular: ExploreView(mode: .popular)
        case .movies: LibraryView(kind: .movie)
        case .shows: LibraryView(kind: .show)
        case .calendar: CalendarView()
        case .requests: RequestsView()
        case .downloads: DownloadsPage()
        case .search: SearchPage()
        case .settings: SettingsView(firstRun: false)
        }
    }

    // MARK: Sidebar

    private var groups: [(String?, [Section])] {
        [(nil, [.search, .home, .explore, .popular]),
         ("Library", [.movies, .shows]),
         ("Your stuff", [.calendar, .requests]),
         ("Activity", [.downloads]),
         ("System", [.settings])]
    }

    var sidebar: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 16) {
                BrandMark().frame(width: 52, height: 52)
                if sidebarOpen { Text("HolyArrs").font(Typo.geist(19, .bold)).foregroundStyle(Theme.text) }
            }
            .padding(.leading, 38).padding(.top, 44).padding(.bottom, 18)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                        if let label = group.0 {
                            Text(label).font(Typo.footnote).foregroundStyle(Theme.textMuted)
                                .opacity(sidebarOpen ? 1 : 0)
                                .padding(.leading, 36).padding(.top, 14).padding(.bottom, 2)
                        }
                        ForEach(group.1) { s in item(s) }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
            .scrollClipDisabled()
            Spacer(minLength: 0)
            if let me = app.me {
                HStack(spacing: 16) {
                    Text(String((me.name ?? "?").prefix(1)).uppercased())
                        .font(Typo.headline).foregroundStyle(.white)
                        .frame(width: 56, height: 56).background(Circle().fill(Color(UIColor(hex: 0x3E5B2C))))
                    if sidebarOpen {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(me.name ?? "").font(Typo.headline).foregroundStyle(Theme.text)
                            Text((me.role ?? "").capitalized).font(Typo.footnote).foregroundStyle(Theme.textMuted)
                        }
                    }
                }
                .padding(.leading, 36).padding(.bottom, 36).padding(.top, 10)
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Theme.sidebarBG.ignoresSafeArea())
        .clipped()
        .overlay(alignment: .trailing) { Rectangle().fill(Theme.line).frame(width: 1).ignoresSafeArea() }
    }

    func item(_ s: Section) -> some View {
        Button {
            if app.section == s { app.paths[s] = NavigationPath() }
            app.section = s
            closeSidebar()
        } label: {
            SidebarRow(section: s, selected: app.section == s, open: sidebarOpen)
        }
        .buttonStyle(SidebarButtonStyle())
        .focused($sidebarFocus, equals: s)
        .disabled(!sidebarReady)
    }
}

struct SidebarRow: View {
    let section: Section
    let selected: Bool
    let open: Bool
    @Environment(\.isFocused) private var focused
    var body: some View {
        HStack(spacing: 22) {
            Image(systemName: section.icon)
                .font(.system(size: 26, weight: .medium))
                .foregroundStyle(selected ? Theme.accent : (focused ? Theme.text : Theme.textMuted))
                .frame(width: 34)
            if open {
                Text(section.label).font(Typo.geist(15, .semibold)).foregroundStyle(Theme.text).lineLimit(1)
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 22)
        .frame(height: 56)
        .frame(maxWidth: open ? .infinity : 96, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Theme.rBtn, style: .continuous)
            .fill(focused ? Theme.surface3 : (selected ? Theme.surface2 : .clear)))
        .scaleEffect(focused ? 1.03 : 1)
        .animation(.easeOut(duration: 0.15), value: focused)
    }
}

struct SidebarButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
    }
}

/// HolyArrs' diamond mark (static/brand/mark-color.svg).
struct BrandMark: View {
    var body: some View {
        GeometryReader { g in
            let s = min(g.size.width, g.size.height)
            ZStack {
                Rectangle().fill(Color(UIColor(hex: 0x1C1D21)))
                Path { p in
                    p.move(to: CGPoint(x: s * 0.5, y: 0))
                    p.addCurve(to: CGPoint(x: s * 0.58, y: s * 0.41), control1: CGPoint(x: s * 0.42, y: s * 0.18), control2: CGPoint(x: s * 0.46, y: s * 0.32))
                    p.addCurve(to: CGPoint(x: s * 0.54, y: s), control1: CGPoint(x: s * 0.70, y: s * 0.50), control2: CGPoint(x: s * 0.66, y: s * 0.70))
                    p.addLine(to: CGPoint(x: s * 1.1, y: s))
                    p.addLine(to: CGPoint(x: s * 1.1, y: 0))
                    p.closeSubpath()
                }
                .fill(Color(UIColor(hex: 0xF23A2E)))
                Star().fill(.white).frame(width: s * 0.44, height: s * 0.44)
            }
            .frame(width: s, height: s)
            .clipShape(RoundedRectangle(cornerRadius: s * 0.06).rotation(.degrees(45)).scale(0.65 * 1.414 / 1.414))
        }
    }
}

struct Star: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: r.midX, y: r.midY), h = r.width / 2
        p.move(to: CGPoint(x: c.x, y: c.y - h))
        p.addQuadCurve(to: CGPoint(x: c.x + h, y: c.y), control: CGPoint(x: c.x + h * 0.2, y: c.y - h * 0.2))
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + h), control: CGPoint(x: c.x + h * 0.2, y: c.y + h * 0.2))
        p.addQuadCurve(to: CGPoint(x: c.x - h, y: c.y), control: CGPoint(x: c.x - h * 0.2, y: c.y + h * 0.2))
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - h), control: CGPoint(x: c.x - h * 0.2, y: c.y - h * 0.2))
        return p
    }
}
