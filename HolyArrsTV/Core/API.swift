import Foundation
import UIKit

struct APIError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Talks to HolyArrs' /api/v1 with an API key, the same calls the web app makes.
/// With `-demo YES` it answers from JSON bundled in the app (used for simulator screenshots).
final class API {
    let base: URL?
    let key: String
    let demo: Bool
    private let session: URLSession

    init(server: String, key: String, demo: Bool) {
        var s = server.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if !s.isEmpty && !s.contains("://") { s = "http://" + s }
        self.base = URL(string: s)
        self.key = key.trimmingCharacters(in: .whitespacesAndNewlines)
        self.demo = demo
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 30
        cfg.requestCachePolicy = .useProtocolCachePolicy
        self.session = URLSession(configuration: cfg)
    }

    var configured: Bool { demo || (base != nil && !key.isEmpty) }

    // MARK: Requests

    func get<T: Decodable>(_ path: String, _ type: T.Type = T.self) async throws -> T {
        try await send("GET", path, body: nil as String?)
    }

    func post<T: Decodable, B: Encodable>(_ path: String, _ body: B, _ type: T.Type = T.self) async throws -> T {
        try await send("POST", path, body: body)
    }

    func put(_ path: String) async throws { let _: Empty = try await send("PUT", path, body: nil as String?) }
    func delete(_ path: String) async throws { let _: Empty = try await send("DELETE", path, body: nil as String?) }

    struct Empty: Decodable { init(from decoder: Decoder) throws {} ; init() {} }

    private func send<T: Decodable, B: Encodable>(_ method: String, _ path: String, body: B?) async throws -> T {
        if demo { return try demoAnswer(method, path) }
        guard let base, let url = URL(string: "/api/v1" + path, relativeTo: base) else {
            throw APIError(message: "Set the HolyArrs address in Settings.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue(key, forHTTPHeaderField: "X-Api-Key")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(Locale.preferredLanguages.first ?? "en", forHTTPHeaderField: "X-HolyArrs-Language")
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONEncoder().encode(body)
        }
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            let msg = (try? JSONDecoder().decode([String: String].self, from: data))?["error"]
            throw APIError(message: msg ?? "HolyArrs answered \(code).")
        }
        if T.self == Empty.self || data.isEmpty { return try JSONDecoder().decode(T.self, from: Data("{}".utf8)) }
        do { return try JSONDecoder().decode(T.self, from: data) }
        catch { throw APIError(message: "Could not read \(path): \(error)") }
    }

    // MARK: Pictures

    /// The web app never loads TMDB directly: it goes through HolyArrs' signed-in artwork proxy.
    func imageURL(_ raw: String?, size: String = "w500") -> URL? {
        guard let raw, !raw.isEmpty else { return nil }
        if raw.hasPrefix("https://image.tmdb.org/t/p/") {
            let file = raw.split(separator: "/").last.map(String.init) ?? ""
            if demo { return URL(string: "https://image.tmdb.org/t/p/\(size)/\(file)") }
            guard let base else { return nil }
            return URL(string: "/api/v1/artwork/tmdb/\(size)/\(file)", relativeTo: base)
        }
        if raw.hasPrefix("/") {
            guard !demo, let base else { return nil }
            return URL(string: raw, relativeTo: base)
        }
        return URL(string: raw)
    }

    func imageRequest(_ url: URL) -> URLRequest {
        var req = URLRequest(url: url)
        if !demo, let host = base?.host, url.host == host { req.setValue(key, forHTTPHeaderField: "X-Api-Key") }
        return req
    }

    // MARK: Demo answers

    static func fixtureName(_ path: String) -> String {
        var p = path
        if p.hasPrefix("/") { p.removeFirst() }
        return "demo_" + String(p.map { $0.isLetter || $0.isNumber ? $0 : "_" })
    }

    private func demoAnswer<T: Decodable>(_ method: String, _ path: String) throws -> T {
        if method != "GET" {
            if let data = load(path) ?? load(path.components(separatedBy: "?")[0]) {
                return try JSONDecoder().decode(T.self, from: data)
            }
            return try JSONDecoder().decode(T.self, from: Data("{}".utf8))
        }
        let plain = path.components(separatedBy: "?")[0]
        guard let data = load(path) ?? load(plain) ?? loadPrefix(plain) else {
            throw APIError(message: "No demo data for \(path)")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func load(_ path: String) -> Data? {
        guard let url = Bundle.main.url(forResource: API.fixtureName(path), withExtension: "json") else { return nil }
        return try? Data(contentsOf: url)
    }

    private func loadPrefix(_ path: String) -> Data? {
        let stem = API.fixtureName(path) + "_"
        let all = Bundle.main.paths(forResourcesOfType: "json", inDirectory: nil).sorted()
        guard let hit = all.first(where: { ($0 as NSString).lastPathComponent.hasPrefix(stem) }) else { return nil }
        return FileManager.default.contents(atPath: hit)
    }
}

/// In-memory picture cache shared by every RemoteImage.
final class ImageStore {
    static let shared = ImageStore()
    private let cache = NSCache<NSURL, UIImage>()
    private var inflight: [URL: Task<UIImage?, Never>] = [:]
    private let lock = NSLock()

    init() { cache.countLimit = 600 }

    func cached(_ url: URL) -> UIImage? { cache.object(forKey: url as NSURL) }

    func load(_ request: URLRequest) async -> UIImage? {
        guard let url = request.url else { return nil }
        if let hit = cached(url) { return hit }
        lock.lock()
        if let t = inflight[url] { lock.unlock(); return await t.value }
        let task = Task<UIImage?, Never> {
            guard let (data, resp) = try? await URLSession.shared.data(for: request),
                  ((resp as? HTTPURLResponse)?.statusCode ?? 200) < 300,
                  let img = UIImage(data: data) else { return nil }
            let ready = await img.byPreparingForDisplay() ?? img
            return ready
        }
        inflight[url] = task
        lock.unlock()
        let img = await task.value
        lock.lock(); inflight[url] = nil; lock.unlock()
        if let img { cache.setObject(img, forKey: url as NSURL) }
        return img
    }
}
