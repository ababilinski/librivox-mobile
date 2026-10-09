#!/usr/bin/env python3
"""Export the open-book/audio mark to launcher, store, web, and iOS assets."""
from __future__ import annotations

import io
import json
from pathlib import Path

import resvg_py
from PIL import Image, ImageCms

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/app-icons'
BACKGROUND = '#E7DFFD'
LEFT = '#7761AB'
RIGHT = '#493675'
LEFT_PAGE = 'M29 31Q40 32 51 38V79Q40 72 29 73Q27 73 27 70V34Q27 31 29 31Z'
RIGHT_PAGE = 'M57 38Q68 32 79 31Q81 31 81 34V70Q81 73 79 73Q68 72 57 79Z'
SOUND = (
    'M61 53A2 2 0 0 1 65 53V61A2 2 0 0 1 61 61Z '
    'M67 48A2 2 0 0 1 71 48V66A2 2 0 0 1 67 66Z '
    'M73 51A2 2 0 0 1 77 51V63A2 2 0 0 1 73 63Z'
)


def foreground(monochrome: bool = False) -> str:
    if monochrome:
        return f'<path d="{LEFT_PAGE} {RIGHT_PAGE} {SOUND}" fill="#FFFFFF" fill-rule="evenodd"/>'
    return (
        f'<path d="{LEFT_PAGE}" fill="{LEFT}"/>'
        f'<path d="{RIGHT_PAGE} {SOUND}" fill="{RIGHT}" fill-rule="evenodd"/>'
    )


def svg(body: str, size: int = 108, viewbox: str = '0 0 108 108') -> str:
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{viewbox}">{body}</svg>\n'


def png(markup: str, size: int, output: Path, opaque_rgb: bool = False) -> None:
    raw = resvg_py.svg_to_bytes(svg_string=markup, width=size, height=size)
    with Image.open(io.BytesIO(raw)) as rendered:
        image = rendered.convert('RGB' if opaque_rgb else 'RGBA')
        profile = ImageCms.ImageCmsProfile(ImageCms.createProfile('sRGB')).tobytes()
        output.parent.mkdir(parents=True, exist_ok=True)
        image.save(output, optimize=True, icc_profile=profile)


def vector(paths: list[tuple[str, str]]) -> str:
    nodes = '\n'.join(
        f'    <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{data}" />'
        for color, data in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        + nodes + '\n</vector>\n'
    )


def preview() -> str:
    previews = [('Circle', 'circle'), ('Squircle', 'squircle'), ('Rounded square', 'round'), ('Square', 'square'), ('Themed', 'theme')]
    body = '<rect width="1120" height="380" fill="#F7F5FC"/>'
    for index, (label, shape) in enumerate(previews):
        x = 24 + 218 * index
        clip = f'clip-{index}'
        if shape in ('circle', 'theme'):
            mask = f'<circle cx="{x+88}" cy="126" r="88"/>'
        else:
            radius = {'squircle': 48, 'round': 28, 'square': 0}[shape]
            mask = f'<rect x="{x}" y="38" width="176" height="176" rx="{radius}"/>'
        body += f'<defs><clipPath id="{clip}">{mask}</clipPath></defs>'
        if shape == 'theme':
            content = '<rect width="108" height="108" fill="#DFE4FF"/>' + foreground(True).replace('#FFFFFF', '#29324F')
        else:
            content = f'<rect width="108" height="108" fill="{BACKGROUND}"/>' + foreground()
        body += f'<g clip-path="url(#{clip})"><svg x="{x}" y="38" width="176" height="176" viewBox="18 18 72 72">{content}</svg></g>'
        body += f'<text x="{x+88}" y="248" text-anchor="middle" font-family="Arial" font-size="20" fill="#34274D">{label}</text>'
    for x, size in [(32, 32), (104, 48), (198, 72)]:
        body += f'<svg x="{x}" y="290" width="{size}" height="{size}" viewBox="18 18 72 72"><rect width="108" height="108" fill="{BACKGROUND}"/>{foreground()}</svg>'
        body += f'<text x="{x+size+8}" y="{310+size//3}" font-family="Arial" font-size="15" fill="#34274D">{size}px</text>'
    body += f'<svg x="382" y="280" width="80" height="80" viewBox="0 0 108 108"><rect width="108" height="108" fill="{BACKGROUND}"/>{foreground()}</svg>'
    body += '<text x="474" y="322" font-family="Arial" font-size="18" fill="#34274D">Play: full square, no baked-in mask</text>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="380" viewBox="0 0 1120 380">{body}</svg>\n'


def main() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    background = f'<rect width="108" height="108" fill="{BACKGROUND}"/>'
    play = svg(background + foreground(), 512)
    sources = {
        'adaptive_background.svg': svg(background),
        'adaptive_foreground.svg': svg(foreground()),
        'adaptive_monochrome.svg': svg(foreground(True)),
        'play_store_icon.svg': play,
        'icon_preview_sheet.svg': preview(),
    }
    for name, markup in sources.items():
        (ASSETS / name).write_text(markup)
    png(play, 512, ASSETS / 'play_store_icon_512.png')
    preview_bytes = resvg_py.svg_to_bytes(svg_string=sources['icon_preview_sheet.svg'])
    (ASSETS / 'icon_preview_sheet.png').write_bytes(preview_bytes)
    drawable = ROOT / 'app/src/main/res/drawable'
    (drawable / 'ic_launcher_background.xml').write_text(vector([(BACKGROUND, 'M0 0H108V108H0Z')]))
    (drawable / 'ic_launcher_foreground.xml').write_text(vector([(LEFT, LEFT_PAGE), (RIGHT, RIGHT_PAGE + ' ' + SOUND)]))
    (drawable / 'ic_launcher_monochrome.xml').write_text(vector([('#FFFFFF', LEFT_PAGE + ' ' + RIGHT_PAGE + ' ' + SOUND)]))
    (ROOT / 'docs/favicon.svg').write_text(play)
    png(play, 32, ROOT / 'docs/favicon-32.png')
    png(play, 180, ROOT / 'docs/apple-touch-icon.png', opaque_rgb=True)
    with Image.open(ASSETS / 'play_store_icon_512.png') as icon:
        icon.save(ROOT / 'docs/favicon.ico', sizes=[(16, 16), (32, 32), (48, 48)])
    for folder in [ROOT / 'docs/google-play', ROOT / 'fastlane/metadata/android/en-US/images']:
        name = 'play-store-icon.png' if folder.name == 'google-play' else 'icon.png'
        png(play, 512, folder / name)
    (ROOT / 'docs/google-play/play-store-icon.svg').write_text(play)
    (ROOT / 'docs/google-play/play-store-icon-preview.png').write_bytes(preview_bytes)
    png(play, 1024, ROOT / 'ios/LibriVoxMobile/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png', opaque_rgb=True)
    (ASSETS / 'design-notes.md').write_text('''# LibriVox Mobile icon\n\nThe mark is an open book with three sound bars cut out of its right page. It uses purple pages on a lavender background, drawing from the page shapes of PageSync and the tonal treatment of Book Matcher.\n\nThe previous teal background and cream closed-book/play mark were too similar to the separate LibriVox Audio Books app. Avoid that silhouette and palette combination in future revisions.\n\nOther directions considered were an open book under headphones, which added a second large contour, and an open book with a play cutout, which was too close to PageSync. The sound bars keep LibriVox Mobile distinct within the same app family.\n\nAdaptive layers are 108x108 dp, with the book inside the centered 66x66 dp safe area. The Play icon has a full square opaque background, an sRGB profile, and no external shadow or baked-in corner mask. The monochrome layer retains the book silhouette and transparent sound-bar cutouts.\n\nRun `python3 scripts/generate-app-icons.py`, followed by `python3 scripts/generate-play-store-assets.py` to refresh the icon in the feature graphic. Dependencies are listed in `scripts/requirements-play-assets.txt`.\n''')
    (ASSETS / 'icon-design.json').write_text(json.dumps({'metaphor': 'Open book with sound bars', 'background': BACKGROUND, 'foreground': [LEFT, RIGHT], 'adaptive_viewport': [108, 108], 'safe_zone': [21, 21, 87, 87], 'mark_bounds': [27, 31, 81, 79], 'play_export': {'size': [512, 512], 'mode': 'RGBA', 'color_profile': 'sRGB', 'background_opaque': True}, 'generator': 'scripts/generate-app-icons.py'}, indent=2) + '\n')
    print('Generated Android adaptive, monochrome, Play, website, and iOS icon assets.')


if __name__ == '__main__':
    main()
