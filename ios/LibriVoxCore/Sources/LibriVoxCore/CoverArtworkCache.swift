import CryptoKit
import Foundation
import ImageIO

public actor CoverArtworkCache {
    public static let shared = CoverArtworkCache()

    private let directoryURL: URL
    private let fileManager: FileManager

    public init(
        directoryURL: URL? = nil,
        fileManager: FileManager = .default
    ) {
        self.directoryURL = directoryURL ?? ApplicationSupportStoreDirectory
            .baseURL(fileManager: fileManager)
            .appendingPathComponent("CoverArtwork", isDirectory: true)
        self.fileManager = fileManager
    }

    public func data(for urls: [URL]) async -> Data? {
        guard !Task.isCancelled else { return nil }
        let candidateURLs = Self.uniqueURLs(urls)

        if let cachedData = cachedData(for: candidateURLs) {
            return cachedData
        }

        for url in candidateURLs {
            guard !Task.isCancelled else { return nil }
            if let fetchedData = await fetchData(from: url) {
                try? store(fetchedData, for: url)
                return fetchedData
            }
        }

        return nil
    }

    public func cachedData(for urls: [URL]) -> Data? {
        for url in Self.uniqueURLs(urls) {
            let fileURL = cacheFileURL(for: url)
            guard fileManager.fileExists(atPath: fileURL.path),
                  let data = try? Data(contentsOf: fileURL),
                  Self.isImageData(data)
            else { continue }
            return data
        }

        return nil
    }

    public func store(_ data: Data, for url: URL) throws {
        guard Self.isImageData(data) else { return }
        try fileManager.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        try data.write(to: cacheFileURL(for: url), options: [.atomic])
    }

    public func removeAll() throws {
        guard fileManager.fileExists(atPath: directoryURL.path) else { return }
        try fileManager.removeItem(at: directoryURL)
    }

    public func cacheFileURL(for url: URL) -> URL {
        directoryURL.appendingPathComponent(Self.cacheFileName(for: url), isDirectory: false)
    }

    public static func cacheFileName(for url: URL) -> String {
        let data = Data(url.absoluteString.utf8)
        let digest = SHA256.hash(data: data)
        let hash = digest.map { String(format: "%02x", $0) }.joined()
        return "\(hash).\(fileExtension(for: url))"
    }

    private func fetchData(from url: URL) async -> Data? {
        var request = URLRequest(url: url)
        request.cachePolicy = .returnCacheDataElseLoad
        request.timeoutInterval = 8

        guard let (data, response) = try? await URLSession.shared.data(for: request),
              Self.isImageData(data),
              !Task.isCancelled,
              (response as? HTTPURLResponse).map({ 200..<400 ~= $0.statusCode }) != false
        else {
            return nil
        }

        return data
    }

    private static func isImageData(_ data: Data) -> Bool {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return false }
        return CGImageSourceCreateImageAtIndex(source, 0, nil) != nil
    }

    private static func uniqueURLs(_ urls: [URL]) -> [URL] {
        var seen: Set<String> = []
        return urls.filter { url in
            seen.insert(url.absoluteString).inserted
        }
    }

    private static func fileExtension(for url: URL) -> String {
        switch url.pathExtension.lowercased() {
        case "jpg", "jpeg":
            "jpg"
        case "png":
            "png"
        case "webp":
            "webp"
        default:
            "img"
        }
    }
}
