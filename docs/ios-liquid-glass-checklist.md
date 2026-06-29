# iOS Liquid Glass Feature Inventory and Checklist

This is the first planning artifact for the iOS version of LibriVox Mobile. It is intentionally feature-first: the iOS app should not begin as a smaller rewrite unless a later product decision explicitly cuts scope.

Source of truth: the Android app feature checklist currently has 96 feature rows in `docs/feature-checklist.md`. Each feature below must be implemented, adapted to an iOS-native equivalent, or explicitly deferred with a reason before the iOS app is considered parity-complete.

## Current Build Status

- [x] Native iOS scaffold exists under `ios/LibriVoxMobile`.
- [x] Reusable Swift package core exists under `ios/LibriVoxCore`.
- [x] Xcode project generation works with `xcodegen generate` from `ios/`.
- [x] Core package tests pass with `swift test --package-path ios/LibriVoxCore`.
- [x] Simulator build passes with `xcodebuild -quiet -project ios/LibriVoxMobile.xcodeproj -scheme LibriVoxMobile -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
- [ ] Full Android feature parity is not complete yet; the remaining unchecked rows below are the port backlog.

## Baseline Decisions

- Build a separate native SwiftUI app in an `ios/` workspace.
- Use a reusable Swift package core for models, catalog clients, storage services, download services, and playback coordination.
- Use SwiftUI + SwiftData + AVFoundation + MediaPlayer + background `URLSession`.
- Target iOS 26+ first so Liquid Glass can be a primary design language.
- Prefer system SwiftUI structures before custom glass: `TabView`, `NavigationStack`, toolbars, sheets, `searchable`, lists, menus, and standard controls.
- Use custom Liquid Glass only for app-specific surfaces such as mini-player chrome, Now Playing control clusters, reader overlays, and route/download status controls.
- Treat AirPlay as the first iOS route target. Google Cast for iOS is a parity milestone after core playback is stable.

## Architecture Checklist

- [x] Create `ios/LibriVoxMobile` Xcode project and `LibriVoxCore` Swift package.
- [x] Define initial core models: audiobook, chapter, source, catalog tag, library status, download state, bookmark, queue entry, settings, and reader settings.
- [ ] Extend core models for durable progress rows and parsed reader assets.
- [x] Build initial service protocols: catalog source, library store, bookmark store, download service, and playback engine.
- [ ] Build parity service protocols: aggregate catalog, search history store, cover cache, and reader asset store.
- [ ] Map Android persistence to iOS equivalents: DataStore/JSON to SwiftData, preferences to `UserDefaults` or SwiftData settings rows, downloaded files to app support storage.
- [x] Map the first Android media layer to iOS equivalents: `AVQueuePlayer`, speech `AVAudioSession`, `MPNowPlayingInfoCenter`, and `MPRemoteCommandCenter`.
- [ ] Finish media parity for chapter queue rebuilding, interruptions, durable progress, and relaunch resume.
- [ ] Map WorkManager downloads to background `URLSession` plus app relaunch reconciliation.
- [ ] Map Android Cast to iOS route behavior: AirPlay in core v1, Google Cast SDK as parity work, downloaded-media casting only after local-file route constraints are designed.
- [x] Keep React Native and Kotlin Multiplatform as optional future spikes, not the initial app foundation.

## iOS Parity Checklist

### Foundation and App Shell

- [x] Application dependency graph: initialize seed stores, catalog client, downloads, playback, and settings.
- [ ] Add dependency graph services for reader storage, routing, diagnostics, and network monitoring.
- [x] Startup seeded library refresh: seed The Jungle and seed LibriVox records without losing in-memory user state.
- [x] Main app shell: SwiftUI app lifecycle, background-audio entitlement, playback connection, and main environment model.
- [ ] Add deep links and route state to the main app shell.
- [x] Top-level navigation: Home, Library, Discover, Settings, Book Detail, and Book Reader.
- [ ] Add dedicated Downloads navigation and any remaining parity destinations.
- [ ] Shared book transitions: source-aware cover transitions between shelves, lists, detail, and player.
- [ ] Debug open-book intent equivalent: development URL scheme or launch argument for opening a book/detail/reader directly.
- [x] First-run onboarding: source, language, download, and search-cache choices before the main app.
- [ ] Network status monitoring: online/offline state for streaming, cache, downloads-only mode, and user messaging.
- [ ] App theme: Liquid Glass-friendly light/dark/system theme with readable surfaces.
- [ ] Skeleton/loading states: stable loading UI for Home, Library, Discover, Detail, Downloads, and Reader.
- [ ] Haptics and press feedback: iOS-native feedback for buttons, rows, sliders, menus, and long-press actions.
- [x] Time and duration formatting: consistent timecode and duration display across rows, player, downloads, and reader.
- [ ] HTML description rendering: safe catalog description rendering without raw tags.

### Catalog, Discovery, and Search

- [ ] Home dashboard: continue listening, daily picks, source shelves, local highlights, and quick entry points.
- [ ] Daily featured picks: stable per-day recommendations by enabled source.
- [x] Initial Discover search and browse: title, author, chapter, reader, all-fields search, and browse mode against the seed catalog.
- [ ] Finish Discover parity: language, source, sort, paging, loading, and error states.
- [ ] Discover detail hydration: prioritize selected book detail over stale visible-row enrichment.
- [ ] Discover cache state: restore matching query/filter/sort/source results only.
- [ ] Catalog autocomplete cache: offline metadata cache with size limits and automatic caching setting.
- [ ] Multi-source aggregation: LibriVox, Lit2Go, Gutendex, and Wolne Lektury source fan-out.
- [ ] Book merge and deduplication: preserve primary source order, source links, chapters, and local state.
- [ ] Catalog query normalization: sanitize links, punctuation, title/article variants, author/reader/chapter matching.
- [ ] LibriVox catalog source: search, browse, detail hydration, archive covers, readers, genres, language, playable chapters.
- [ ] Lit2Go catalog source: index/detail scraping, chapter MP3s, metadata, real book covers.
- [ ] Gutendex catalog source: Project Gutenberg metadata, playable audio filtering, covers, creators, languages.
- [ ] Wolne Lektury catalog source: Polish audiobooks, metadata, translators, original language, MP3 parts, EPUB/audio EPUB/DAISY assets.
- [ ] Catalog source and language settings: source selector, primary/other public audiobook source grouping, Polish-only Wolne copy, language normalization.
- [ ] Source links and donation links: source pages, donation/support links, and official-source disclosure.
- [ ] Sharing: iOS share sheet for book/source links with stable public URLs where possible.

### Library, Downloads, and Persistence

- [x] Initial Library list: seeded and saved books with open actions.
- [ ] Finish Library parity: local/catalog merge, search, filters, sort, progress, and download state.
- [ ] Downloads-only library mode: user-enabled and offline-driven downloaded-only views with clear exit behavior.
- [ ] Library book action sheet: play, details, read source, queue, download, delete downloads, remove, external links, donate.
- [x] Initial manual download queue action from Book Detail.
- [ ] Finish manual downloads: chapter and whole-book downloads with progress, retry, failure states, and network policy.
- [ ] Book-level source asset downloads: audio EPUB, DAISY, EPUB, cover, and other source assets where supported.
- [ ] Cover downloads and local-first artwork: downloaded covers replace stale remote thumbnails on detail, library, player, and Now Playing.
- [ ] Delete downloads and undo: remove audio/source assets/covers safely and restore if user undoes immediately.
- [ ] Downloads screen: active, queued, failed, downloaded, and completed download management.
- [ ] Download network policy: Wi-Fi-only/manual policy equivalents using iOS network path information.
- [ ] Auto-download while listening: current chapter/book auto-download modes with user-visible setting.
- [ ] Library persistence: SwiftData state for catalog upserts, local downloads, seed defaults, and dedupe.
- [ ] Queue persistence: app-level queue separate from the active `AVQueuePlayer` queue.
- [ ] Bookmark persistence: per-book/chapter/time bookmarks with notes and selected reader text.
- [ ] Search history: deduped recent searches for Discover and suggestions.
- [ ] First-run setting persistence: onboarding choices survive app relaunch.
- [ ] AudioBook model helpers: local file resolution, download counts, source URLs, cast/route eligibility, display titles, read-along asset availability.

### Book Detail and Book Surfaces

- [x] Initial Book Detail screen: title, cover placeholder, source badge, metadata, description, source link, actions, and chapters.
- [ ] Finish Book Detail parity: real cover art, complete source metadata, action sheets, hydration state, and offline state.
- [ ] Book detail cover backdrop: optional artwork backdrop that remains readable under Liquid Glass.
- [ ] Book detail artwork color scheme: optional artwork-derived tinting, disabled by default.
- [ ] Book detail translation metadata: Wolne translated-work pill, translator credit, and original language.
- [x] Initial Book Detail chapter list: playable chapters with reader and duration.
- [ ] Finish chapter list parity: director, download state, selected chapter state, and richer chapter actions.
- [ ] Source donation links: source-specific support links and unofficial app disclosure.
- [ ] Related metadata taps: author/reader/source metadata opens appropriate filtered Discover searches.
- [ ] Local-first artwork in system surfaces: player, lock screen, and AirPlay metadata prefer downloaded cover art.

### Playback and Now Playing

- [x] Initial background playback service equivalent: background audio mode and speech audio session.
- [ ] Finish background playback parity: interruption handling and resume behavior.
- [x] Initial play book/chapter setup: start from a book or selected chapter.
- [ ] Finish queue setup parity: build and persist the expected chapter queue.
- [ ] Progress save and resume: per-book/chapter progress, last book, and resume after relaunch.
- [x] Initial player state repository equivalent: observable playback state for mini-player, full player, and system controls.
- [ ] Connect playback state to reader, library progress, and durable persistence.
- [x] Initial mini player: persistent Liquid Glass mini-player with current book/chapter and play/pause.
- [ ] Finish mini-player parity: progress and route affordance.
- [x] Initial full Now Playing sheet: cover placeholder, metadata, transport, bookmark action, and AirPlay affordance placeholder.
- [ ] Finish full Now Playing parity: chapters, speed, sleep timer, route state, and richer actions.
- [ ] Cover art display modes: cover artwork choices that remain legible with Liquid Glass.
- [ ] Transport controls: play/pause, previous/next chapter, skip back/forward, scrub, disabled states.
- [ ] Playback speed: persisted speed and active speed changes.
- [ ] Sleep timer: timer presets, cancel/extend behavior, player/reader synchronization.
- [x] Initial notification controls equivalent: play, pause, toggle, skip forward, and skip backward through `MPRemoteCommandCenter`.
- [ ] Finish notification controls parity: previous/next chapter, elapsed progress restore, and artwork.
- [ ] Feedback controls: liked/disliked/book or chapter-level feedback behavior.
- [ ] Up Next queue panel: queued chapters/books with reorder or remove where supported.
- [ ] Player chapters panel: current book chapter list with current chapter, progress, download state, and play action.
- [ ] Bookmarks: add/remove bookmark at playhead, notes, bookmark hint, bookmark panel.
- [ ] Wavy progress slider equivalent: iOS-native progress scrubber with bookmark markers or a custom accessible slider.
- [ ] Playback bus equivalent: shared sleep timer and speed state across player and reader.

### Reader and Read-Along

- [ ] Read-along repository: local store for parsed source text, sync maps, and generated/imported reader state.
- [ ] Wolne read-along parser: EPUB/audio EPUB/DAISY parsing, SMIL timing, text blocks, spans, TOC, pages, and footnotes.
- [ ] Repeated title trimming in reader chapters: avoid noisy repeated chapter/title text.
- [x] Initial Reader screen scaffold: source-text surface, chapter sections, search field, and follow-playback toggle.
- [ ] Finish Reader parity: real source text, chapter navigation, playback connection, actions, and loading/failure states.
- [ ] Reader follow playback: auto-scroll/highlight with stable behavior when playback changes.
- [ ] Reader highlight modes: current sentence/paragraph/chapter modes matching Android settings.
- [ ] Reader search and find all: in-reader search with result sheet and jump actions.
- [ ] Reader text selection actions: select/copy/bookmark/read action support using native text selection affordances.
- [ ] Reader table of contents and page controls: source TOC, page labels, scroll progress, and jump controls.
- [ ] Reader footnotes and source formatting: preserve headings, paragraphs, verse, quotes, spans, links, and footnote sheets.
- [ ] Reader settings: follow playback, highlight current text, highlight mode, text scale, speed, and sleep timer.

### Routes, Cast, AirPlay, and Diagnostics

- [ ] AirPlay route UI: native route picker/control surface for iOS v1.
- [ ] Custom Cast route UI: parity milestone for Google Cast SDK for iOS if full Cast parity is required.
- [ ] Google default Cast UI mode: parity milestone for official Cast chooser/controller.
- [ ] Cast media metadata: receiver-safe title, subtitle, artwork, duration, chapter number, narrator, publisher, source.
- [ ] Local Cast HTTP server equivalent: only if Google Cast local/downloaded media support is implemented; local files must become receiver-reachable.
- [ ] Cast session transfer: transfer local to remote and remote to local while preserving chapter, queue, speed, and position.
- [ ] Cast hardware volume: route hardware volume to the active receiver where the platform allows it.
- [ ] Cast settings: UI mode, receiver app id, local media behavior, artwork, diagnostics, reset, and advanced toggles.
- [ ] Cast diagnostics: in-memory/file logs, diagnostics screen, clear/export behavior.
- [ ] Cast options provider equivalent: iOS Cast SDK initialization and settings-derived receiver configuration.
- [ ] Projected glasses player equivalent: no direct iOS equivalent; decide later whether this becomes Apple Watch, CarPlay, Live Activity, StandBy, or is marked Android-only.

### Settings and Preferences

- [x] Initial Settings screen: Appearance, Catalog, Downloads, Playback, Read Book, and Routes sections.
- [ ] Finish Settings parity: nested screens and diagnostics surfaces.
- [ ] Appearance settings: theme, animation/reduce motion policy, artwork color scheme, cover backdrop, cover display mode.
- [x] Initial playback settings: speech audio behavior is fixed in code, and auto-download mode is exposed.
- [ ] Finish playback settings parity: mini-player behavior, remote controls behavior, feedback scope, and default speed.
- [x] Initial download settings: downloads-only mode, auto-download mode, and network policy.
- [ ] Finish download settings parity: cache limits and cleanup controls.
- [x] Initial catalog settings: enabled source/language summary and automatic search caching default.
- [ ] Finish catalog settings parity: source/language editors and cache size management.
- [x] Initial Read Book settings: follow playback, highlight current text, and highlight mode.
- [ ] Finish Read Book settings parity: text scale and any reader-specific speed/sleep timer coupling.
- [ ] Advanced settings placement: keep diagnostics and developer-like controls available but out of the first settings viewport.

### Liquid Glass UI Checklist

- [x] Use system `TabView`/`NavigationStack`/toolbars/search before custom chrome.
- [x] Avoid opaque backgrounds, extra scrims, and hand-built toolbar fills that fight system materials.
- [x] Put search at the correct scope: Discover page search, plus optional global catalog search later.
- [ ] Use semantic icon tint only; do not tint every glass/icon surface for decoration.
- [x] Keep custom glass elements grouped when they visually belong together.
- [ ] Validate mini-player, Now Playing, reader overlays, and route/download controls with light mode, dark mode, Reduce Transparency, Increase Contrast, and Dynamic Type.
- [ ] Preserve content legibility over artwork; artwork-derived color modes stay opt-in.

### Testing and Release Checklist

- [x] Initial unit tests for seed library, seed catalog author/reader search, and duration formatting.
- [ ] Finish unit test parity for catalog parsing, query normalization, merge/dedupe, language/source normalization, settings defaults, bookmarks, and progress.
- [ ] Integration tests for LibriVox search to detail to playback, chapter queue, progress save/resume, download/offline playback, and cache reuse.
- [ ] UI tests for onboarding, Home, Discover, Library, Book Detail, Now Playing, Downloads, Settings, and Reader.
- [ ] Manual device tests for background audio, lock-screen controls, Control Center, AirPlay, interruptions, network loss, long downloads, low storage, app relaunch, VoiceOver, Dynamic Type, and iPad layout.
- [ ] Instruments/profiling checklist for startup, scrolling, catalog parsing, playback transitions, memory, energy, and download throughput.
- [ ] App Store checklist: final bundle id, signing, privacy nutrition labels, unofficial LibriVox disclosure, source attribution, screenshots, support/privacy URLs, and release notes.

## Milestone Order

1. **Foundation:** Xcode project, Swift package core, models, storage, Liquid Glass shell, onboarding, settings skeleton.
2. **Core listening:** LibriVox catalog, Home/Discover/Detail, playback, Now Playing, lock-screen controls, progress, The Jungle seed.
3. **Library and offline:** library, bookmarks, downloads, cover cache, downloads-only mode, search history, cache settings.
4. **Full source parity:** Lit2Go, Gutendex, Wolne Lektury, source/language selector, merge/dedupe, source links, donations.
5. **Reader parity:** reader, Wolne EPUB/audio EPUB/DAISY parsing, highlighting, TOC, search, selected text actions, reader settings.
6. **Route parity:** AirPlay polish first, then Google Cast for iOS, local/downloaded media handling, Cast diagnostics/settings.
7. **Release polish:** accessibility, iPad, performance, App Store metadata, signing, beta distribution, crash/logging strategy.

## Open Decisions After Scaffold

- Current scaffold bundle id is `com.ababilinski.librivoxmobile`; confirm before App Store signing.
- Current scaffold deployment target is iOS 26+; confirm before widening device support.
- Confirm whether Google Cast on iOS is required for first public release or can follow AirPlay.
- Confirm whether projected glasses parity should become Apple Watch, CarPlay, Live Activity, StandBy, or remain Android-only.
- Confirm whether the first iOS release must include every source or can ship LibriVox-first while the checklist remains the parity contract.
