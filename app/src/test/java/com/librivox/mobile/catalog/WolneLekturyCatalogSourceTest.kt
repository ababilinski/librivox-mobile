package com.librivox.mobile.catalog

import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.canReadSourceText
import com.librivox.mobile.model.downloadableBookEpubUrl
import com.librivox.mobile.model.downloadableReadAlongAssetUrl
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolneLekturyCatalogSourceTest {
    private val source = WolneLekturyCatalogSource()

    @Test
    fun audiobookIndex_keepsOnlyAudioTitlesAndMapsPolishMetadata() {
        val stubs = source.parseAudiobookIndexJson(
            """
            [
              {
                "title": "Antek",
                "author": "Bolesław Prus",
                "slug": "antek",
                "has_audio": true,
                "genre": "Nowela",
                "kind": "Epika",
                "epoch": "Pozytywizm",
                "simple_thumb": "https://wolnelektury.pl/media/book/cover_api_thumb/antek.jpg",
                "url": "https://wolnelektury.pl/katalog/lektura/antek/"
              },
              {
                "title": "Paper only",
                "author": "Someone",
                "slug": "paper-only",
                "has_audio": false,
                "genre": "Wiersz"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, stubs.size)
        val antek = stubs.single()
        assertEquals("antek", antek.slug)
        assertEquals("Antek", antek.title)
        assertEquals("Bolesław Prus", antek.author)
        assertEquals("https://wolnelektury.pl/media/book/cover_api_thumb/antek.jpg", antek.coverUrl)
        assertEquals(listOf("Novella"), antek.literaryGenres.map { it.name })
        assertEquals(listOf("Prose"), antek.literaryKinds.map { it.name })
        assertEquals(listOf("Positivism"), antek.literaryEpochs.map { it.name })
        assertEquals(listOf("nowela"), antek.literaryGenres.map { it.slug })
    }

    @Test
    fun audiobookIndex_keepsParentTitlesWithAudioChildren() {
        val stubs = source.parseAudiobookIndexJson(
            """
            [
              {
                "title": "Chłopi",
                "author": "Władysław Stanisław Reymont",
                "slug": "chlopi",
                "has_audio": false,
                "children": [
                  {"title": "Chłopi, Część pierwsza - Jesień", "slug": "chlopi-czesc-pierwsza-jesien", "has_audio": true}
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, stubs.size)
        assertEquals("chlopi", stubs.single().slug)
        assertEquals("Chłopi", stubs.single().title)
    }

    @Test
    fun audiobookIndex_readsCollectionBooksArray() {
        val stubs = source.parseAudiobookIndexJson(
            """
            {
              "url": "https://wolnelektury.pl/katalog/lektury/zakazane-ksiazki/",
              "books": [
                {
                  "title": "Kandyd",
                  "author": "François-Marie Arouet (Voltaire / Wolter)",
                  "slug": "kandyd",
                  "has_audio": true,
                  "genre": "Powiastka filozoficzna",
                  "kind": "Epika",
                  "epoch": "Oświecenie"
                },
                {
                  "title": "Paper only",
                  "author": "Someone",
                  "slug": "paper-only",
                  "has_audio": false
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, stubs.size)
        assertEquals("kandyd", stubs.single().slug)
        assertEquals("Kandyd", stubs.single().title)
        assertEquals(listOf("Philosophical tale"), stubs.single().literaryGenres.map { it.name })
    }

    @Test
    fun popularityCatalogPage_mapsWebsiteOrderToSourceRanks() {
        val page = source.parsePopularityCatalogPage(
            """
            <div id="book-list">
              <article class="l-books__item" data-pop="-1008">
                <h2 class="s"><a href="/katalog/lektura/pan-tadeusz/">Pan Tadeusz</a></h2>
              </article>
              <article class="l-books__item" data-pop="-908">
                <h2 class="s"><a href="https://wolnelektury.pl/katalog/lektura/cierpienia-mlodego-wertera/">Werter</a></h2>
              </article>
            </div>
            <div id="paginator">
              <a href="?page=2">2</a>
              <a href="?page=3">3</a>
            </div>
            """.trimIndent(),
            firstRank = 10,
        )

        assertEquals(
            mapOf(
                "pan-tadeusz" to 10,
                "cierpienia-mlodego-wertera" to 11,
            ),
            page.ranks,
        )
        assertEquals(3, page.pageCount)
    }

    @Test
    fun featuredBooks_useZakazaneCollectionSortedByPopularity() = runBlocking {
        val source = WolneLekturyCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    val (body, type) = when (url) {
                        "https://wolnelektury.pl/api/collections/zakazane-ksiazki/?format=json" ->
                            zakazaneCollectionJson() to "application/json"
                        "https://wolnelektury.pl/katalog/audiobooki/?page=1&order=pop&search=" ->
                            popularAudiobooksHtml() to "text/html"
                        else -> error("Unexpected URL: $url")
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody(type.toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.featuredBooks()

        assertEquals(
            listOf("Pan Tadeusz", "Kandyd", "Hamlet"),
            books.map { it.title },
        )
        assertEquals(listOf(1, 2, 3), books.map { it.sourcePopularityRank })
    }

    @Test
    fun taggedAudiobooksUrl_buildsAuthorAudiobookEndpointFromPolishName() {
        assertEquals(
            "https://wolnelektury.pl/api/authors/boleslaw-prus/parent_books/count/50/?format=json",
            source.taggedAudiobooksUrl("Bolesław Prus", CatalogSearchField.Author, count = 50),
        )
    }

    @Test
    fun taggedAudiobooksUrl_translatesEnglishGenreToPolishApiSlug() {
        assertEquals(
            "https://wolnelektury.pl/api/genres/powiesc/parent_books/count/50/?format=json",
            source.taggedAudiobooksUrl("Novel", CatalogSearchField.Genre, count = 50),
        )
    }

    @Test
    fun audiobookIndex_keepsFullBoleslawPrusAuthorAudiobookList() {
        val stubs = source.parseAudiobookIndexJson(
            """
            [
              {"title": "Antek", "author": "Bolesław Prus", "slug": "antek", "has_audio": true},
              {"title": "Kamizelka", "author": "Bolesław Prus", "slug": "kamizelka", "has_audio": true},
              {"title": "Katarynka", "author": "Bolesław Prus", "slug": "katarynka", "has_audio": true},
              {"title": "Lalka, tom drugi", "author": "Bolesław Prus", "slug": "lalka-tom-drugi", "has_audio": true},
              {"title": "Lalka, tom pierwszy", "author": "Bolesław Prus", "slug": "lalka-tom-pierwszy", "has_audio": true},
              {"title": "Anielka", "author": "Bolesław Prus", "slug": "prus-anielka", "has_audio": true},
              {"title": "Placówka", "author": "Bolesław Prus", "slug": "prus-placowka", "has_audio": true},
              {"title": "Zemsta", "author": "Bolesław Prus", "slug": "prus-zemsta", "has_audio": true},
              {"title": "Z legend dawnego Egiptu", "author": "Bolesław Prus", "slug": "z-legend-dawnego-egiptu", "has_audio": true}
            ]
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "Antek",
                "Kamizelka",
                "Katarynka",
                "Lalka, tom drugi",
                "Lalka, tom pierwszy",
                "Anielka",
                "Placówka",
                "Zemsta",
                "Z legend dawnego Egiptu",
            ),
            stubs.map { it.title },
        )
    }

    @Test
    fun titleSearch_fallsBackToExactBookSlugWhenIndexMissesParentTitle() = runBlocking {
        val source = WolneLekturyCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    val body = when (url) {
                        "https://wolnelektury.pl/api/parent_books/?format=json" -> "[]"
                        "https://wolnelektury.pl/api/books/chlopi/?format=json" -> chlopiParentJson()
                        "https://wolnelektury.pl/api/books/chlopi-czesc-pierwsza-jesien/?format=json" -> chlopiJesienJson()
                        else -> error("Unexpected URL: $url")
                    }.toResponseBody("application/json".toMediaType())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "chlopi",
            field = CatalogSearchField.Title,
            limit = 10,
            offset = 0,
            language = "Polish",
        )

        assertEquals(1, books.size)
        val book = books.single()
        assertEquals("wolnelektury-chlopi", book.id)
        assertEquals("Chłopi", book.title)
        assertEquals("Władysław Stanisław Reymont", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("Rozdział 1", book.chapters[0].title)
        assertEquals("Joanna Domańska", book.chapters[0].reader)

        val accentedBooks = source.search(
            query = "Chłopi",
            field = CatalogSearchField.Title,
            limit = 10,
            offset = 0,
            language = "Polish",
        )
        assertEquals("wolnelektury-chlopi", accentedBooks.single().id)

        val allFieldBooks = source.search(
            query = "chlopi",
            field = CatalogSearchField.All,
            limit = 10,
            offset = 0,
            language = "Polish",
        )
        assertEquals("wolnelektury-chlopi", allFieldBooks.single().id)
    }

    @Test
    fun allSearch_doesNotHydrateUnmatchedWolneStubs() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val source = WolneLekturyCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls += url
                    val (code, body) = when (url) {
                        "https://wolnelektury.pl/api/parent_books/?format=json" -> 200 to """
                            [
                              {
                                "title": "Antek",
                                "author": "Bolesław Prus",
                                "slug": "antek",
                                "has_audio": true
                              }
                            ]
                        """.trimIndent()
                        "https://wolnelektury.pl/api/books/scarlet-letter/?format=json" -> 404 to "{}"
                        else -> error("Unexpected URL: $url")
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "Not found")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "Scarlet Letter",
            field = CatalogSearchField.All,
            limit = 10,
            offset = 0,
            language = "Polish",
        )

        assertTrue(books.isEmpty())
        assertEquals(
            listOf(
                "https://wolnelektury.pl/api/parent_books/?format=json",
                "https://wolnelektury.pl/katalog/audiobooki/?page=1&order=pop&search=",
                "https://wolnelektury.pl/api/books/scarlet-letter/?format=json",
            ),
            requestedUrls,
        )
    }

    @Test
    fun allSearch_matchesWolneStubMetadataWithoutDetailHydration() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val source = WolneLekturyCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls += url
                    val body = when (url) {
                        "https://wolnelektury.pl/api/parent_books/?format=json" -> """
                            [
                              {
                                "title": "Pan Tadeusz",
                                "author": "Adam Mickiewicz",
                                "slug": "pan-tadeusz",
                                "has_audio": true,
                                "genre": "Epos",
                                "kind": "Epika",
                                "epoch": "Romantyzm"
                              }
                            ]
                        """.trimIndent() to "application/json"
                        "https://wolnelektury.pl/katalog/audiobooki/?page=1&order=pop&search=" ->
                            popularAudiobooksHtml() to "text/html"
                        else -> error("Unexpected URL: $url")
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.first.toResponseBody(body.second.toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "Romanticism",
            field = CatalogSearchField.All,
            limit = 10,
            offset = 0,
            language = "Polish",
        )

        assertEquals(listOf("wolnelektury-pan-tadeusz"), books.map { it.id })
        assertEquals(listOf("Romanticism"), books.single().literaryEpochs.map { it.name })
        assertEquals(
            listOf(
                "https://wolnelektury.pl/api/parent_books/?format=json",
                "https://wolnelektury.pl/katalog/audiobooki/?page=1&order=pop&search=",
            ),
            requestedUrls,
        )
    }

    @Test
    fun chapterSearch_usesCachedDetailsWithoutRemoteCrawling() = runBlocking {
        val source = WolneLekturyCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    error("Unexpected URL: ${chain.request().url}")
                }
                .build(),
        )

        val books = source.search(
            query = "Rozdział",
            field = CatalogSearchField.Chapter,
            limit = 10,
            offset = 0,
            language = "Polish",
        )

        assertTrue(books.isEmpty())
    }

    @Test
    fun bookDetail_mapsMp3MediaToPlayableChapters() {
        val book = source.parseBookDetail(
            slug = "pan-tadeusz",
            rawJson = """
            {
              "title": "Pan Tadeusz, czyli ostatni zajazd na Litwie",
              "url": "https://wolnelektury.pl/katalog/lektura/pan-tadeusz/",
              "cover": "https://wolnelektury.pl/media/book/cover/pan-tadeusz.jpg",
              "simple_thumb": "https://wolnelektury.pl/media/book/cover_api_thumb/pan-tadeusz.jpg",
              "language": "pol",
              "audio_length": "8:44:04",
              "fragment_data": {
                "html": "<p class=\"paragraph\">Litwo! Ojczyzno moja...</p>"
              },
              "authors": [
                {"name": "Adam Mickiewicz"}
              ],
              "genres": [
                {"name": "Epos"}
              ],
              "kinds": [
                {"name": "Epika"}
              ],
              "epochs": [
                {"name": "Romantyzm"}
              ],
              "media": [
                {
                  "url": "https://wolnelektury.pl/media/book/audio.epub/pan-tadeusz.audio.epub",
                  "type": "audio.epub",
                  "name": "Pan Tadeusz, czyli ostatni zajazd na Litwie"
                },
                {
                  "url": "https://wolnelektury.pl/media/book/daisy.zip/adam-mickiewicz-pan-tadeusz.daisy.zip",
                  "type": "daisy",
                  "name": "Pan Tadeusz, czyli ostatni zajazd na Litwie"
                },
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/pan-tadeusz_001_ksiega-pierwsza-gospodarstwo.mp3",
                  "director": "Adam Bień",
                  "type": "mp3",
                  "name": "01. Adam Mickiewicz, Pan Tadeusz, czyli ostatni zajazd na Litwie, Księga pierwsza. Gospodarstwo",
                  "artist": "Wiktor Korzeniewski"
                },
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/pan-tadeusz_002_ksiega-druga-zamek.mp3",
                  "director": "Adam Bień",
                  "type": "mp3",
                  "name": "02. Adam Mickiewicz, Pan Tadeusz, czyli ostatni zajazd na Litwie, Księga druga. Zamek",
                  "artist": "Wiktor Korzeniewski"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("wolnelektury-pan-tadeusz", book.id)
        assertEquals(BookSource.WolneLektury, book.source)
        assertEquals("Adam Mickiewicz", book.author)
        assertEquals("Polish", book.language)
        assertTrue(book.translationMetadataChecked)
        assertTrue(book.translators.isEmpty())
        assertEquals(31_444, book.totalDurationSeconds)
        assertEquals("Litwo! Ojczyzno moja...", book.description)
        assertEquals("https://wolnelektury.pl/katalog/lektura/pan-tadeusz/", book.wolneLekturyUrl)
        assertEquals(
            "https://wolnelektury.pl/media/book/audio.epub/pan-tadeusz.audio.epub",
            book.audioEpubUrl,
        )
        assertEquals(
            "https://wolnelektury.pl/media/book/daisy.zip/adam-mickiewicz-pan-tadeusz.daisy.zip",
            book.daisyUrl,
        )
        assertEquals(
            "https://wolnelektury.pl/media/book/cover_api_thumb/pan-tadeusz.jpg",
            book.coverImageUrl,
        )
        assertEquals(
            "https://wolnelektury.pl/media/book/cover/pan-tadeusz.jpg",
            book.fullCoverImageUrl,
        )
        assertTrue(book.genres.contains("Epic"))
        assertTrue(book.genres.contains("Romanticism"))
        assertEquals(listOf("Adam Mickiewicz"), book.authorTags.map { it.name })
        assertEquals(listOf("Epic"), book.literaryGenres.map { it.name })
        assertEquals(listOf("Prose"), book.literaryKinds.map { it.name })
        assertEquals(listOf("Romanticism"), book.literaryEpochs.map { it.name })
        assertEquals(2, book.chapters.size)
        assertEquals("Księga pierwsza. Gospodarstwo", book.chapters[0].title)
        assertEquals("Wiktor Korzeniewski", book.chapters[0].reader)
        assertEquals("Adam Bień", book.chapters[0].director)
        assertEquals(
            "https://wolnelektury.pl/media/book/mp3/pan-tadeusz_001_ksiega-pierwsza-gospodarstwo.mp3",
            book.chapters[0].listenUrl,
        )
        assertEquals("audio/mpeg", book.chapters[0].mimeType)
        assertEquals(0, book.chapters[0].durationSeconds)
    }

    @Test
    fun bookDetail_mapsTranslatorMetadataForTranslatedWorks() {
        val book = source.parseBookDetail(
            slug = "studnia-i-wahadlo",
            rawJson = """
            {
              "title": "Studnia i wahadło",
              "url": "https://wolnelektury.pl/katalog/lektura/studnia-i-wahadlo/",
              "language": "pol",
              "authors": [
                {"name": "Edgar Allan Poe"}
              ],
              "translators": [
                {"name": "Bolesław Leśmian"}
              ],
              "media": [
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/studnia-i-wahadlo.mp3",
                  "type": "mp3",
                  "name": "Edgar Allan Poe, Studnia i wahadło",
                  "artist": "Jan Nowak"
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(book.translationMetadataChecked)
        assertEquals(listOf("Bolesław Leśmian"), book.translators)
        assertNull(book.originalLanguage)
    }

    @Test
    fun bookDetail_keepsEveryJaszczurPlayableAudioPart() {
        val media = (1..22).joinToString(",\n") { index ->
            val padded = index.toString().padStart(2, '0')
            val title = when (index) {
                1 -> "Od tłumacza; I. Talizman (część 1.)"
                in 2..5 -> "I. Talizman (część $index.)"
                in 6..13 -> "II. Kobieta bez serca (część ${index - 5}.)"
                in 14..21 -> "III. Agonia (część ${index - 13}.)"
                else -> "III. Agonia (część 8.); Epilog"
            }
            """
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/balzac-komedia-ludzka-jaszczur_$padded.mp3",
                  "type": "mp3",
                  "name": "$padded. Honoré de Balzac, Jaszczur, $title",
                  "artist": "Wiktor Korzeniewski"
                }
            """.trimIndent()
        }
        val book = source.parseBookDetail(
            slug = "balzac-komedia-ludzka-jaszczur",
            rawJson = """
            {
              "title": "Jaszczur",
              "language": "pol",
              "authors": [
                {"name": "Honoré de Balzac"}
              ],
              "media": [
                $media
              ]
            }
            """.trimIndent(),
        )

        assertEquals(22, book.chapters.size)
        assertEquals("Od tłumacza; I. Talizman (część 1.)", book.chapters.first().title)
        assertEquals(1, book.chapters.first().number)
        assertEquals("III. Agonia (część 8.); Epilog", book.chapters.last().title)
        assertEquals(22, book.chapters.last().number)
    }

    @Test
    fun bookDetail_omitsWolneStopkaTrackFromPlayableChapters() {
        val book = source.parseBookDetail(
            slug = "prus-anielka",
            rawJson = """
            {
              "title": "Anielka",
              "language": "pol",
              "authors": [
                {"name": "Bolesław Prus"}
              ],
              "media": [
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/prus-anielka_001_stopka.mp3",
                  "type": "mp3",
                  "name": "01. Bolesław Prus, Anielka, Stopka",
                  "artist": "Daniel Brzeziński"
                },
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/prus-anielka_002_rozdzial-pierwszy.mp3",
                  "type": "mp3",
                  "name": "02. Bolesław Prus, Anielka, Rozdział pierwszy. Autor dokonywa przeglądu osób",
                  "artist": "Daniel Brzeziński"
                },
                {
                  "url": "https://wolnelektury.pl/media/book/mp3/prus-anielka_003_rozdzial-drugi.mp3",
                  "type": "mp3",
                  "name": "03. Bolesław Prus, Anielka, Rozdział drugi. Czytelnik bliżej poznaje bohaterkę",
                  "artist": "Daniel Brzeziński"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, book.chapters.size)
        assertEquals("Rozdział pierwszy. Autor dokonywa przeglądu osób", book.chapters[0].title)
        assertEquals(1, book.chapters[0].number)
        assertEquals(
            "https://wolnelektury.pl/media/book/mp3/prus-anielka_002_rozdzial-pierwszy.mp3",
            book.chapters[0].listenUrl,
        )
    }

    @Test
    fun bookDetail_usesTotalDurationForSingleTrackAudiobooks() {
        val book = source.parseBookDetail(
            slug = "antek",
            rawJson = """
            {
              "title": "Antek",
              "language": "pol",
              "epub": "https://wolnelektury.pl/media/book/epub/antek.epub",
              "audio_length": "1:02:07",
              "authors": [
                {"name": "Bolesław Prus", "slug": "boleslaw-prus"}
              ],
              "media": [
                {
                  "url": "https://wolnelektury.pl/media/book/daisy.zip/boleslaw-prus-antek.daisy.zip",
                  "type": "daisy",
                  "name": "Bolesław Prus, Antek"
                },
                {
                  "url": "/media/book/mp3/boleslaw-prus-antek.mp3",
                  "type": "mp3",
                  "name": "Bolesław Prus, Antek",
                  "artist": "Jan Peszek",
                  "director": "Marcin Cisło"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(3_727, book.totalDurationSeconds)
        assertEquals(1, book.chapters.size)
        assertEquals("Antek", book.chapters.single().title)
        assertEquals("Jan Peszek", book.chapters.single().reader)
        assertEquals("Marcin Cisło", book.chapters.single().director)
        assertEquals(3_727, book.chapters.single().durationSeconds)
        assertTrue(book.canReadSourceText())
        assertEquals(
            "https://wolnelektury.pl/media/book/epub/antek.epub",
            book.downloadableBookEpubUrl(),
        )
        assertEquals(
            "https://wolnelektury.pl/media/book/daisy.zip/boleslaw-prus-antek.daisy.zip",
            book.downloadableReadAlongAssetUrl(),
        )
        assertEquals(
            "https://wolnelektury.pl/media/book/mp3/boleslaw-prus-antek.mp3",
            book.chapters.single().listenUrl,
        )
    }

    @Test
    fun bookDetail_mapsAudioChildrenToCollectionChapters() {
        val book = source.parseBookDetail(
            slug = "baczynski-1942-1943-1944",
            rawJson = """
            {
              "title": "1942, 1943, 1944",
              "url": "https://wolnelektury.pl/katalog/lektura/baczynski-1942-1943-1944/",
              "language": "pol",
              "audio_length": "",
              "authors": [
                {"name": "Krzysztof Kamil Baczyński", "slug": "krzysztof-kamil-baczynski"}
              ],
              "genres": [
                {"name": "Wiersz", "slug": "wiersz"}
              ],
              "kinds": [
                {"name": "Liryka", "slug": "liryka"}
              ],
              "epochs": [
                {"name": "Współczesność", "slug": "wspolczesnosc"}
              ],
              "children": [
                {
                  "slug": "baczynski-1942-1943-1944-motto-i-dedykacja",
                  "title": "[1942, 1943, 1944 - Motto]",
                  "has_audio": true
                },
                {
                  "slug": "baczynski-bez-audio",
                  "title": "Bez audio",
                  "has_audio": false
                },
                {
                  "slug": "baczynski-snieg-jak-wieko-zelazne",
                  "title": "[Śnieg jak wieko żelazne...]",
                  "has_audio": true
                }
              ],
              "media": []
            }
            """.trimIndent(),
            childDetailJsons = listOf(
                """
                {
                  "title": "[1942, 1943, 1944 - Motto]",
                  "url": "https://wolnelektury.pl/katalog/lektura/baczynski-1942-1943-1944-motto-i-dedykacja/",
                  "audio_length": "0:43",
                  "media": [
                    {
                      "url": "/media/book/mp3/baczynski-1942-1943-1944-motto-i-dedykacja.mp3",
                      "director": "Rafał Poławski",
                      "type": "mp3",
                      "name": "Krzysztof Kamil Baczyński, 1942, 1943, 1944, [1942, 1943, 1944 - Motto]",
                      "artist": "Robert Koszucki"
                    }
                  ]
                }
                """.trimIndent(),
                """
                {
                  "title": "[Śnieg jak wieko żelazne...]",
                  "url": "https://wolnelektury.pl/katalog/lektura/baczynski-snieg-jak-wieko-zelazne/",
                  "audio_length": "3:55",
                  "media": [
                    {
                      "url": "https://wolnelektury.pl/media/book/mp3/baczynski-snieg-jak-wieko-zelazne.mp3",
                      "director": "Rafał Poławski",
                      "type": "mp3",
                      "name": "Krzysztof Kamil Baczyński, 1942, 1943, 1944, [Śnieg jak wieko żelazne...]",
                      "artist": "Robert Koszucki"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals("wolnelektury-baczynski-1942-1943-1944", book.id)
        assertEquals("1942, 1943, 1944", book.title)
        assertEquals(2, book.chapters.size)
        assertEquals("[1942, 1943, 1944 - Motto]", book.chapters[0].title)
        assertEquals("[Śnieg jak wieko żelazne...]", book.chapters[1].title)
        assertEquals("baczynski-1942-1943-1944-motto-i-dedykacja-1", book.chapters[0].id)
        assertEquals(1, book.chapters[0].number)
        assertEquals(2, book.chapters[1].number)
        assertEquals("Robert Koszucki", book.chapters[0].reader)
        assertEquals("Rafał Poławski", book.chapters[0].director)
        assertEquals(43 + 235, book.totalDurationSeconds)
        assertEquals(
            "https://wolnelektury.pl/media/book/mp3/baczynski-1942-1943-1944-motto-i-dedykacja.mp3",
            book.chapters[0].listenUrl,
        )
    }

    @Test
    fun bookDetail_prefersCatalogPageDescriptionOverFragmentExcerpt() {
        val pageHtml = """
            <main>
              <div class="l-article__overlay abstract" data-max-height="327">
                <p class="paragraph">Talent nie zawsze jest błogosławieństwem.</p>
                <p class="paragraph">Jedno z najbardziej poczytnych opowiadań Bolesława Prusa.</p>
              </div>
            </main>
        """.trimIndent()
        val book = source.parseBookDetail(
            slug = "antek",
            rawJson = """
            {
              "title": "Antek",
              "language": "pol",
              "fragment_data": {
                "html": "<p class=\"paragraph\">Pierwszy raz w życiu uczuł wielką swoją nędzę.</p>"
              },
              "authors": [
                {"name": "Bolesław Prus", "slug": "boleslaw-prus"}
              ],
              "genres": [
                {"name": "Nowela", "slug": "nowela"}
              ],
              "kinds": [
                {"name": "Epika", "slug": "epika"}
              ],
              "epochs": [
                {"name": "Pozytywizm", "slug": "pozytywizm"}
              ],
              "media": []
            }
            """.trimIndent(),
            pageHtml = pageHtml,
        )

        assertEquals(
            "Talent nie zawsze jest błogosławieństwem.\n\nJedno z najbardziej poczytnych opowiadań Bolesława Prusa.",
            book.description,
        )
        assertEquals(listOf("Novella"), book.literaryGenres.map { it.name })
        assertEquals(listOf("Prose"), book.literaryKinds.map { it.name })
        assertEquals(listOf("Positivism"), book.literaryEpochs.map { it.name })
    }

    @Test
    fun durationParser_supportsHourMinuteSecondAndMinuteSecondShapes() {
        assertEquals(31_444, source.parseDurationSeconds("8:44:04"))
        assertEquals(45, source.parseDurationSeconds("0:45"))
        assertEquals(0, source.parseDurationSeconds(null))
    }

    private fun zakazaneCollectionJson(): String =
        """
        {
          "url": "https://wolnelektury.pl/katalog/lektury/zakazane-ksiazki/",
          "books": [
            {
              "title": "Kandyd",
              "author": "François-Marie Arouet (Voltaire / Wolter)",
              "slug": "kandyd",
              "has_audio": true,
              "genre": "Powiastka filozoficzna",
              "kind": "Epika",
              "epoch": "Oświecenie",
              "url": "https://wolnelektury.pl/katalog/lektura/kandyd/"
            },
            {
              "title": "Hamlet",
              "author": "William Shakespeare (Szekspir)",
              "slug": "hamlet",
              "has_audio": true,
              "genre": "Tragedia",
              "kind": "Dramat",
              "epoch": "Renesans",
              "url": "https://wolnelektury.pl/katalog/lektura/hamlet/"
            },
            {
              "title": "Pan Tadeusz",
              "author": "Adam Mickiewicz",
              "slug": "pan-tadeusz",
              "has_audio": true,
              "genre": "Epos",
              "kind": "Epika",
              "epoch": "Romantyzm",
              "url": "https://wolnelektury.pl/katalog/lektura/pan-tadeusz/"
            }
          ]
        }
        """.trimIndent()

    private fun popularAudiobooksHtml(): String =
        """
        <div id="book-list">
          <article class="l-books__item" data-pop="-1008">
            <h2 class="s"><a href="/katalog/lektura/pan-tadeusz/">Pan Tadeusz</a></h2>
          </article>
          <article class="l-books__item" data-pop="-787">
            <h2 class="s"><a href="/katalog/lektura/kandyd/">Kandyd</a></h2>
          </article>
          <article class="l-books__item" data-pop="-704">
            <h2 class="s"><a href="/katalog/lektura/hamlet/">Hamlet</a></h2>
          </article>
        </div>
        """.trimIndent()

    private fun chlopiParentJson(): String =
        """
        {
          "title": "Chłopi",
          "url": "https://wolnelektury.pl/katalog/lektura/chlopi/",
          "language": "pol",
          "audio_length": "",
          "authors": [
            {"name": "Władysław Stanisław Reymont", "slug": "wladyslaw-stanislaw-reymont"}
          ],
          "genres": [
            {"name": "Powieść", "slug": "powiesc"}
          ],
          "kinds": [
            {"name": "Epika", "slug": "epika"}
          ],
          "epochs": [
            {"name": "Pozytywizm", "slug": "pozytywizm"},
            {"name": "Modernizm", "slug": "modernizm"}
          ],
          "children": [
            {
              "slug": "chlopi-czesc-pierwsza-jesien",
              "title": "Chłopi, Część pierwsza - Jesień",
              "has_audio": true
            }
          ],
          "media": []
        }
        """.trimIndent()

    private fun chlopiJesienJson(): String =
        """
        {
          "title": "Chłopi, Część pierwsza - Jesień",
          "url": "https://wolnelektury.pl/katalog/lektura/chlopi-czesc-pierwsza-jesien/",
          "audio_length": "",
          "media": [
            {
              "url": "/media/book/mp3/chlopi-czesc-pierwsza-jesien_001_rozdzial-1.mp3",
              "director": "Adam Bień",
              "type": "mp3",
              "name": "01. Władysław Stanisław Reymont, Chłopi, Chłopi, Część pierwsza - Jesień, Rozdział 1",
              "artist": "Joanna Domańska"
            },
            {
              "url": "/media/book/mp3/chlopi-czesc-pierwsza-jesien_002_rozdzial-2.mp3",
              "director": "Adam Bień",
              "type": "mp3",
              "name": "02. Władysław Stanisław Reymont, Chłopi, Chłopi, Część pierwsza - Jesień, Rozdział 2",
              "artist": "Joanna Domańska"
            }
          ]
        }
        """.trimIndent()
}
