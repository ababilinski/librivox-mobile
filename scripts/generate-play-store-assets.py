#!/usr/bin/env python3
"""Compose Play assets from authentic Android captures without changing app UI."""
from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

import resvg_py
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/google-play'
IMAGES = ROOT / 'fastlane/metadata/android/en-US/images'
SOURCES = DOCS / 'source-screenshots/release-2026-10-08'
USAGE_SOURCES = DOCS / 'source-screenshots/usage-2026-10-08'
PHONE = IMAGES / 'phoneScreenshots'
CREAM = '#F4E7C1'
TEAL = '#006973'
BACKGROUND = '#F8F5E9'
ASSETS = [
    ('01-browse.png', '01-browse-audiobooks.png', ('Find your next', 'audiobook')),
    ('02-casting.png', '02-cast-to-speakers-and-tvs.png', ('Cast to speakers', 'and TVs')),
    ('03-player.png', '03-player-controls.png', ('Listen at', 'your own pace')),
    ('04-downloads.png', '04-offline-listening.png', ('Download for', 'offline listening')),
    ('05-library.png', '05-library-progress.png', ('Pick up where', 'you left off')),
    ('06-chapters.png', '06-chapter-list.png', ('Jump to', 'any chapter')),
    ('07-bookmarks.png', '07-bookmarks-and-notes.png', ('Save bookmarks', 'and notes')),
    ('08-sleep-timer.png', '08-sleep-timer.png', ('Set a', 'sleep timer')),
]
FONT_DIR = Path('/System/Library/Fonts/Supplemental')
USAGE_SCENES = {
    '02-casting.png': ('casting', 'casting-volume.png', (465, 253, 859, 1082), (659, 275, 11)),
    '04-downloads.png': ('offline', 'offline-book-playing.png', (246, 115, 758, 1204), (496, 144, 12)),
    '08-sleep-timer.png': ('sleep', 'sleep-timer-15-min.png', (323, 292, 695, 1075), (509, 313, 10)),
}


def render_usage_scenes() -> None:
    """Render source captures as fixed SVG layers inside generated phone scenes."""
    for name, capture, bounds, camera in USAGE_SCENES.values():
        base = DOCS / 'usage-scenes' / f'{name}-phone-base.png'
        with Image.open(base) as photo:
            width, height = photo.size
        with Image.open(USAGE_SOURCES / capture) as screen:
            background = '#%02x%02x%02x' % screen.convert('RGB').getpixel((0, 0))
        x, y, right, bottom = bounds
        camera_x, camera_y, camera_radius = camera
        svg = f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <defs>
    <mask id="display" maskUnits="userSpaceOnUse" x="0" y="0" width="{width}" height="{height}">
      <rect x="{x}" y="{y}" width="{right-x}" height="{bottom-y}" rx="36" fill="white"/>
      <circle cx="{camera_x}" cy="{camera_y}" r="{camera_radius}" fill="black"/>
    </mask>
  </defs>
  <image xlink:href="{name}-phone-base.png" width="{width}" height="{height}"/>
  <g mask="url(#display)">
    <rect x="{x}" y="{y}" width="{right-x}" height="{bottom-y}" fill="{background}"/>
    <image xlink:href="../source-screenshots/usage-2026-10-08/{capture}" x="{x}" y="{y}" width="{right-x}" height="{bottom-y}" preserveAspectRatio="xMidYMid meet"/>
  </g>
</svg>
'''
        vector = DOCS / 'usage-scenes' / f'{name}-screen.svg'
        vector.write_text(svg)
        (DOCS / 'usage-scenes' / f'{name}.png').write_bytes(resvg_py.svg_to_bytes(svg_path=str(vector), resources_dir=str(vector.parent)))


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_DIR / ('Arial Bold.ttf' if bold else 'Arial.ttf')), size)


def image_insert(canvas: Image.Image, source: Path, bounds: tuple[int, int, int, int], radius: int = 28) -> None:
    """Fit the whole capture proportionally. Never stretch, retouch, or add UI."""
    with Image.open(source) as original:
        insert = original.convert('RGB')
    insert.thumbnail((bounds[2] - bounds[0], bounds[3] - bounds[1]), Image.Resampling.LANCZOS)
    x = bounds[0] + (bounds[2] - bounds[0] - insert.width) // 2
    y = bounds[1] + (bounds[3] - bounds[1] - insert.height) // 2
    shadow = Image.new('RGBA', canvas.size)
    ImageDraw.Draw(shadow).rounded_rectangle((x - 3, y + 8, x + insert.width + 3, y + insert.height + 8), radius=radius, fill=(0, 45, 49, 55))
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    canvas.paste(shadow, (0, 0), shadow)
    mask = Image.new('L', insert.size)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, insert.width - 1, insert.height - 1), radius=radius, fill=255)
    canvas.paste(insert, (x, y), mask)


def screenshot(source: Path, output: Path, caption: tuple[str, ...]) -> None:
    canvas = Image.new('RGB', (1080, 1920), BACKGROUND)
    scene = USAGE_SCENES.get(source.name)
    if scene:
        with Image.open(DOCS / 'usage-scenes' / f'{scene[0]}.png') as photo:
            canvas.paste(ImageOps.fit(photo.convert('RGB'), (1080, 1565), method=Image.Resampling.LANCZOS), (0, 355))
    draw = ImageDraw.Draw(canvas)
    if scene:
        draw.rectangle((0, 0, 1080, 355), fill=BACKGROUND)
    draw.ellipse((-340, -410, 620, 390), fill=CREAM)
    if not scene:
        draw.ellipse((810, 1380, 1520, 2130), fill='#DFEFEB')
    for index, line in enumerate(caption):
        draw.text((540, 103 + 85 * index), line, anchor='mt', font=font(76, True), fill=TEAL)
    draw.text((540, 294), 'LibriVox Mobile', anchor='mt', font=font(25), fill=TEAL)
    if not scene:
        image_insert(canvas, source, (104, 369, 976, 1857))
    canvas.save(output, optimize=True)


def feature_graphic() -> None:
    canvas = Image.new('RGB', (1024, 500), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((562, 24, 1000, 476), radius=40, fill='#DCEFEB')
    with Image.open(ROOT / 'app/src/main/assets/app-icons/play_store_icon_512.png') as raw:
        icon = raw.convert('RGBA').resize((76, 76), Image.Resampling.LANCZOS)
    canvas.paste(icon.convert('RGB'), (54, 46), icon)
    draw.multiline_text((52, 151), 'LibriVox\nMobile', font=font(60, True), fill=TEAL, spacing=1)
    draw.multiline_text((56, 318), 'Audiobooks on your phone,\nspeakers and TVs.', font=font(29), fill=TEAL, spacing=9)
    image_insert(canvas, SOURCES / '02-casting.png', (570, 43, 804, 457), radius=15)
    image_insert(canvas, SOURCES / '03-player.png', (793, 65, 988, 435), radius=15)
    canvas.save(IMAGES / 'featureGraphic.png', optimize=True)
    canvas.save(DOCS / 'feature-graphic.png', optimize=True)


def main() -> None:
    missing = [source for source, _, _ in ASSETS if not (SOURCES / source).is_file()]
    if missing:
        raise SystemExit('Missing authentic release captures: ' + ', '.join(missing))
    PHONE.mkdir(parents=True, exist_ok=True)
    render_usage_scenes()
    expected = {output for _, output, _ in ASSETS}
    # This directory contains only the generated Play screenshot set.
    for old in PHONE.glob('*.png'):
        if old.name not in expected:
            old.unlink()
    manifest = []
    for source, output, caption in ASSETS:
        screenshot(SOURCES / source, PHONE / output, caption)
        scene = USAGE_SCENES.get(source)
        actual_source = USAGE_SOURCES / scene[1] if scene else SOURCES / source
        manifest.append({'source': str(actual_source.relative_to(ROOT)), 'output': str((PHONE / output).relative_to(ROOT)), 'caption': ' '.join(caption), 'source_sha256': hashlib.sha256(actual_source.read_bytes()).hexdigest(), 'output_sha256': hashlib.sha256((PHONE / output).read_bytes()).hexdigest(), 'usage_scene': f'{scene[0]}.png' if scene else None, 'screen_composition': 'Unmodified Android capture as a proportional SVG image layer inside the phone display' if scene else 'Complete capture fitted proportionally'})
    feature_graphic()
    shutil.copy2(ROOT / 'app/src/main/assets/app-icons/play_store_icon_512.png', IMAGES / 'icon.png')
    shutil.copy2(IMAGES / 'icon.png', DOCS / 'play-store-icon.png')
    shutil.copy2(ROOT / 'app/src/main/assets/app-icons/play_store_icon.svg', DOCS / 'play-store-icon.svg')
    sheet = Image.new('RGB', (1120, 1000), BACKGROUND)
    for index, (_, name, _) in enumerate(ASSETS):
        with Image.open(PHONE / name) as im:
            thumb = im.resize((270, 480), Image.Resampling.LANCZOS)
        sheet.paste(thumb, (10 + (index % 4) * 280, 10 + (index // 4) * 500))
    sheet.save(DOCS / 'phone-screenshot-contact-sheet.png', optimize=True)
    (DOCS / 'asset-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (DOCS / 'asset-alt-text.md').write_text('# Play screenshot descriptions\n\n' + '\n'.join('- ' + a['output'].split('/')[-1] + ': ' + a['caption'] + ('. Generated phone scene with an authentic Android capture inside its display.' if a['usage_scene'] else '. Authentic Android capture.') for a in manifest) + '\n')
    print('Generated eight 1080x1920 screenshots, a 1024x500 feature graphic, and the current 512x512 app icon.')


if __name__ == '__main__':
    main()
