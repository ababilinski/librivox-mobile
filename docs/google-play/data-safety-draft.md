# Data Safety Draft

This is a repo-grounded draft for Play Console review. Confirm every answer before submitting.

## Observed In This Build

- No account system found.
- No ads, billing, analytics, Crashlytics, Firebase, Sentry, or similar SDKs found in Gradle dependencies.
- Uses network access for catalog search, covers, streaming, downloads, and Cast.
- Stores library, progress, bookmarks, likes, settings, cache, and downloads on the device.
- Android backup/device transfer may include progress, settings, feedback preferences, and DataStore files.
- Cast diagnostics can create local logs that the user may share from the app.

## Permissions To Explain

- Internet and network state: search, streaming, downloads, cover art, and connectivity behavior.
- Foreground service/media playback: background audiobook playback.
- Notifications: media playback notification controls.
- Local network: Cast playback to nearby receivers.

## Suggested Data Safety Position

- Data collected by developer: no, unless future builds add developer-run services.
- Data shared by developer: no, unless future builds add developer-run services.
- Data processed by third parties: verify LibriVox-linked hosts, Google Cast/Media3 Cast, and any browser-opened links.
- Data deletion: users can remove downloads and local library/progress data in the app or through Android app storage controls.

## Before Submission

- Add and publish a privacy policy URL.
- Confirm whether SDKs or linked services require disclosure of device IDs, diagnostics, or app activity.
- Confirm whether Android backup behavior needs to be described in the privacy policy.
- Re-check this file after dependency, permission, or networking changes.
