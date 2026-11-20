import SwiftUI
import UIKit

/// Design tokens mirrored from HolyArrs' web app (lib/core/tokens.css).
/// The TV is read from a sofa, so the web's pixel sizes are scaled by `Theme.scale`.
enum Theme {
    static let scale: CGFloat = 1.55

    private static func dyn(_ light: UInt32, _ dark: UInt32, _ la: CGFloat = 1, _ da: CGFloat = 1) -> Color {
        Color(UIColor { tc in
            tc.userInterfaceStyle == .dark ? UIColor(hex: dark, alpha: da) : UIColor(hex: light, alpha: la)
        })
    }

    static let bg = dyn(0xFFFFFF, 0x0B0B0C)
    static let sidebarBG = dyn(0xF6F6F7, 0x111113)
    static let surface1 = dyn(0xFFFFFF, 0x161618)
    static let surface2 = dyn(0xF2F2F4, 0x1E1E21)
    static let surface3 = dyn(0xE9E9EC, 0x2A2A2E)
    static let line = dyn(0x000000, 0xFFFFFF, 0.08, 0.09)
    static let lineStrong = dyn(0x000000, 0xFFFFFF, 0.16, 0.18)
    static let text = dyn(0x1D1D1F, 0xF5F5F7)
    static let textMuted = dyn(0x6E6E73, 0xA1A1A6)
    static let textFaint = dyn(0x8E8E93, 0x7C7C80)
    static let accent = dyn(0xE5352B, 0xFF453A)
    static let accentText = dyn(0xCF2C23, 0xFF453A)
    static let accentFill = Color(UIColor(hex: 0xD42D24))
    static let accentSoft = dyn(0xE5352B, 0xFF453A, 0.10, 0.16)
    static let ok = dyn(0x248A3D, 0x30D158)
    static let wait = dyn(0xB25000, 0xFFB340)
    static let fail = dyn(0xD70015, 0xFF6961)
    static let info = dyn(0x0071E3, 0x409CFF)
    static let segThumb = dyn(0xFFFFFF, 0x3A3A3E)

    static let markHave = Color(UIColor(hex: 0x1F9D48))
    static let markDownload = Color(UIColor(hex: 0x4F46E5))
    static let markAsk = Color(UIColor(hex: 0xF0A020))
    static let markNo = Color(UIColor(hex: 0xD92D20))

    // Tile icon colours on Home (attention, asks, library, disk, week, new, calendar).
    static let tileAmber = Color(UIColor(hex: 0xF5A524))
    static let tileOrange = Color(UIColor(hex: 0xF97316))
    static let tileIndigo = Color(UIColor(hex: 0x6366F1))
    static let tileTeal = Color(UIColor(hex: 0x14B8A6))
    static let tileCyan = Color(UIColor(hex: 0x22A6C3))
    static let tilePink = Color(UIColor(hex: 0xF43F5E))
    static let tilePurple = Color(UIColor(hex: 0xA855F7))

    // Shape (web px × scale).
    static let rSm: CGFloat = 6 * scale
    static let rBtn: CGFloat = 10 * scale
    static let rCard: CGFloat = 12 * scale
    static let rPanel: CGFloat = 16 * scale
    static let rModal: CGFloat = 20 * scale

    static let gutter: CGFloat = 64
    static let shelfGap: CGFloat = 16 * scale
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }
}

/// Geist, the web app's typeface, at the web type scale.
enum Typo {
    static func geist(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name: String
        switch weight {
        case .bold, .heavy, .black: name = "Geist-Bold"
        case .semibold: name = "Geist-SemiBold"
        case .medium: name = "Geist-Medium"
        default: name = "Geist-Regular"
        }
        return .custom(name, fixedSize: size * Theme.scale)
    }
    static let largeTitle = geist(34, .bold)
    static let title1 = geist(28, .bold)
    static let title2 = geist(22, .bold)
    static let title3 = geist(19, .semibold)
    static let headline = geist(16, .semibold)
    static let body = geist(15)
    static let callout = geist(14)
    static let subhead = geist(13, .medium)
    static let footnote = geist(12, .medium)
    static let caption = geist(11, .semibold)
}

/// The web app draws a gradient with initials where a picture is missing.
enum Placeholder {
    static let palettes: [(UInt32, UInt32)] = [
        (0x5B4A32, 0x3A3222), (0x2F5F63, 0x1F3540), (0x5A2E55, 0x3B1E33), (0x2C4258, 0x1F2A3D),
        (0x3E5B2C, 0x24391F), (0x5B3A2E, 0x3A2620), (0x46552F, 0x2C3A1E), (0x33365E, 0x241F44),
    ]
    static func colors(for name: String) -> [Color] {
        var h: UInt32 = 5381
        for u in name.unicodeScalars { h = (h &* 33) &+ u.value }
        let p = palettes[Int(h % UInt32(palettes.count))]
        return [Color(UIColor(hex: p.0)), Color(UIColor(hex: p.1))]
    }
    static func initials(_ name: String) -> String {
        let words = name.split { !$0.isLetter && !$0.isNumber }.prefix(2)
        return words.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}
