# Play Store assets

This directory contains the store icon, feature graphic, screenshots, and phone scenes.
Android captures are in `source-screenshots/`; phone scene layers are in `usage-scenes/`.

On macOS, install dependencies with `python3 -m pip install -r scripts/requirements-play-assets.txt` from the repository root. The screenshot generator uses the system Arial fonts.

Run `python3 scripts/generate-app-icons.py` to export the app icons and local previews in `artifacts/icon-previews/`.
Run `python3 scripts/generate-play-store-assets.py` to rebuild the phone scenes, eight store screenshots, and feature graphic.

Store-ready images are copied to `fastlane/metadata/android/en-US/images/`. These commands create files locally and do not upload them.
