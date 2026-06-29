# iOS Builder Checklist Review

Reviewed source checklist:

`/Users/ababilinski/Documents/Git/ai-glasses-prototypes/audio-player-prototype/docs/ios-liquid-glass-app-builder-checklist.md`

## Current Status

The iOS app is a native SwiftUI + Swift core scaffold with real Liquid Glass surfaces for app-specific controls, but it is not yet full Android feature parity. The builder checklist requires every Android feature unless scope is explicitly cut; the remaining gaps below must stay visible until implemented and tested.

## Completed Or Improved In This Pass

- Native SwiftUI app remains the foundation; no React Native path was introduced.
- Standard SwiftUI structures remain primary: `TabView`, `NavigationStack`, `Form`, `List`, `searchable`, native sheets, menus, sliders, toggles, and `AVRoutePickerView`.
- Custom Liquid Glass is used for app-specific controls: mini-player, Now Playing transport group, detail action cluster, reader floating controls, and setup bottom action.
- Settings now mirrors Android’s workflow grouping: Appearance, Catalog, Downloads, Playback, Read Book, and AirPlay/Diagnostics.
- Settings persist with `UserDefaults`, including onboarding completion, theme, source/language choices, download policy, playback defaults, reader defaults, and mini-player display options.
- Discover now has query field selection, source filtering, language filtering, sort mode, recent search history, offline state, and source/language-aware catalog filtering.
- Home now shows continue/start listening, library shelf, enabled-source shelves, online/offline/downloads-only state, and bottom Liquid Glass mini-player.
- Playback now includes absolute scrubbing, previous/next chapter, skip back/forward, speed control, bookmark creation, AirPlay route control, chapter list, bookmark list, and queue placeholder.
- Library now includes downloads-only filtering, richer search metadata, and context actions for play, details, read, queue, download, and share.
- Book Detail now includes a Liquid Glass action cluster, queue/download/save/share actions, source links for supported source URLs, translation pill hook, and optional cover backdrop behavior.
- Reader now includes settings-driven follow/highlight/text-scale controls in a bottom Liquid Glass control strip.
- Debug URL scheme exists: `librivoxmobile://book/<book-id>`.

## Still Not Full Parity

- Real network catalog clients are still not ported for LibriVox, Lit2Go, Gutendex, and Wolne Lektury; the current iOS catalog client is still seed-data based.
- Downloads are queued in-memory only; background `URLSession`, file persistence, retry/cancel/delete/undo, local cover cache, and offline playback are not complete.
- SwiftData or durable storage for library, bookmarks, queue, progress, search cache, reader assets, and downloads is not complete.
- Discover paging, remote loading, error recovery, detail hydration priority, autocomplete cache limits, merge/dedupe, and query normalization are not complete.
- Full Now Playing parity is still incomplete: sleep timer, feedback persistence, chapter download actions, queue reorder/remove, and lock-screen artwork are not complete.
- Reader parity is still incomplete: Wolne EPUB/audio EPUB/DAISY parsing, timed highlights, TOC, pages, footnotes, selection actions, find-all results, and source formatting are not complete.
- Cast parity is not complete. iOS currently has AirPlay route UI; Google Cast SDK, receiver metadata, local HTTP server, session transfer, volume routing, and diagnostics are still backlog.
- iPad split-view layout, Dynamic Type/Reduce Motion/Increase Contrast passes, dark-mode screenshot pass, offline simulation pass, and warm-cache validation still need manual test notes.

## Acceptance Rule

Do not call the iOS app full Android parity until every row in the external builder checklist has an implementation reference plus a passing unit, integration, UI, simulator, or explicit device-required test note.
