# Google Play Store Listing

Source docs:
- App descriptions: https://support.google.com/googleplay/android-developer/answer/13393723
- Preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311

## Text

Title: LibriVox Mobile

Short description: Stream and cast LibriVox audiobooks on your phone. Completely free.

Full description: `fastlane/metadata/android/en-US/full_description.txt`

Release notes: `fastlane/metadata/android/en-US/changelogs/default.txt`

Asset generator: `scripts/generate-play-store-assets.py`

Asset generator dependency: `scripts/requirements-play-assets.txt`

Play icon: `fastlane/metadata/android/en-US/images/icon.png`

Feature graphic SVG source: `docs/google-play/feature-graphic.svg`

Feature graphic PNG: `docs/google-play/feature-graphic.png`

Play icon SVG source: `docs/google-play/play-store-icon.svg`

Play icon PNG: `docs/google-play/play-store-icon.png`

Screenshot SVG sources: `docs/google-play/screenshots/`

Original uncaptioned screen grabs: `docs/google-play/source-screenshots/`

Reusable UI capture library: `docs/image-resources/ui-captures/`

Reusable UI capture manifest: `docs/image-resources/ui-captures/manifest.json`

Asset alt text: `docs/google-play/asset-alt-text.md`

Screenshot contact sheet: `docs/google-play/phone-screenshot-contact-sheet.png`

## Suggested Console Fields

App category: Music & Audio

Tags: Audiobooks, Books & Reference, Music & Audio

Contact website: project repository or app landing page

Privacy policy: required before production release

Draft privacy policy: `docs/google-play/privacy-policy-draft.md`

Draft Data safety notes: `docs/google-play/data-safety-draft.md`

## Website Pages

GitHub Pages root: `docs/index.html`

Features: `/features/`

Privacy policy: `/privacy-policy/`

Support: `/support/`

Terms of service: `/terms-of-service/`

FAQ: `/faq/`

Resources: `/resources/`

Publishing link helper: `/publishing/`

## Review Notes

- This is an unofficial LibriVox client.
- Do not imply endorsement by LibriVox.
- Keep public-domain/source attribution visible in the listing.
- Confirm final package name, signing, screenshots, and Data safety answers before release.
