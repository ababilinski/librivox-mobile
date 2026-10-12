import Foundation
import Testing
@testable import LibriVoxCore

@Test func unifiedSearchFindsSavedBooksWithoutCatalogResults() {
    let saved = searchBook(id: "saved", title: "River Stories", source: .customLocal)
    let unrelated = searchBook(id: "unrelated", title: "Mountain Stories")

    let everything = UnifiedBookSearch.results(
        libraryBooks: [unrelated, saved], catalogBooks: [], query: "river",
        field: .all, languages: [.all]
    )
    let library = UnifiedBookSearch.results(
        libraryBooks: [unrelated, saved], catalogBooks: [], query: "river",
        field: .all, languages: [.all], scope: .library
    )

    #expect(everything == library)
    #expect(everything.map(\.book.id) == ["saved"])
    #expect(everything.allSatisfy { $0.isInLibrary })
}

@Test func unifiedSearchMatchesAndRanksEveryLocalSearchField() {
    let books = [
        searchBook(id: "genre", genres: ["River folklore"]),
        searchBook(id: "reader", chapters: [AudioBookChapter(
            id: "reader-1", title: "Opening", number: 1, reader: "River Voice"
        )]),
        searchBook(id: "author", author: "River Person"),
        searchBook(id: "chapter", chapters: [AudioBookChapter(
            id: "chapter-1", title: "River Crossing", number: 1
        )]),
        searchBook(id: "title", title: "River Stories"),
    ]
    let all = UnifiedBookSearch.results(
        libraryBooks: books, catalogBooks: [], query: "river",
        field: .all, languages: [.english]
    )
    #expect(all.map(\.book.id) == ["title", "chapter", "author", "reader", "genre"])

    for (field, id) in [
        (CatalogSearchField.title, "title"), (.chapter, "chapter"),
        (.author, "author"), (.reader, "reader"), (.genre, "genre"),
    ] {
        let results = UnifiedBookSearch.results(
            libraryBooks: books, catalogBooks: [], query: "river",
            field: field, languages: [.english]
        )
        #expect(results.map(\.book.id) == [id])
    }
}

@Test func unifiedSearchFindsNarratorsAndCatalogTags() {
    var narrated = searchBook(id: "narrated")
    narrated.narrators = ["River Voice"]
    var tagged = searchBook(id: "tagged")
    tagged.literaryEpochs = [CatalogTag(name: "Romanticism", slug: "romanticism")]

    let readerResults = UnifiedBookSearch.results(
        libraryBooks: [narrated, tagged], catalogBooks: [], query: "river",
        field: .reader, languages: [.all]
    )
    let metadataResults = UnifiedBookSearch.results(
        libraryBooks: [narrated, tagged], catalogBooks: [], query: "romanticism",
        field: .all, languages: [.all]
    )
    #expect(readerResults.map(\.book.id) == ["narrated"])
    #expect(metadataResults.map(\.book.id) == ["tagged"])
}

@Test func unifiedSearchFoldsPolishDiacriticsAndWhitespace() {
    let book = searchBook(id: "lalka", title: "Lalka", author: "Bolesław Prus", language: "Polish")
    let results = UnifiedBookSearch.results(
        libraryBooks: [book], catalogBooks: [], query: "  BOLESLAW   prus \n",
        field: .author, languages: [.polish]
    )
    #expect(results.map(\.book.id) == ["lalka"])
}

@Test func unifiedSearchKeepsLocalImportsAndDisabledCatalogOriginsWithoutSourceFilter() {
    let imported = searchBook(id: "import", title: "River", source: .customLocal)
    let bundled = searchBook(id: "bundled", title: "River", source: .localAsset)
    let savedCatalog = searchBook(id: "saved", title: "River", source: .gutendex)
    let results = UnifiedBookSearch.results(
        libraryBooks: [imported, bundled, savedCatalog], catalogBooks: [], query: "river",
        field: .title, languages: [.all]
    )
    #expect(Set(results.map(\.book.id)) == ["import", "bundled", "saved"])
}

@Test func unifiedSearchAppliesExplicitSourceFiltersToBothCollections() {
    let local = [
        searchBook(id: "local-wolne", title: "River", source: .wolneLektury),
        searchBook(id: "local-import", title: "River", source: .customLocal),
    ]
    let remote = [
        searchBook(id: "remote-wolne", title: "River", source: .wolneLektury),
        searchBook(id: "remote-libri", title: "River", source: .libriVox),
    ]
    let results = UnifiedBookSearch.results(
        libraryBooks: local, catalogBooks: remote, query: "river",
        field: .title, languages: [.all], sources: [.wolneLektury]
    )
    #expect(results.map(\.book.id) == ["local-wolne", "remote-wolne"])
    #expect(results.map(\.isInLibrary) == [true, false])

    let noSources = UnifiedBookSearch.results(
        libraryBooks: local, catalogBooks: remote, query: "river",
        field: .title, languages: [.all], sources: []
    )
    #expect(noSources.isEmpty)
}

@Test func unifiedSearchHonorsLocalLanguagesIncludingUnknownImportedLanguage() {
    let books = [
        searchBook(id: "english", title: "River", language: "en"),
        searchBook(id: "polish", title: "River", language: "Polish"),
        searchBook(id: "unknown", title: "River", source: .customLocal, language: nil),
    ]
    let polish = UnifiedBookSearch.results(
        libraryBooks: books, catalogBooks: [], query: "river",
        field: .title, languages: [.polish]
    )
    let all = UnifiedBookSearch.results(
        libraryBooks: books, catalogBooks: [], query: "river",
        field: .title, languages: [.all]
    )
    let unspecified = UnifiedBookSearch.results(
        libraryBooks: books, catalogBooks: [], query: "river",
        field: .title, languages: []
    )
    #expect(polish.map(\.book.id) == ["polish"])
    #expect(all.count == 3)
    #expect(unspecified == all)
}

@Test func unifiedSearchScopesSeparateLibraryAndCatalogMatches() {
    let local = searchBook(id: "saved", title: "River")
    let remote = searchBook(id: "remote", title: "River")
    let library = UnifiedBookSearch.results(
        libraryBooks: [local], catalogBooks: [remote], query: "river",
        field: .title, languages: [.all], scope: .library
    )
    let catalogs = UnifiedBookSearch.results(
        libraryBooks: [local], catalogBooks: [remote], query: "river",
        field: .title, languages: [.all], scope: .catalogs
    )
    #expect(library.map(\.book.id) == ["saved"])
    #expect(catalogs.map(\.book.id) == ["remote"])
    #expect(catalogs.first?.isInLibrary == false)
}

@Test func unifiedSearchPrefersCompleteLocalRecordForExactRemoteDuplicate() {
    var local = searchBook(id: "saved", title: "River", chapters: [AudioBookChapter(
        id: "offline-chapter", title: "Opening", number: 1, localFileName: "river.m4b"
    )])
    local.isFavorite = true
    local.addedAt = Date(timeIntervalSince1970: 123)
    local.localCoverFileName = "river.jpg"
    let remote = searchBook(id: "saved", title: "River")

    for scope in [BookSearchScope.everything, .catalogs] {
        let results = UnifiedBookSearch.results(
            libraryBooks: [local], catalogBooks: [remote], query: "river",
            field: .title, languages: [.all], scope: scope
        )
        #expect(results.count == 1)
        #expect(results.first?.book == local)
        #expect(results.first?.isInLibrary == true)
    }
}

@Test func unifiedSearchRetainsDistinctSavedEditionsAndSourceIdentities() {
    let first = searchBook(id: "edition-1", title: "River", chapters: [AudioBookChapter(
        id: "recording-1", title: "Opening", number: 1, reader: "First Reader"
    )])
    let second = searchBook(id: "edition-2", title: "River", chapters: [AudioBookChapter(
        id: "recording-2", title: "Opening", number: 1, reader: "Second Reader"
    )])
    let otherSource = searchBook(id: "edition-1", title: "River", source: .customLocal)
    let results = UnifiedBookSearch.results(
        libraryBooks: [first, first, second, otherSource], catalogBooks: [first], query: "river",
        field: .title, languages: [.all]
    )
    #expect(results.count == 3)
    #expect(Set(results.map(\.id)).count == 3)
    #expect(results.map(\.book) == [first, second, otherSource])
}

@Test func unifiedSearchPreservesServerMatchesAndTheirOrderWhenClientMetadataIsIncomplete() {
    let remote = [
        searchBook(id: "server-2", title: "Zebra Collection"),
        searchBook(id: "server-1", title: "Animal Collection"),
    ]
    let results = UnifiedBookSearch.results(
        libraryBooks: [], catalogBooks: remote, query: "Hidden Chapter",
        field: .chapter, languages: [.english]
    )
    #expect(results.map(\.book) == remote)
    #expect(results.allSatisfy { !$0.isInLibrary })
}

@Test func unifiedSearchUsesLocalPlaybackRecordForServerOnlyMatch() {
    var local = searchBook(id: "collection", title: "Story Collection")
    local.isFavorite = true
    let remote = searchBook(id: "collection", title: "Story Collection")
    let results = UnifiedBookSearch.results(
        libraryBooks: [local], catalogBooks: [remote], query: "Unlisted Reader",
        field: .reader, languages: [.all]
    )
    #expect(results.map(\.book) == [local])
    #expect(results.first?.isInLibrary == true)
}

@Test func unifiedSearchDoesNotSubstituteLocalEditionOutsideLanguageFilter() {
    let local = searchBook(id: "recording", title: "River", language: "English")
    let remote = searchBook(id: "recording", title: "River", language: "Polish")
    let results = UnifiedBookSearch.results(
        libraryBooks: [local], catalogBooks: [remote], query: "river",
        field: .title, languages: [.polish]
    )
    #expect(results.map(\.book) == [remote])
}

@Test func unifiedSearchReturnsNoResultsForEmptyOrPunctuationOnlyQuery() {
    let book = searchBook(id: "saved", title: "River")
    for query in ["", " \n\t ", "… !?"] {
        let results = UnifiedBookSearch.results(
            libraryBooks: [book], catalogBooks: [book], query: query,
            field: .all, languages: [.all]
        )
        #expect(results.isEmpty)
    }
}

private func searchBook(
    id: String,
    title: String = "Collected Stories",
    author: String = "Example Author",
    source: BookSource = .libriVox,
    chapters: [AudioBookChapter] = [],
    language: String? = "English",
    genres: [String] = []
) -> AudioBook {
    AudioBook(
        id: id, title: title, author: author, source: source,
        libraryStatus: .inLibrary, chapters: chapters, language: language, genres: genres
    )
}
