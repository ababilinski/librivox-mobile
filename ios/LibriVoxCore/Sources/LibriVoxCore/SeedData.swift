import Foundation

public extension AudioBook {
    static let theJungle = AudioBook(
        id: "3269",
        title: "The Jungle",
        author: "Upton Sinclair",
        description: "A LibriVox recording read by Tom Weiss. The Rudkus family emigrates to Chicago in search of a better life and faces the brutal realities of Packingtown and the meatpacking industry.",
        source: .libriVox,
        libraryStatus: .inLibrary,
        coverImageURL: "https://archive.org/services/img/jungle_tw_0908_librivox",
        fullCoverImageURL: "https://archive.org/services/img/jungle_tw_0908_librivox",
        chapters: [
            AudioBookChapter(
                id: "3269-182898",
                title: "Chapter 1",
                number: 1,
                reader: "Tom Weiss",
                durationSeconds: 3424,
                listenURL: "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_01_sinclair_64kb.mp3"
            ),
            AudioBookChapter(
                id: "3269-182899",
                title: "Chapter 2",
                number: 2,
                reader: "Tom Weiss",
                durationSeconds: 1664,
                listenURL: "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_02_sinclair_64kb.mp3"
            ),
            AudioBookChapter(
                id: "3269-182900",
                title: "Chapter 3",
                number: 3,
                reader: "Tom Weiss",
                durationSeconds: 1990,
                listenURL: "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_03_sinclair_64kb.mp3"
            ),
        ],
        totalDurationSeconds: 57_768,
        libriVoxURL: "https://librivox.org/the-jungle-by-upton-sinclair/",
        language: "English",
        narrators: ["Tom Weiss"],
        publisher: "LibriVox",
        genres: ["Fiction", "Political fiction"],
        addedAt: .now
    )

    static let prideAndPrejudice = AudioBook(
        id: "253",
        title: "Pride and Prejudice",
        author: "Jane Austen",
        description: "A public-domain LibriVox recording of Jane Austen's classic novel.",
        source: .libriVox,
        coverImageURL: "https://is1-ssl.mzstatic.com/image/thumb/Publication211/v4/c2/d3/13/c2d3135e-89c9-d032-12e3-46be81d32f50/BKS-WW-ABC-Pride_and_Prejudice-Jane_Austen.png/600x600bb.jpg",
        fullCoverImageURL: "https://is1-ssl.mzstatic.com/image/thumb/Publication211/v4/c2/d3/13/c2d3135e-89c9-d032-12e3-46be81d32f50/BKS-WW-ABC-Pride_and_Prejudice-Jane_Austen.png/1200x1200bb.jpg",
        chapters: [
            AudioBookChapter(
                id: "253-001",
                title: "Chapter 1",
                number: 1,
                reader: "LibriVox volunteers",
                durationSeconds: 700,
                listenURL: "https://www.archive.org/download/prideandprejudice_0709_librivox/prideandprejudice_01_austen_64kb.mp3"
            ),
        ],
        libriVoxURL: "https://librivox.org/pride-and-prejudice-by-jane-austen/",
        language: "English",
        publisher: "LibriVox"
    )

    static let seedLibrary: [AudioBook] = [
        .theJungle,
        .prideAndPrejudice,
    ]
}

public func formatDuration(seconds: Int) -> String {
    guard seconds > 0 else { return "0:00" }
    let hours = seconds / 3600
    let minutes = (seconds % 3600) / 60
    let remainingSeconds = seconds % 60
    if hours > 0 {
        return "\(hours):" + String(format: "%02d:%02d", minutes, remainingSeconds)
    }
    return "\(minutes):" + String(format: "%02d", remainingSeconds)
}
