import CoreGraphics
import ImageIO
import LibriVoxCore
import SwiftUI

struct ArtworkRGB: Hashable, Sendable {
    var red: Double
    var green: Double
    var blue: Double

    static let black = ArtworkRGB(red: 0, green: 0, blue: 0)
    static let white = ArtworkRGB(red: 1, green: 1, blue: 1)

    var color: Color { Color(red: red, green: green, blue: blue) }
    var saturation: Double {
        let high = max(red, green, blue)
        return high == 0 ? 0 : (high - min(red, green, blue)) / high
    }
    var luminance: Double {
        func linear(_ component: Double) -> Double {
            component <= 0.04045 ? component / 12.92 : pow((component + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }
    func mixed(with other: ArtworkRGB, amount: Double) -> ArtworkRGB {
        ArtworkRGB(
            red: red * (1 - amount) + other.red * amount,
            green: green * (1 - amount) + other.green * amount,
            blue: blue * (1 - amount) + other.blue * amount
        )
    }
    func contrast(with other: ArtworkRGB) -> Double {
        (max(luminance, other.luminance) + 0.05) / (min(luminance, other.luminance) + 0.05)
    }
}

struct ArtworkPalette: Hashable, Sendable {
    var dominant: ArtworkRGB
    var secondary: ArtworkRGB

    /// A small, deterministic histogram favors substantial colored areas over text and paper borders.
    static func extract(from data: Data) -> ArtworkPalette? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil), CGImageSourceGetCount(source) > 0,
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: 64,
                kCGImageSourceCreateThumbnailWithTransform: true,
              ] as CFDictionary)
        else { return nil }
        let width = image.width, height = image.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }
        struct Bucket {
            var count = 0
            var red = 0.0, green = 0.0, blue = 0.0
            var rgb: ArtworkRGB {
                ArtworkRGB(red: red / Double(count), green: green / Double(count), blue: blue / Double(count))
            }
        }
        var histogram: [Int: Bucket] = [:]
        for index in stride(from: 0, to: bytes.count, by: 4) {
            let alpha = Double(bytes[index + 3]) / 255
            guard alpha > 0.9 else { continue }
            let rgb = ArtworkRGB(
                red: min(1, Double(bytes[index]) / 255 / alpha),
                green: min(1, Double(bytes[index + 1]) / 255 / alpha),
                blue: min(1, Double(bytes[index + 2]) / 255 / alpha)
            )
            guard rgb.saturation > 0.16, rgb.luminance > 0.015, rgb.luminance < 0.92 else { continue }
            let key = Int(rgb.red * 15) << 8 | Int(rgb.green * 15) << 4 | Int(rgb.blue * 15)
            var bucket = histogram[key, default: Bucket()]
            bucket.count += 1
            bucket.red += rgb.red; bucket.green += rgb.green; bucket.blue += rgb.blue
            histogram[key] = bucket
        }
        let buckets = histogram.sorted {
            let lhs = Double($0.value.count) * (0.6 + $0.value.rgb.saturation)
            let rhs = Double($1.value.count) * (0.6 + $1.value.rgb.saturation)
            return lhs == rhs ? $0.key < $1.key : lhs > rhs
        }
        guard let first = buckets.first, first.value.count >= max(4, width * height / 100) else { return nil }
        let dominant = first.value.rgb
        let secondary = buckets.dropFirst().first { item in
            let color = item.value.rgb
            return abs(color.red - dominant.red) + abs(color.green - dominant.green) + abs(color.blue - dominant.blue) > 0.3
                && item.value.count >= first.value.count / 10
        }?.value.rgb ?? dominant
        return ArtworkPalette(dominant: dominant, secondary: secondary)
    }

    func backgroundColors(dark: Bool, increasedContrast: Bool = false) -> [ArtworkRGB] {
        let target: ArtworkRGB = dark ? .black : .white
        let amount = dark ? (increasedContrast ? 0.68 : 0.60) : (increasedContrast ? 0.82 : 0.66)
        return [dominant.mixed(with: target, amount: amount), secondary.mixed(with: target, amount: amount + 0.08)]
    }
}

extension AudioBook {
    var artworkURLs: [URL] {
        var seen: Set<String> = []
        return [fullCoverImageURL, coverImageURL].compactMap { value in
            guard let value, var components = URLComponents(string: value.trimmingCharacters(in: .whitespacesAndNewlines)) else { return nil }
            if components.host == "www.archive.org" { components.host = "archive.org" }
            guard let url = components.url, seen.insert(url.absoluteString).inserted else { return nil }
            return url
        }
    }
    var artworkIdentity: String { artworkURLs.map(\.absoluteString).joined(separator: "|") }
}

actor ArtworkPaletteCache {
    static let shared = ArtworkPaletteCache()
    private struct Entry { var palette: ArtworkPalette? }
    private var palettes: [String: Entry] = [:]
    private var order: [String] = []
    private var pending: [String: Task<ArtworkPalette?, Never>] = [:]

    func palette(for book: AudioBook) async -> ArtworkPalette? {
        let key = book.artworkIdentity, urls = book.artworkURLs
        guard !urls.isEmpty else { return nil }
        if let cached = palettes[key] { return cached.palette }
        if let request = pending[key] { return await request.value }
        let request = Task.detached(priority: .utility) {
            guard let data = await CoverArtworkCache.shared.data(for: urls) else { return nil as ArtworkPalette? }
            return ArtworkPalette.extract(from: data)
        }
        pending[key] = request
        let palette = await request.value
        pending[key] = nil
        palettes[key] = Entry(palette: palette)
        order.append(key)
        if order.count > 96 { palettes[order.removeFirst()] = nil }
        return palette
    }
}

struct ArtworkGradient: View {
    enum Style { case hero, player, card }
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var contrast
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var palette: ArtworkPalette?
    @State private var loadedIdentity = ""
    var book: AudioBook?
    var style: Style
    var enabled = true

    var body: some View {
        let currentPalette = loadedIdentity == book?.artworkIdentity ? palette : nil
        let colors = currentPalette?.backgroundColors(dark: colorScheme == .dark, increasedContrast: contrast == .increased).map(\.color)
        let surface = LibriVoxBrand.surface(for: colorScheme)
        Group {
            if enabled, let colors {
                LinearGradient(stops: [
                    .init(color: colors[0], location: 0),
                    .init(color: colors[1], location: style == .hero ? 0.55 : 1),
                    .init(color: style == .hero ? surface : colors[1], location: 1),
                ], startPoint: .topLeading, endPoint: .bottomTrailing)
            } else {
                surface
            }
        }
        .task(id: "\(book?.artworkIdentity ?? "")|\(enabled)") {
            guard enabled, let book else { palette = nil; loadedIdentity = ""; return }
            let newPalette = await ArtworkPaletteCache.shared.palette(for: book)
            guard !Task.isCancelled else { return }
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.35)) {
                loadedIdentity = book.artworkIdentity
                palette = newPalette
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

enum ArtworkAppearanceMigration {
    private static let key = "didAdoptCoverColorAppearance"
    static func apply(to settings: inout UserSettings, defaults: UserDefaults) {
        guard !defaults.bool(forKey: key) else { return }
        settings.bookDetailUseArtworkColorScheme = true
        if let data = try? JSONEncoder().encode(settings) {
            defaults.set(data, forKey: "settings")
            defaults.set(true, forKey: key)
        }
    }
}
