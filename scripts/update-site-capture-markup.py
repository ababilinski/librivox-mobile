#!/usr/bin/env python3
"""Use truthful locale/theme-aware native captures in every product image."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
CAPTURES = r'(?:browse|casting|player|downloads|library|chapters|bookmarks|sleep-timer)'

for path in DOCS.rglob('*.html'):
    if 'pl' in path.relative_to(DOCS).parts:
        continue
    source = path.read_text()

    def picture(match):
        image = match[0]
        capture = re.search(r'((?:(?:\.\./)+|\./)?assets/product/)(' + CAPTURES + r')-(360|720)\.webp', image)
        if not capture:
            return image
        prefix, scene, _ = capture.groups()
        variants = []
        for theme in ['dark', 'light']:
            variants.append(f'<source data-theme="{theme}" media="(prefers-color-scheme: {theme})" srcset="{prefix}en/{theme}/{scene}-360.webp 360w, {prefix}en/{theme}/{scene}-720.webp 720w" sizes="' + re.search(r'sizes="([^"]+)"', image)[1] + '">')
        image = image.replace(prefix + scene + '-', prefix + 'en/light/' + scene + '-')
        # Dedicated Android capture profile has a native 1080 x 2400 display.
        image = re.sub(r'height="\d+"', 'height="800"', image)
        return '<picture data-app-capture="' + scene + '">' + ''.join(variants) + image + '</picture>'

    # This pattern only matches legacy paths, so rerunning cannot nest pictures.
    source = re.sub(r'<img\b[^>]*\b(?:src|srcset)="[^"\n]*assets/product/' + CAPTURES + r'-[^>]*>', picture, source)
    source = source.replace('Bleak House playing with chapter progress and playback controls in the Android release', 'Bleak House playing with chapter progress and playback controls in the Android app')
    source = source.replace('Android Cast controls connected to a Kitchen speaker, captured on a Pixel 9', 'Android Cast picker showing available audio outputs')
    source = source.replace('Actual Kitchen speaker Cast connection captured on a Pixel 9', 'Native Android audio output and Cast device picker')
    path.write_text(source)

catalog_path = DOCS / 'i18n/pl.json'
catalog = json.loads(catalog_path.read_text())
catalog.update({
    'Bleak House playing with chapter progress and playback controls in the Android app': 'Pan Tadeusz z postępem rozdziału i przyciskami odtwarzania w aplikacji na Androida',
    'Bleak House chapter list in the Android app': 'Lista rozdziałów Pana Tadeusza w aplikacji na Androida',
    'Android audiobook player showing Bleak House and playback controls': 'Odtwarzacz audiobooków na Androidzie z Panem Tadeuszem i przyciskami odtwarzania',
    'Android Cast picker showing available audio outputs': 'Lista dostępnych urządzeń audio w panelu Cast na Androidzie',
    'Native Android audio output and Cast device picker': 'Wybór urządzenia audio i Cast w aplikacji na Androida',
})
catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n')
print('Product screenshots now select the page language and effective appearance.')
