import SwiftUI

// MARK: Pictures

/// Loads a picture through HolyArrs (with the API key), falling back to the web's initials tile.
struct RemoteImage: View {
    @EnvironmentObject var app: AppState
    let raw: String?
    var size: String = "w500"
    var name: String = ""
    var showInitials = true
    var contentMode: ContentMode = .fill
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        let url = app.api.imageURL(raw, size: size)
        ZStack {
            if let image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
                    .transition(.opacity)
            } else if url == nil || failed {
                if showInitials { InitialsTile(name: name) } else { Color.clear }
            } else {
                Theme.surface2
            }
        }
        .task(id: url) {
            guard let url else { image = nil; return }
            if let hit = ImageStore.shared.cached(url) { image = hit; return }
            image = nil
            failed = false
            let img = await ImageStore.shared.load(app.api.imageRequest(url))
            withAnimation(.easeOut(duration: 0.25)) {
                image = img
                failed = img == nil
            }
        }
    }
}

struct InitialsTile: View {
    let name: String
    var body: some View {
        GeometryReader { g in
            ZStack {
                LinearGradient(colors: Placeholder.colors(for: name), startPoint: .top, endPoint: .bottom)
                Text(Placeholder.initials(name))
                    .font(.custom("Geist-Bold", fixedSize: min(g.size.width, g.size.height) * 0.3))
                    .foregroundStyle(.white.opacity(0.78))
            }
        }
    }
}

// MARK: Focus

/// Cards lift and glow on focus like the web's hover lift, without the system's
/// parallax tilt (which reacts to resting a thumb on the touch surface).
struct CardButtonStyle: ButtonStyle {
    var radius: CGFloat = Theme.rCard
    func makeBody(configuration: Configuration) -> some View {
        CardFocusBody(configuration: configuration, radius: radius)
    }
}

private struct CardFocusBody: View {
    let configuration: ButtonStyleConfiguration
    let radius: CGFloat
    @Environment(\.isFocused) private var focused
    var body: some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : (focused ? 1.06 : 1))
            .shadow(color: .black.opacity(focused ? 0.35 : 0), radius: focused ? 24 : 0, y: focused ? 14 : 0)
            .animation(.easeOut(duration: 0.18), value: focused)
            .animation(.easeOut(duration: 0.09), value: configuration.isPressed)
    }
}

/// Reads focus inside a card label so the picture can get the accent ring.
struct FocusRing: ViewModifier {
    @Environment(\.isFocused) private var focused
    var radius: CGFloat
    func body(content: Content) -> some View {
        content.overlay(
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .strokeBorder(focused ? Color.white : .clear, lineWidth: 5)
        )
    }
}

enum PillKind { case primary, glass, plain, accent, segment(selected: Bool) }

/// The web's rounded buttons: white "In library", glass "Open in Jellyfin", red primary, segment pills.
struct PillButtonStyle: ButtonStyle {
    var kind: PillKind = .plain
    var round = false
    func makeBody(configuration: Configuration) -> some View { PillBody(configuration: configuration, kind: kind, round: round) }
}

private struct PillBody: View {
    let configuration: ButtonStyleConfiguration
    let kind: PillKind
    let round: Bool
    @Environment(\.isFocused) private var focused

    var body: some View {
        configuration.label
            .font(Typo.geist(17, .semibold))
            .lineLimit(1)
            .padding(.horizontal, round ? 0 : 20 * Theme.scale)
            .frame(minWidth: round ? 44 * Theme.scale : nil, minHeight: 44 * Theme.scale)
            .foregroundStyle(fg)
            .background(Capsule().fill(bg))
            .overlay(Capsule().strokeBorder(border, lineWidth: 1.5))
            .scaleEffect(configuration.isPressed ? 0.95 : (focused ? 1.08 : 1))
            .shadow(color: .black.opacity(focused ? 0.3 : 0), radius: 14, y: 6)
            .animation(.easeOut(duration: 0.15), value: focused)
    }

    var fg: Color {
        if focused { return kind.isOnArt ? .black : .white }
        switch kind {
        case .primary: return .black
        case .glass: return .white
        case .accent: return .white
        case .plain: return Theme.text
        case .segment(let s): return s ? Theme.text : Theme.textMuted
        }
    }
    var bg: Color {
        if focused { return kind.isOnArt ? .white : Theme.accentFill }
        switch kind {
        case .primary: return .white
        case .glass: return .white.opacity(0.16)
        case .accent: return Theme.accentFill
        case .plain: return Theme.surface2
        case .segment(let s): return s ? Theme.segThumb : .clear
        }
    }
    var border: Color {
        switch kind {
        case .glass: return .white.opacity(focused ? 0 : 0.22)
        case .segment(let s): return s && !focused ? Theme.line : .clear
        default: return .clear
        }
    }
}

extension PillKind {
    var isOnArt: Bool { switch self { case .primary, .glass: true; default: false } }
}

// MARK: Shelf

/// A titled horizontal row. Each row is its own focus section so a vertical swipe
/// on the touch surface lands on the row below instead of skipping diagonally.
struct Shelf<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(Typo.title2).foregroundStyle(Theme.text)
                if let subtitle { Text(subtitle).font(Typo.callout).foregroundStyle(Theme.textMuted) }
            }
            .padding(.horizontal, Theme.gutter)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: Theme.shelfGap) { content() }
                    .padding(.horizontal, Theme.gutter)
                    .padding(.vertical, 28)
            }
            .scrollClipDisabled()
        }
        .focusSection()
    }
}

// MARK: Cards

/// Explore's wide card: backdrop, logo bottom-left, red "fresh" badge, name and "Movie · 2026" below.
struct WideCard: View {
    @EnvironmentObject var app: AppState
    let item: Item
    var width: CGFloat = 520
    var subtitle: String? = nil

    var body: some View {
        Button { app.open(item.route) } label: {
            VStack(alignment: .leading, spacing: 10) {
                ZStack(alignment: .bottomLeading) {
                    RemoteImage(raw: item.card_art ?? item.backdrop, size: "w780", name: item.name)
                        .frame(width: width, height: width * 9 / 16)
                    LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
                    if let logo = item.card_logo {
                        RemoteImage(raw: logo, size: "w500", showInitials: false, contentMode: .fit)
                            .frame(maxWidth: width * 0.45, maxHeight: width * 0.16, alignment: .bottomLeading)
                            .padding(22)
                    }
                    StatusMark(item: item).padding(14).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    if let text = item.fresh?.text ?? item.what {
                        Badge(text: text).padding(16).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    }
                }
                .frame(width: width, height: width * 9 / 16)
                .clipShape(RoundedRectangle(cornerRadius: Theme.rCard, style: .continuous))
                .modifier(FocusRing(radius: Theme.rCard))
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name).font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                    Text(subtitle ?? [item.kind.word, item.year.map(String.init)].compactMap { $0 }.joined(separator: " · "))
                        .font(Typo.footnote).foregroundStyle(Theme.textMuted).lineLimit(1)
                }
                .frame(width: width, alignment: .leading)
            }
        }
        .buttonStyle(CardButtonStyle())
    }
}

/// Library-style poster: 2:3 picture with name and year under it.
struct PosterCard: View {
    @EnvironmentObject var app: AppState
    let name: String
    let year: Int?
    let poster: String?
    let route: Route
    var width: CGFloat = 230
    var rank: Int? = nil
    var mark: String? = nil

    var body: some View {
        Button { app.open(route) } label: {
            HStack(alignment: .bottom, spacing: -width * 0.12) {
                if let rank {
                    Text("\(rank)")
                        .font(.custom("Geist-Bold", fixedSize: width * 0.78))
                        .foregroundStyle(Theme.surface3)
                        .offset(y: width * 0.14)
                        .zIndex(0)
                }
                VStack(alignment: .leading, spacing: 10) {
                    RemoteImage(raw: poster, size: "w342", name: name)
                        .frame(width: width, height: width * 1.5)
                        .overlay(alignment: .topTrailing) { if let mark { StatusDot(state: mark).padding(12) } }
                        .clipShape(RoundedRectangle(cornerRadius: Theme.rCard, style: .continuous))
                        .modifier(FocusRing(radius: Theme.rCard))
                    HStack(spacing: 8) {
                        Text(name).font(Typo.headline).foregroundStyle(Theme.text).lineLimit(1)
                        if let year { Text(String(year)).font(Typo.footnote).foregroundStyle(Theme.textMuted) }
                    }
                    .frame(width: width, alignment: .leading)
                }
                .zIndex(1)
            }
        }
        .buttonStyle(CardButtonStyle())
    }
}

struct Badge: View {
    let text: String
    var body: some View {
        Text(text).font(Typo.caption).foregroundStyle(.white)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(RoundedRectangle(cornerRadius: 4).fill(Theme.accentFill))
    }
}

/// The library status circle drawn on cards (have / downloading / asked / no).
struct StatusDot: View {
    let state: String
    var body: some View {
        let (color, icon): (Color, String) = {
            switch state {
            case "have": return (Theme.markHave, "checkmark")
            case "downloading": return (Theme.markDownload, "arrow.down")
            case "asked", "partial": return (Theme.markAsk, "clock")
            case "not_out_yet": return (Theme.markAsk, "calendar")
            default: return (Theme.markNo, "xmark")
            }
        }()
        Image(systemName: icon).font(.system(size: 18, weight: .bold)).foregroundStyle(.white)
            .frame(width: 40, height: 40).background(Circle().fill(color))
            .overlay(Circle().strokeBorder(.black.opacity(0.22), lineWidth: 1))
    }
}

struct StatusMark: View {
    let item: Item
    var body: some View {
        if item.in_library == true { StatusDot(state: "have") }
        else if let s = item.ask?.state, s == "asked" || s == "approved" { StatusDot(state: "asked") }
    }
}

// MARK: Page chrome

struct SegmentBar<T: Hashable>: View {
    let options: [(T, String, String?)]
    @Binding var selection: T
    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.0) { opt in
                Button { selection = opt.0 } label: {
                    HStack(spacing: 8) {
                        if let icon = opt.2 { Image(systemName: icon).font(.system(size: 20, weight: .medium)) }
                        Text(opt.1)
                    }
                }
                .buttonStyle(PillButtonStyle(kind: .segment(selected: selection == opt.0)))
            }
        }
        .padding(5)
        .background(Capsule().fill(Theme.surface2))
        .focusSection()
    }
}

struct LoadingView: View {
    var body: some View {
        ProgressView().progressViewStyle(.circular).tint(Theme.textMuted).frame(maxWidth: .infinity, minHeight: 300)
    }
}

/// The web's empty card: a round icon, a headline and one line under it.
struct EmptyCard: View {
    let icon: String
    let title: String
    let line: String
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 30, weight: .medium)).foregroundStyle(Theme.text)
                .frame(width: 80, height: 80).background(Circle().fill(Theme.surface2))
            Text(title).font(Typo.title3).foregroundStyle(Theme.text)
            Text(line).font(Typo.callout).foregroundStyle(Theme.textMuted).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 90)
        .background(RoundedRectangle(cornerRadius: Theme.rPanel).strokeBorder(Theme.line, lineWidth: 2))
    }
}

struct ErrorCard: View {
    let message: String
    var retry: (() -> Void)?
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "exclamationmark.triangle").font(.system(size: 34)).foregroundStyle(Theme.wait)
            Text(message).font(Typo.callout).foregroundStyle(Theme.textMuted).multilineTextAlignment(.center)
            if let retry { Button("Try again", action: retry).buttonStyle(PillButtonStyle(kind: .accent)) }
        }
        .frame(maxWidth: .infinity).padding(60)
    }
}

/// Wraps long text (overviews, biographies) so the focus engine can reach it:
/// on tvOS only focusable views scroll into view, so plain text below the last
/// button would be unreachable with the touch surface.
struct FocusableText: View {
    let text: String
    var lines = 4
    @State private var open = false
    var body: some View {
        Button { open = true } label: {
            Text(text).font(Typo.body).foregroundStyle(Theme.text).lineLimit(lines).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
        }
        .buttonStyle(TextBlockStyle())
        .sheet(isPresented: $open) {
            ScrollView { Text(text).font(Typo.body).padding(60) }.focusable()
        }
    }
}

struct TextBlockStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { TextBlockBody(configuration: configuration) }
}
private struct TextBlockBody: View {
    let configuration: ButtonStyleConfiguration
    @Environment(\.isFocused) private var focused
    var body: some View {
        configuration.label
            .background(RoundedRectangle(cornerRadius: Theme.rCard).fill(focused ? Theme.surface2 : .clear))
            .scaleEffect(focused ? 1.01 : 1)
            .animation(.easeOut(duration: 0.15), value: focused)
    }
}

extension Int64 {
    var bytes: String { ByteCountFormatter.string(fromByteCount: self, countStyle: .file) }
}
