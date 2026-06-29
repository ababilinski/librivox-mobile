# Audio Player Performance Investigation

Date: 2026-06-20

Package: `com.librivox.mobile`

Device: Pixel 9, `google/tokay/tokay:CinnamonBun/CP3A.260508.004.A3/15402111:userdebug/dev-keys`

## Summary

The fresh-launch crash was caused by duplicate `AudioBook.id` values in persisted library data reaching keyed lazy lists. The app now sanitizes `LibraryState` on decode, snapshot, local-download detection, seed-default merge, and DataStore writes. `mergedWithIncoming(...)` now collapses duplicates already present in persisted data and duplicates in incoming catalog data while preserving local downloads, local covers, favorites, library status, and the newest `addedAtMillis`.

Transition responsiveness was improved by removing full-screen lateral route slides for hierarchy changes, restoring the smoother `a5dccd6` mini-player settle thresholds, keeping heavyweight full-player content off the first 12% of the sheet expansion, adding immediate mini-player haptic feedback, avoiding app-private `file://` cover art in Media3/SystemUI metadata, accepting already-downloaded cover art without starting `BookCoverDownloadWorker`, and resuming the current paused item without rebuilding the whole Media3 queue.

Follow-up implemented on 2026-06-20:

- Removed deprecated `CastPlayer.setSessionAvailabilityListener(...)` usage. Cast lifecycle and remote queue loading now rely on the existing Cast SDK `SessionManagerListener` and `SessionTransferCallback` path.
- Removed WorkManager's AndroidX Startup initializer, added lazy WorkManager configuration, and delayed startup-only catalog/search-cache work for 30 seconds after `Application.onCreate()`.
- Added a small process-local staged book detail cache. Discover and Home can navigate to details immediately while durable catalog persistence happens in the background.
- Reworked mini-player to full Now Playing motion again after comparing against the YouTube Music transition references. The opening animation now uses a lightweight motion shell, prefetches artwork through Coil's shared image loader, precomposes the full player after a short idle delay while the mini-player is visible, and keeps the expensive full-player subtree out of the visible opening frames.
- Moved full-player media/bookmark/scrub derivation to a smaller `FullPlayerMedia` presenter at the sheet level. Mini-player, compact player, and full Now Playing now share one `ScrubBarData` model instead of rebuilding bookmark snap data inside each progress bar.
- Deferred restored queue hydration in `AudiobookPlaybackService` so a fresh controller connection no longer runs library snapshot restore, MediaItem creation, and `player.prepare()` directly inside service creation.
- Coalesced notification refreshes and skipped unchanged notification command layout updates, reducing MediaSession/notification churn during playback state flurries.
- Cached the Up Next queue projection in `PlayerStateRepository` so the full queue is not rebuilt on every 500 ms position poll.
- Made Cast route initialization lazy in the app route repository and delayed playback-service Cast callback registration so Cast discovery does not compete with first-frame startup.
- Added `scripts/profile-audio-transitions.sh` for repeatable startup, mini-player open/close, Discover tab, book-open, and manual transition captures.

The crash is fixed in the tested build. Mini-player open, chevron close, middle swipe-down, and lazy Cast route opening test cleanly on device. Startup no longer initializes Cast during `Application`/`Activity` creation, but one startup skipped-frame burst remains before deferred queue hydration and still needs a Perfetto slice-level pass.

## Repro And Profiling Commands

```bash
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew :app:compileDebugKotlin
sh ./gradlew :app:installDebug

adb shell dumpsys gfxinfo com.librivox.mobile reset
adb shell am force-stop com.librivox.mobile
adb shell am start -W -n com.librivox.mobile/.MainActivity
adb logcat -d -v time | rg -i "Choreographer|Skipped [0-9]+ frames|FATAL EXCEPTION|AndroidRuntime|ImageLoader|FileNotFoundException|BookCoverDownloadWorker"
adb shell dumpsys gfxinfo com.librivox.mobile

adb shell screenrecord --time-limit 8 /sdcard/now-playing-open.mp4
adb pull /sdcard/now-playing-open.mp4 build/perf-captures/now-playing-open.mp4

adb shell perfetto --background-wait \
  -o /data/misc/perfetto-traces/audio-startup-final.perfetto-trace \
  -t 10s -b 64mb \
  sched/sched_switch sched/sched_wakeup freq idle am wm gfx view binder_driver dalvik \
  --app com.librivox.mobile
```

Captured artifacts:

- `build/perf-captures/audio-startup-final.perfetto-trace` (28 MB)
- `build/perf-captures/now-playing-open-final.perfetto-trace` (5.9 MB)
- `build/perf-captures/now-playing-open.mp4`
- `build/perf-captures/now-playing-swipe-close.mp4`
- `build/perf-captures/mini-open-final.mp4`
- `build/perf-captures/middle-swipe-close-final.mp4`
- `build/perf-captures/startup-after-30s-defer-*.txt`
- Screenshots in `build/perf-captures/*.png`

## Crash Evidence

Older crash log:

```text
java.lang.IllegalArgumentException: Key "lit2go-168__common-sense" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

Persisted data also showed duplicate IDs such as `1894`, `2348`, `414`, and `421`. The crash was easiest to hit on process-dead launch with existing app data because the UI could collect persisted duplicates before any normal merge/write cleaned them up.

Fixes:

- `AudioBook.kt`: added `dedupedByBookId()` and duplicate-aware merge helpers.
- `LibraryRepository.kt`: emits and writes sanitized state.
- Unit tests cover persisted duplicate repair, incoming duplicate repair, and local state preservation.

## Profile Results

| Scenario | Action | Expected response | Observed result | Frame stats | Trace/log notes | Suspected cause | Proposed next fix |
|---|---|---|---|---|---|---|---|
| Fresh startup, final APK | `am force-stop`, `am start -W` with existing data | No crash, first frame quickly, no duplicate-key exception | No crash. Launch displayed Home. WorkManager startup initializer is removed, restored queue hydration is deferred, and Cast initialization is lazy/delayed. | Latest unlocked cold pass: `TotalTime=715ms`, `WaitTime=717ms`, 65 frames, 13 janky (20.00%), P95 250ms, P99 1050ms. | `after-lazy-cast-startup-unlocked/startup-log.txt` still showed `Skipped 53 frames` before deferred queue preparation. Cast callback registration happened later, after the skipped-frame burst. | Remaining startup work is likely initial Home/mini-player composition, image binding, and service/controller connection before queue hydration. | Open Perfetto around the 0.8-1.0s startup window; inspect first Home composition, image decode, MediaController connection, and any remaining synchronous DataStore/catalog work. |
| Library to book detail | Tap Common Sense from Library list | Immediate press feedback and detail first paint | Detail opens cleanly after route transition change. | 281 frames, 5 janky (1.78%), P95 9ms, P99 38ms, one 300ms histogram outlier | Detail screenshot saved as `library-card-detail-final.png`. | Remaining outlier likely image/list composition or DataStore read during route. | Keep detail state from selected list item in memory so first paint does not depend on repository collection. Analyze trace on the 300ms outlier. |
| Discover tab from Home | Tap Discover bottom nav | Selected-tab state reacts immediately; content does not block tab feedback | Tab opens and content is ready, but frame distribution is heavier. | 384 frames, 5 janky (1.30%), legacy jank 71 (18.49%), P90 28ms, P95 30ms, P99 38ms | Screenshot `discover-tab-final.png`. Autocomplete worker ran shortly after Discover activity in later logs. | Discover grid/image composition and cache/recommendation work compete with navigation. | Keep selected-tab state independent from Discover load; delay autocomplete/cache refresh until after route animation settles; pre-size image cells and only bind visible thumbnails. |
| Discover book to detail | Tap first Discover book card body | Immediate card feedback and detail first paint | Good after route transition change. | 335 frames, 2 janky (0.60%), P95 12ms, P99 30ms | Screenshot `discover-card-detail-final.png`. | Remaining high input-latency count is likely because `openCatalogBook` writes to `LibraryRepository` before navigating. | Add a selected-book detail cache or pass enough detail state through navigation so DataStore upsert can happen after navigation begins. |
| Mini-player to full Now Playing | Tap mini-player cover/title area, not Cast | Sheet begins immediately; no hitch at 10-20% | Latest follow-up shares scrub/bookmark/library-derived state outside `FullPlayerContent` and keeps the lightweight opening shell/precomposed full player path. | Latest warmed pass: 62 frames, 4 janky (6.45%), P95 42ms, P99 109ms, missed vsync 2. No 200-250ms frame bucket. | Recording `after-derived-state-open/now-playing-open.mp4`; contact sheet `after-derived-state-open/now-playing-open-contact.png`. | The original hitch was caused by first composition/layout of full-player state, panels, scrub data, and large artwork during the visible sheet motion. The remaining tail frame is much smaller but still worth tracing. | Keep full-player content warm while the mini-player is visible; next trace should inspect the single 85-109ms tail frame. |
| Close full Now Playing by chevron | Tap down chevron | Collapse starts immediately and settles without spikes | Collapse remains visually smooth after the shared-state change. | 266 frames, 9 janky (3.38%), P95 20ms, P99 57ms, missed vsync 6 | Recording `after-derived-state-close/now-playing-close.mp4`; contact sheet `after-derived-state-close/now-playing-close-contact.png`. | Mostly acceptable; contact sheet shows smooth collapse. | Keep using one sheet animation owner; avoid adding work in close callbacks. |
| Close full Now Playing by middle swipe | Swipe down from album-art/middle content area | Content follows finger and collapses | Gesture collapsed successfully from the album-art/body region and visually tracks downward into the mini-player. | Latest device pass: 263 frames, 3 janky (1.14%), legacy jank 19 (7.22%), P95 25ms, P99 42ms, missed vsync 0. | Recording `after-derived-state-middle-swipe/now-playing-middle-swipe.mp4`; contact sheet `after-derived-state-middle-swipe/now-playing-middle-swipe-contact.png`. | Touch-driven body drag now stays inside low frame buckets. | Continue using parent sheet drag for body regions; recheck with a human finger swipe because ADB swipe is deterministic. |
| Downloaded book Play before cover gate | Tap Play on downloaded Common Sense detail | Sheet opens without work starting mid-animation | Bad: cover worker and media work caused skipped-frame bursts. | 346 frames, 7 janky (2.02%), P99 40ms, with 750/800ms histogram buckets | Logs showed `BookCoverDownloadWorker` plus skipped 45, 30, 47 frames. | `downloadCover()` enqueued work even when a valid local cover existed but metadata did not exactly match. Also rebuilt Media3 queue for current paused item. | Implemented local-cover no-op/stamp and current-item resume fast path. |
| Downloaded book Play after cover gate and fast path | Same action after fixes | Resume/open without cover worker or queue rebuild | Better: no cover worker, no huge 750-1050ms buckets. One media-session skipped-frame burst remains. | 393 frames, 4 janky (1.02%), P95 14ms, P99 36ms | Logs show media focus/session/foreground-service/notification work and one skipped 34-frame burst; no `BookCoverDownloadWorker`. | Remaining cost is MediaSession/notification/service start and possibly glasses notification forwarding. | Avoid re-posting unchanged media notification data; debounce queue/playback metadata updates; inspect `AudiobookPlaybackService` and Media3 session callbacks in Perfetto. |

## 10-20% Now Playing Open Stutter

The first regression came from mounting `FullPlayerContent` too early in the expansion. The earlier smooth build (`a5dccd6`) kept `FullPlayerContent` out of composition until `fraction > 0.12f` and used very responsive settle thresholds:

```kotlin
private const val SheetSettleVelocityThresholdPx = 160f
private const val SheetSettleDragFraction = 0.01f
private const val SheetSettleAnchorFraction = 0.10f

if (fraction > FullPlayerContentMountFraction) FullPlayerContent(...)
```

The intermediate `0.02f` mount experiment tried to pre-warm content earlier, but on-device feel regressed because large artwork, bookmark state, panel state, library collection, and scrub data could all enter the opening path almost immediately after tap.

The final pass replaces the threshold-only approach:

- `OpeningPlayerMotionContent` is a lightweight source-aware shell that stays composed and transparent behind the mini-player, then becomes visible as the sheet expands.
- The shell scales a small already-loaded artwork layer instead of requesting the large hero cover during the first visible frames.
- `PrefetchPlayerArtwork(...)` uses `context.imageLoader.enqueue(...)`, so artwork is warmed through the same Coil image loader used by Compose.
- `FullPlayerContent` is precomposed after a 650ms idle delay while the mini-player is visible. This moves library/bookmark/panel/scrub composition out of the tap-to-open path.
- The real full-player content uses a very short final handoff: `FullPlayerContentMountFraction = 0.96f`, `FullPlayerContentRevealDistance = 0.04f`.
- `HeroIdentity` no longer starts an infinite marquee during the transition. The main chapter title is stable centered text, so the final state does not land mid-scroll with clipped title fragments.

Final device evidence: `now-playing-open-precompose.mp4` shows immediate sheet movement; `now-playing-open-precompose-gfxinfo.txt` shows no missed vsync and no 200-250ms frame. The remaining 40-53ms tail frames are far smaller and should be tackled by moving full-player derived state out of composition if they remain visible in hand testing.

## Code Changes Implemented

- `AudioBook.kt`
  - Added duplicate repair helpers for books and chapters.
  - Preserves downloads, local cover, favorite, library status, newest `addedAtMillis`, and richer catalog metadata when merging.
  - Added `systemArtworkUri(...)` so Media3/SystemUI no longer receives app-private `file://` cover paths.

- `LibraryRepository.kt`
  - Sanitizes decoded state, local-download repaired state, seeded state, snapshots, and writes.
  - Library getters also dedupe defensively.

- `DownloadManager.kt`
  - Treats a valid detected local cover as enough and stamps metadata instead of starting `BookCoverDownloadWorker`.

- `BookDetailScreen.kt`
  - Opens Now Playing before scheduling cover download.
  - If the selected book/chapter is already current, resumes and opens instead of rebuilding the full Media3 queue.

- `AudiobookHost.kt`
  - Replaced hierarchy-wide horizontal slides with fade/scale route motion.

- `PlayerSheet.kt`
  - Adds a lightweight opening motion shell so the sheet begins moving without mounting the full Now Playing subtree.
  - Prefetches artwork via Coil's shared image loader.
  - Precomposes the full player after a short idle delay while the mini-player is visible.
  - Delays the visible full-player handoff until the last 4% of the expansion.
  - Shares `FullPlayerMedia` and `ScrubBarData` across mini, compact, and full-player surfaces so bookmark/scrub/library derivation is warmed outside the visible expansion frames.
  - Removes the hero title's transition-time marquee and uses stable centered text.
  - Keeps predictive back active once the sheet is barely open.
  - Adds immediate mini-player expansion haptic.

- `PlayerStateRepository.kt`
  - Caches the Up Next queue projection and invalidates it only when the timeline revision, current index, item count, or library state changes.
  - Keeps queue metadata repair for generic "Chapter" rows while avoiding a full queue rebuild during every position tick.

- `PlayerSheetState.kt`
  - Restored the `a5dccd6` low settle velocity, drag, and anchor thresholds.

- `AudiobookPlaybackService.kt`
  - Removed deprecated `setSessionAvailabilityListener(...)` and uses the existing Cast SDK session callbacks for remote load/cleanup.
  - Defers initial restored queue preparation until after service creation, builds the restored queue snapshot on IO, and skips the restore if the user starts another item first.
  - Dedupe/debounce notification command layout and refresh work so adjacent Media3 callbacks do not all force notification rebuilds.
  - Delays Cast session callback registration so Cast SDK startup work does not run in the first visible launch window.

- `MainActivity.kt`, `CastRouteRepository.kt`
  - Removes eager `CastContext` initialization from activity startup and route repository construction.
  - Lazily initializes Cast when the user opens or controls the route picker.

- `AudiobookApplication.kt`
  - Removes WorkManager startup initialization and provides lazy WorkManager configuration.
  - Delays startup catalog/WorkManager background setup for 30 seconds after app creation.
  - Adds a small staged book-detail cache for immediate detail navigation.

- `AndroidManifest.xml`
  - Removes only `androidx.work.WorkManagerInitializer` from AndroidX Startup while leaving other startup initializers intact.

- `DiscoverScreen.kt`, `HomeScreen.kt`, `BookDetailScreen.kt`
  - Stage tapped catalog/featured books before navigation, then persist them in the background.

- `AudioBookChapterTest.kt`
  - Added duplicate repair and merge-preservation coverage.

- `scripts/profile-audio-transitions.sh`
  - Adds repeatable ADB capture commands for startup, mini-player open/close, Discover tab, Discover/book card press, and manual transition recordings.

## Remaining High-Impact Work

1. Continue deferring heavy startup work until after first frame.
   - Startup catalog/autocomplete scheduling and seeded catalog refresh now wait 30 seconds after app creation.
   - WorkManager no longer initializes through AndroidX Startup, but already-scheduled work can still reschedule after force-stop/boot.
   - Restored queue hydration now waits until after service creation and runs snapshot/media-item creation off the main thread.
   - Cast route initialization is now lazy and service Cast callback registration is delayed.
   - In Perfetto, inspect `Choreographer#doFrame`, app main thread, Binder, `MediaSessionService`, first Home composition, image decode, and MediaController connection around the remaining skipped 53-frame burst.

2. Monitor selected-book navigation after staged-detail cache.
   - Discover and Home now stage the tapped book in memory, navigate immediately, and persist catalog data in the background.
   - Re-profile Discover book to detail and Home featured book to detail to confirm input-to-first-paint improves.

3. Reduce MediaSession and notification churn.
   - Notification command layout updates are now keyed by mode/media/speed/feedback and notification refreshes are debounced.
   - Downloaded-book resume should be re-profiled to confirm the remaining skipped-frame burst is reduced.
   - Continue inspecting glasses notification forwarding for unchanged session state if frame spikes remain.

4. Continue trimming Now Playing open work.
   - The full-player subtree is now warmed while the mini-player is visible, and library/bookmark/scrub derivation is shared by a smaller sheet-level presenter.
   - If the remaining 85-109ms tail frame is visible in hand testing, move child panel state and large artwork composition into a longer-lived presenter/cache.
   - Keep placeholders stable so late artwork/color updates do not resize or restart layout.

5. Add automated transition profiling.
   - `scripts/profile-audio-transitions.sh` now covers startup, mini-player expand/collapse, Discover tab press, book press, and manual recordings.
   - Add Macrobenchmark/UIAutomator coverage once the manual ADB script captures a stable target sequence.

## Verification Results

```bash
sh ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:installDebug
```

Result: passed and installed on Pixel 9.

Additional focused run:

```bash
sh ./gradlew :app:testDebugUnitTest --tests com.librivox.mobile.model.AudioBookChapterTest
```

Result: passed.

Device validation:

- Fresh launch with existing data no longer crashes.
- Library item opens detail.
- Discover tab opens.
- Discover card opens detail.
- Mini-player cover/title opens full Now Playing with the warmed shell/precomposed full-player path (`now-playing-open-precompose.mp4`).
- Chevron close collapses full Now Playing (`now-playing-close-precompose.mp4`).
- Middle/album-art swipe-down collapses full Now Playing.
- Latest mini-player open capture: `build/perf-captures/after-derived-state-open/now-playing-open.mp4`.
- Latest chevron close capture: `build/perf-captures/after-derived-state-close/now-playing-close.mp4`.
- Latest middle swipe capture: `build/perf-captures/after-derived-state-middle-swipe/now-playing-middle-swipe.mp4`.
- Latest lazy Cast smoke screenshot: `build/perf-captures/cast-lazy-smoke-2.png`.
- Downloaded Common Sense Play no longer starts `BookCoverDownloadWorker` during the transition and no longer exposes private `file://` artwork to SystemUI logs.

Known residual risk:

- Startup jank is improved but still present during the first Home/service connection window.
- MediaSession/foreground-service/notification work should be re-profiled on downloaded book resume after the notification coalescing change.
- Perfetto traces were captured but not deeply parsed in this pass; the next investigation should open the trace files and annotate exact main-thread slices.
