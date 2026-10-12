import Foundation

public extension AudioBook {
    /// Also cleans saved editions created before catalog chapter names were normalized.
    func normalizingWolneChapterTitles() -> AudioBook {
        guard source == .wolneLektury else { return self }
        var book = self
        book.chapters = chapters.map { chapter in
            var cleaned = chapter
            cleaned.title = WolneLekturyMetadata.chapterTitle(
                chapter.title, bookTitle: title, authors: [author], number: chapter.number,
                chapterCount: chapters.count
            )
            return cleaned
        }
        return book
    }
}

enum WolneLekturyMetadata {
    /// Match recorded chapter ordinals to the written headings, rather than relying on list order.
    static func addingWrittenChapterNames(to book: AudioBook, fromXML xml: String) -> AudioBook {
        let delegate = WolneChapterHeadingParser()
        let parser = XMLParser(data: Data(xml.utf8))
        parser.delegate = delegate
        guard parser.parse() else { return book }
        var enriched = book
        enriched.chapters = book.chapters.map { chapter in
            let parts = chapter.title.components(separatedBy: ",")
            let label = (parts.last ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard label.range(of: #"^Rozdział\s+[IVXLCDM0-9]+$"#, options: [.regularExpression, .caseInsensitive]) != nil,
                  let ordinal = label.split(separator: " ").last.flatMap({ ordinalNumber(String($0)) }),
                  let heading = delegate.headings[headingKey(volume: volumeNumber(in: chapter.title), chapter: ordinal)],
                  !heading.isEmpty
            else { return chapter }
            var named = chapter
            named.title = "\(chapter.title) — \(heading)"
            return named
        }
        return enriched
    }

    fileprivate static func headingKey(volume: Int?, chapter: Int) -> String {
        "\(volume ?? 0):\(chapter)"
    }

    fileprivate static func ordinalNumber(_ value: String) -> Int? {
        if let number = Int(value), number > 0 { return number }
        let romanValues: [Character: Int] = ["I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000]
        let characters = Array(value.uppercased())
        guard !characters.isEmpty, characters.allSatisfy({ romanValues[$0] != nil }) else { return nil }
        return characters.enumerated().reduce(0) { total, entry in
            let current = romanValues[entry.element]!
            let next = entry.offset + 1 < characters.count ? romanValues[characters[entry.offset + 1]]! : 0
            return total + (current < next ? -current : current)
        }
    }

    static func chapterTitle(
        _ rawTitle: String?, bookTitle: String, authors: [String],
        ancestorTitles: [String] = [], number: Int, chapterCount: Int
    ) -> String {
        var title = (rawTitle ?? "")
            .replacingOccurrences(of: #"^\s*\d+[\.:]\s*"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let rootTitle = bookTitle.components(separatedBy: ",").first ?? bookTitle
        let prefixes = (authors + ancestorTitles + [bookTitle, rootTitle])
            .filter { !$0.isEmpty }
            .sorted { $0.count > $1.count }
        var removedMetadata = false
        while let prefix = prefixes.first(where: {
            prefixKey(String(title.prefix($0.count + 1))) == prefixKey($0 + ",")
        }) {
            title = String(title.dropFirst(prefix.count + 1)).trimmingCharacters(in: .whitespacesAndNewlines)
            removedMetadata = true
        }
        // A parent edition can retain a child's written-out volume label after its title.
        // Keep the actual "Tom I, Rozdział I" chapter label intact.
        if removedMetadata {
            title = title.replacingOccurrences(
                of: #"^tom\s+(?:pierwszy|drugi|trzeci|czwarty|piąty|szósty|siódmy|ósmy|dziewiąty|dziesiąty)\s*,\s*"#,
                with: "", options: [.regularExpression, .caseInsensitive]
            )
        }
        if title == bookTitle || title.isEmpty {
            return chapterCount == 1 ? bookTitle : "Chapter \(max(1, number))"
        }
        return title
    }

    private static func prefixKey(_ value: String) -> String {
        value.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "pl"))
            .replacingOccurrences(of: "ł", with: "l")
    }

    static func volumeNumber(in title: String) -> Int? {
        let normalized = title.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "pl"))
        guard let range = normalized.range(of: #"\btom\s+([a-z0-9]+)\b"#, options: .regularExpression) else { return nil }
        let value = normalized[range].split(separator: " ").last.map(String.init) ?? ""
        if let number = Int(value) { return number }
        let ordinals = ["pierwszy", "drugi", "trzeci", "czwarty", "piaty", "szosty", "siodmy", "osmy", "dziewiaty", "dziesiaty"]
        if let index = ordinals.firstIndex(of: value) { return index + 1 }
        let roman = ["i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"]
        return roman.firstIndex(of: value).map { $0 + 1 }
    }
}

private final class WolneChapterHeadingParser: NSObject, XMLParserDelegate {
    var headings: [String: String] = [:]
    private var volume: Int?
    private var headingElement: String?
    private var text = ""
    private var footnoteDepth = 0

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String]) {
        if footnoteDepth > 0 || (headingElement != nil && elementName == "pa") {
            footnoteDepth += 1
        } else if elementName == "naglowek_czesc" || elementName == "naglowek_rozdzial" {
            headingElement = elementName
            text = ""
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if headingElement != nil && footnoteDepth == 0 { text += string }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if footnoteDepth > 0 {
            footnoteDepth -= 1
            return
        }
        guard elementName == headingElement else { return }
        let heading = text.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if elementName == "naglowek_czesc" {
            volume = WolneLekturyMetadata.volumeNumber(in: heading)
        } else {
            let parts = heading.split(separator: ".", maxSplits: 1).map(String.init)
            if parts.count == 2,
               let ordinal = WolneLekturyMetadata.ordinalNumber(parts[0]),
               !parts[1].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                let key = WolneLekturyMetadata.headingKey(volume: volume, chapter: ordinal)
                // Ambiguous headings must never be attached to an unrelated recording.
                if headings[key] == nil {
                    headings[key] = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
                } else {
                    headings[key] = ""
                }
            }
        }
        headingElement = nil
    }
}
