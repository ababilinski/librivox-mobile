#if DEBUG
import AVFoundation
import LibriVoxCore
import UIKit

/// Deterministic, offline-only artwork and audio for visual regression tests.
@MainActor
enum ArtworkAppearanceFixtures {
    static func readerDocument(for book: AudioBook) -> ReadAlongBook {
        ReadAlongBook(
            bookID: book.id, title: book.title, author: book.author, source: book.source,
            sourceURL: book.sourceTextURL, syncQuality: .estimated,
            chapters: book.chapters.map { chapter in
                ReadAlongChapter(
                    id: chapter.id, title: chapter.displayTitle, number: chapter.number,
                    segments: (0..<4).map { index in
                        ReadAlongSegment(
                            id: "\(chapter.id)-reader-\(index)", chapterID: chapter.id, index: index,
                            text: "Paragraph \(index + 1). The garden was quiet in the early morning. A reader walked among the trees and listened to the birds."
                        )
                    }
                )
            }
        )
    }

    static let books: [AudioBook] = {
        let directory = ApplicationSupportStoreDirectory.baseURL().appendingPathComponent("CoverArtwork")
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let audioURL = FileManager.default.temporaryDirectory.appendingPathComponent("appearance-silence.caf")
        if !FileManager.default.fileExists(atPath: audioURL.path),
           let format = AVAudioFormat(standardFormatWithSampleRate: 8_000, channels: 1),
           let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 480_000) {
            buffer.frameLength = 480_000
            buffer.floatChannelData?[0].update(repeating: 0, count: 480_000)
            if let file = try? AVAudioFile(forWriting: audioURL, settings: format.settings) { try? file.write(from: buffer) }
        }
        let samples: [(String, String, UIColor?)] = [
            ("red", "The Scarlet Garden: An Unusually Long Journey Through the Seasons and the Stories We Remember", UIColor(red: 0.76, green: 0.12, blue: 0.09, alpha: 1)),
            ("gold", "The Golden Orchard", UIColor(red: 0.73, green: 0.48, blue: 0.09, alpha: 1)),
            ("pale", "A Quiet Morning", UIColor(white: 0.92, alpha: 1)),
            ("dark", "Midnight Stories", UIColor(red: 0.13, green: 0.18, blue: 0.39, alpha: 1)),
            ("missing", "A Book Without Artwork", nil),
        ]
        return samples.map { key, title, color in
            let coverURL = URL(string: "https://appearance-fixture.invalid/\(key).png")!
            if let color {
                let image = UIGraphicsImageRenderer(size: CGSize(width: 300, height: 420)).image { context in
                    color.setFill()
                    context.fill(CGRect(x: 0, y: 0, width: 300, height: 420))
                    let style = NSMutableParagraphStyle(); style.alignment = .center
                    let attributes: [NSAttributedString.Key: Any] = [
                        .font: UIFont.systemFont(ofSize: 26, weight: .semibold),
                        .foregroundColor: UIColor.white, .paragraphStyle: style,
                    ]
                    (title as NSString).draw(in: CGRect(x: 25, y: 50, width: 250, height: 220), withAttributes: attributes)
                    ("ARTWORK STUDY" as NSString).draw(in: CGRect(x: 25, y: 330, width: 250, height: 70), withAttributes: attributes)
                }
                if let data = image.pngData() {
                    try? data.write(to: directory.appendingPathComponent(CoverArtworkCache.cacheFileName(for: coverURL)), options: .atomic)
                }
            }
            let isLongBook = key == "gold" && UserDefaults.standard.bool(forKey: "appearanceLongBook")
            return AudioBook(
                id: "appearance-\(key)", title: title, author: "A. Reader",
                description: isLongBook ? String(repeating: "A long audiobook with a full description and many named chapters. ", count: 20) : "",
                source: isLongBook ? .wolneLektury : .libriVox,
                coverImageURL: color == nil ? nil : coverURL.absoluteString,
                sourceTextURL: "https://example.com/read",
                chapters: isLongBook ? (1...160).map { number in
                    AudioBookChapter(id: "gold-\(number)", title: "\(number). A. Reader, \(title), Chapter \(number) — A journey through the orchard", number: number, reader: "A. Narrator", durationSeconds: 60, listenURL: audioURL.absoluteString)
                } : [
                    AudioBookChapter(id: "\(key)-1", title: "The opening chapter with a wonderfully long descriptive title for the stories we remember", number: 1, reader: "A. Narrator", durationSeconds: 60, listenURL: audioURL.absoluteString),
                    AudioBookChapter(id: "\(key)-2", title: "The next chapter", number: 2, reader: "A. Narrator", durationSeconds: 60, listenURL: audioURL.absoluteString),
                ], language: "English", genres: ["Fiction"]
            )
        }
    }()
}
#endif
