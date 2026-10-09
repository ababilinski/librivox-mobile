# Google Play release listing

Default language: English (United States), en-US
App: LibriVox Mobile
Package: com.librivox.mobile
Developer: BabilinApps
Category: Music & Audio
Pricing: Free, with no in-app purchases or ads
Target audience: 13-15, 16-17, and 18+
Availability: All eligible countries
Endpoint: Ready for review. Do not select Send for review.

## Listing content

The maintained title, short description, full description, and release notes are in `fastlane/metadata/android/en-US/`. The short description names LibriVox, Lit2Go, Project Gutenberg, and Wolne Lektury without mentioning casting. Pricing claims stay in the full description, following Google's short-description guidance.

The full description leads with all four audiobook sources and the app's 100% free access, no ads, no subscriptions, and no in-app purchases. It explains how to enable additional catalogs, credits the projects that provide the recordings, and includes the source-code link, independent-app disclosure, casting requirements, and country-dependent rights notice.

Support: adrian@babilinapps.com
Website: https://ababilinski.github.io/librivox-mobile/
Privacy: https://ababilinski.github.io/librivox-mobile/privacy-policy/
Source code: https://github.com/ababilinski/librivox-mobile

## Graphics

Run `python3 scripts/generate-play-store-assets.py` after capturing the release UI. Eight phone images in `fastlane/metadata/android/en-US/images/phoneScreenshots/` are ordered browse, casting, player, offline downloads, library progress, chapters, bookmarks/notes, and sleep timer. Each is 1080x1920 RGB PNG with the complete authentic Android capture fitted proportionally inside the composition.

Casting, offline listening, and the sleep timer use phone scenes that fit the feature: a desk beside a Google Home speaker, a phone held at a cafe table, and a bedside nightstand. Create the scene first, then insert a different authentic app capture into the phone display. The SVG screen layers keep the captured UI intact, fit it proportionally, and preserve the physical camera cutout. Captions remain outside the phone. Generated scenes are labeled as AI assets in Console; these images illustrate usage rather than document the photographed setting of the device test.

The 1024x500 feature graphic includes the real casting sheet and player. The 512x512 Play icon comes from the current launcher icon asset. `asset-manifest.json` records source and output hashes. `phone-screenshot-contact-sheet.png` is a local review preview, not a store upload.

## Review declarations

Complete all required App content sections using the release code and SDK audit. See `data-safety-draft.md` for the collection review. The maintained policy is `docs/privacy-policy/index.html`; do not reuse the earlier no-collection draft.

Media playback uses a user-started foreground service. Provide a video that shows starting audio, background playback, and notification controls. Record the accepted bundle, actual saved declarations, and remaining blockers in `artifacts/google-play-preparation/completion.md`.

## Release verification

Existing signing configuration is local and must stay out of Git. Build a new AAB. Preserve unrelated checkout changes and do not publish the local commit backlog during website publication. Publish only the scoped policy, review video, and listing assets on top of the current public branch.
