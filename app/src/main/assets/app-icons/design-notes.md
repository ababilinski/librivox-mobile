# LibriVox Mobile icon

The mark is an open book with three sound bars cut out of its right page. It uses purple pages on a lavender background, drawing from the page shapes of PageSync and the tonal treatment of Book Matcher.

The previous teal background and cream closed-book/play mark were too similar to the separate LibriVox Audio Books app. Avoid that silhouette and palette combination in future revisions.

Other directions considered were an open book under headphones, which added a second large contour, and an open book with a play cutout, which was too close to PageSync. The sound bars keep LibriVox Mobile distinct within the same app family.

Adaptive layers are 108x108 dp, with the book inside the centered 66x66 dp safe area. The Play icon has a full square opaque background, an sRGB profile, and no external shadow or baked-in corner mask. The monochrome layer retains the book silhouette and transparent sound-bar cutouts.

Run `python3 scripts/generate-app-icons.py`, followed by `python3 scripts/generate-play-store-assets.py` to refresh the icon in the feature graphic. Dependencies are listed in `scripts/requirements-play-assets.txt`.
