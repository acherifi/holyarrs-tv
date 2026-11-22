import AVFoundation
import AVKit
import SwiftUI
import UIKit

/// Talks to the trailers sidecar (trailers/ in the repo): it turns the YouTube id
/// HolyArrs gives into an MP4 the app plays itself, so nothing opens YouTube.
enum Trailers {
    struct Answer: Decodable { var state: String; var url: String?; var error: String? }

    /// Waits for the sidecar to have the file (it downloads on first ask) and returns
    /// its URL, or nil if it fails, isn't set up, or takes over two minutes.
    /// Cancelling the calling task stops the wait.
    static func resolve(base raw: String, token: String, key: String) async -> URL? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if s.isEmpty { return nil }
        if !s.contains("://") { s = "http://" + s }
        guard let base = URL(string: s), let ask = URL(string: "/trailers/\(key)", relativeTo: base) else { return nil }
        var req = URLRequest(url: ask, timeoutInterval: 15)
        if !token.isEmpty { req.setValue(token, forHTTPHeaderField: "X-Api-Key") }
        for _ in 0..<40 {
            guard !Task.isCancelled,
                  let (data, _) = try? await URLSession.shared.data(for: req),
                  let st = try? JSONDecoder().decode(Answer.self, from: data) else { return nil }
            switch st.state {
            case "ready":
                guard let path = st.url, var c = URLComponents(url: base.appendingPathComponent(path), resolvingAgainstBaseURL: true) else { return nil }
                // AVPlayer can't send headers, so the token goes in the query.
                if !token.isEmpty { c.queryItems = [URLQueryItem(name: "token", value: token)] }
                return c.url
            case "pending":
                try? await Task.sleep(for: .seconds(3))
            default:
                return nil
            }
        }
        return nil
    }
}

/// Full-screen player with the system controls, for the Trailer button (autoplay off).
struct TrailerPlayer: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.player = AVPlayer(url: url)
        vc.player?.play()
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {}

    static func dismantleUIViewController(_ vc: AVPlayerViewController, coordinator: ()) {
        vc.player?.pause()
    }
}

/// An AVPlayerLayer that fills its frame like the backdrop image it covers.
struct TrailerVideo: UIViewRepresentable {
    let player: AVPlayer

    final class LayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }

    func makeUIView(context: Context) -> LayerView {
        let v = LayerView()
        v.playerLayer.videoGravity = .resizeAspectFill
        v.playerLayer.player = player
        v.isUserInteractionEnabled = false
        return v
    }

    func updateUIView(_ v: LayerView, context: Context) { v.playerLayer.player = player }
}
