# Google Play data handling review

Reviewed October 8, 2026 against this checkout. The release resolves Media3 Cast 1.10.1 and Google Cast Android Sender SDK 22.1.0.

## Collection

Do not declare that the app collects no data. Google Cast collects anonymous app interactions and SDK/session information for analytics. The SDK cannot be opted out of and its logs are not ephemeral request processing. Google encrypts its logs in transit; users and the developer cannot delete them.

Online catalog search sends search terms to enabled catalog services. Streaming, downloads, metadata, and artwork requests expose the requested resource and normal network connection information to their hosts. Do not assume that independent hosts process every request ephemerally or retain no logs.

The proposed Console data types are App activity / App interactions (Cast, analytics) and App activity / In-app search history (catalog searches, app functionality). Reconcile exact Console labels and any additional data types with the final release dependency/endpoint audit.

## Sharing and security

Google describes Cast logs as anonymous. Play's anonymous-data sharing exception applies only to fully anonymous data. Catalog requests, choosing a Cast receiver, external links, and the diagnostic share sheet are user-initiated flows; document the relevant sharing exception rather than declaring that nothing leaves the device.

Cast SDK logging uses encrypted connections. Catalogs primarily use HTTPS. The local Cast bridge can serve audio/artwork over HTTP. Do not claim all off-device data is encrypted without evaluating every disclosed flow.

## Local data and deletion

No app account, ads, billing, or developer-operated analytics backend is present. Library, progress, bookmarks/notes, likes, settings, downloads, caches, and optional diagnostic logs remain local unless included in Android backup or explicitly shared. Local-only data is outside the collection label. Users can delete downloads/logs or clear app storage. There is no account-deletion feature to declare.

## Sources

- https://developers.google.com/cast/docs/android_sender/data_disclosure
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://ababilinski.github.io/librivox-mobile/privacy-policy/

Record the actual saved Console responses and supporting evidence in the release completion record. This file is preparation evidence, not proof that the Console forms were submitted.
