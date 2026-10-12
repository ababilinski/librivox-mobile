#!/usr/bin/env python3
"""Export LibriVox Mobile icons for Android, iOS, and the website."""
from __future__ import annotations

import io
import shutil
from pathlib import Path

import resvg_py
from PIL import Image, ImageCms

from icon_showcase import showcase_svg

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/app-icons'
PREVIEWS = ROOT / 'artifacts/icon-previews'
BACKGROUND = '#0B2054'
CREAM = '#FFF0DA'

# Scale the 1254-unit artwork as one group to keep its proportions.
SOURCE_CENTER = 627
PIVOT = 54
SCALE = 0.057
STORE_VIEWBOX = '16 16 76 76'
MINT_ROTATION = -10
MINT_PIVOT = (915.5, 739.5)

# SVG and VectorDrawable share the same paint coordinates.
GRADIENTS = {
    'coral': ((241, 429, 406, 1037), ('#FF5E69', '#FF696C')),
    'apricot': ((424, 285, 607, 1037), ('#FFAE55', '#FFB45F')),
    'purple': ((624, 389, 787, 1037), ('#A983FF', '#AF8BFC')),
    'mint': ((842, 440, 989, 1039), ('#8BDFAF', '#93E4BA')),
    'headband': ((137, 108, 1113, 580), ('#FFF2DE', '#FFEDD4')),
    'ear-front': ((139, 536, 1107, 870), ('#F8E7CC', '#F4DEBC')),
}


def rounded_rect(x: float, y: float, width: float, height: float, radius: float) -> str:
    right, bottom = x + width, y + height
    return (
        f'M{x + radius:g} {y:g}H{right - radius:g}'
        f'Q{right:g} {y:g} {right:g} {y + radius:g}V{bottom - radius:g}'
        f'Q{right:g} {bottom:g} {right - radius:g} {bottom:g}H{x + radius:g}'
        f'Q{x:g} {bottom:g} {x:g} {bottom - radius:g}V{y + radius:g}'
        f'Q{x:g} {y:g} {x + radius:g} {y:g}Z'
    )


def rectangle(x: float, y: float, width: float, height: float) -> str:
    return f'M{x:g} {y:g}H{x + width:g}V{y + height:g}H{x:g}Z'


HEADBAND = 'M137 580C137 319 351 108 627 108C902 108 1113 319 1113 580H1066C1066 346 871 162 627 162C383 162 184 346 184 580Z'
LEFT_EAR_BACK = 'M146 568C104 568 74 600 74 643V765C74 807 104 840 146 840H185V568Z'
RIGHT_EAR_BACK = 'M1098 568C1140 568 1170 600 1170 643V765C1170 807 1140 840 1098 840H1061V568Z'
LEFT_EAR_FRONT = rounded_rect(139, 536, 84, 334, 40)
RIGHT_EAR_FRONT = rounded_rect(1016, 536, 91, 334, 40)

# Geometry order: coral, taller apricot, purple, then the tilted mint book.
# Each tuple contains its body, central label, spine bands, and paint colors.
BOOKS = [
    {
        'name': 'coral', 'body': rounded_rect(241, 429, 165, 608, 37),
        'label': rounded_rect(279, 630, 91, 196, 17),
        'bands': [rectangle(241, y, 165, h) for y, h in [(507, 23), (546, 21), (895, 22), (934, 21)]],
        'label_color': '#FFE7DD', 'band_color': '#FFE9DD',
        'rotation': 0, 'pivot': (0, 0),
    },
    {
        'name': 'apricot', 'body': rounded_rect(424, 285, 183, 752, 36),
        'label': rounded_rect(470, 495, 90, 308, 17),
        'bands': [rectangle(424, y, 183, 24) for y in [363, 407, 882, 930]],
        'label_color': '#FFEACD', 'band_color': '#DF7938',
        'rotation': 0, 'pivot': (0, 0),
    },
    {
        'name': 'purple', 'body': rounded_rect(624, 389, 163, 648, 36),
        'label': rounded_rect(662, 574, 91, 252, 17),
        'bands': [rectangle(624, y, 163, 22) for y in [473, 917]],
        'label_color': '#E6DAFD', 'band_color': '#E9DEFF',
        'rotation': 0, 'pivot': (0, 0),
    },
    {
        'name': 'mint', 'body': rounded_rect(842, 440, 147, 599, 33),
        'label': rounded_rect(870, 635, 93, 222, 17),
        'bands': [rectangle(842, y, 147, 23) for y in [529, 574, 903, 947]],
        'label_color': '#D1F5E0', 'band_color': '#36A885',
        'rotation': MINT_ROTATION, 'pivot': MINT_PIVOT,
    },
]

# Each shape is (paint, path data, fill rule). Groups preserve the mint tilt.
Shape = tuple[str, str, str]
ArtGroup = tuple[float, tuple[float, float], list[Shape]]


def artwork(monochrome: bool = False) -> list[ArtGroup]:
    headband_paint = '#FFFFFF' if monochrome else 'headband'
    ear_paint = '#FFFFFF' if monochrome else CREAM
    groups: list[ArtGroup] = [
        (0, (0, 0), [(headband_paint, HEADBAND, 'nonzero')]),
        (0, (0, 0), [(ear_paint, LEFT_EAR_BACK, 'nonzero'), (ear_paint, RIGHT_EAR_BACK, 'nonzero')]),
    ]
    for book in BOOKS:
        if monochrome:
            # Four separated book bodies and broad label counters stay readable
            # at launcher size. The thinner decorative bands are omitted.
            paths = [('#FFFFFF', book['body'] + ' ' + book['label'], 'evenodd')]
        else:
            paths = [(book['name'], book['body'], 'nonzero')]
            paths += [(book['band_color'], band, 'nonzero') for band in book['bands']]
            paths += [(book['label_color'], book['label'], 'nonzero')]
        groups.append((book['rotation'], book['pivot'], paths))
    front_paint = '#FFFFFF' if monochrome else 'ear-front'
    groups.append((0, (0, 0), [(front_paint, LEFT_EAR_FRONT, 'nonzero'), (front_paint, RIGHT_EAR_FRONT, 'nonzero')]))
    return groups


def gradient_svg() -> str:
    definitions = []
    for name, (coordinates, colors) in GRADIENTS.items():
        x1, y1, x2, y2 = coordinates
        definitions.append(
            f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
            f'<stop offset="0" stop-color="{colors[0]}"/><stop offset="1" stop-color="{colors[1]}"/></linearGradient>'
        )
    return '<defs>' + ''.join(definitions) + '</defs>'


def foreground(monochrome: bool = False) -> str:
    groups = []
    for rotation, (cx, cy), paths in artwork(monochrome):
        shapes = ''.join(
            f'<path d="{path}" fill="{paint if paint.startswith("#") else "url(#" + paint + ")"}" fill-rule="{rule}"/>'
            for paint, path, rule in paths
        )
        groups.append(f'<g transform="rotate({rotation} {cx} {cy})">{shapes}</g>' if rotation else shapes)
    return (
        ('' if monochrome else gradient_svg())
        + f'<g transform="translate({PIVOT} {PIVOT}) scale({SCALE}) translate(-{SOURCE_CENTER} -{SOURCE_CENTER})">'
        + ''.join(groups) + '</g>'
    )


def svg(body: str, size: int = 108, viewbox: str = '0 0 108 108') -> str:
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{viewbox}"><metadata>LibriVox Mobile icon</metadata>{body}</svg>\n'


def png(markup: str, size: int, output: Path, opaque_rgb: bool = False) -> None:
    raw = resvg_py.svg_to_bytes(svg_string=markup, width=size, height=size)
    with Image.open(io.BytesIO(raw)) as rendered:
        image = rendered.convert('RGB' if opaque_rgb else 'RGBA')
        profile = ImageCms.ImageCmsProfile(ImageCms.createProfile('sRGB')).tobytes()
        output.parent.mkdir(parents=True, exist_ok=True)
        image.save(output, optimize=True, icc_profile=profile)


def vector_path(shape: Shape) -> str:
    paint, path, rule = shape
    fill_rule = 'evenOdd' if rule == 'evenodd' else 'nonZero'
    if paint.startswith('#'):
        return f'    <path android:fillColor="{paint}" android:fillType="{fill_rule}" android:pathData="{path}" />'
    (x1, y1, x2, y2), colors = GRADIENTS[paint]
    return (
        f'    <path android:fillType="{fill_rule}" android:pathData="{path}">\n'
        '        <aapt:attr name="android:fillColor">\n'
        f'            <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">\n'
        f'                <item android:offset="0" android:color="{colors[0]}" />\n'
        f'                <item android:offset="1" android:color="{colors[1]}" />\n'
        '            </gradient>\n'
        '        </aapt:attr>\n'
        '    </path>'
    )


def vector(monochrome: bool = False, background: bool = False) -> str:
    if background:
        content = vector_path((BACKGROUND, 'M0 0H108V108H0Z', 'nonzero'))
    else:
        groups = []
        for rotation, (cx, cy), paths in artwork(monochrome):
            content = '\n'.join(vector_path(shape) for shape in paths)
            if rotation:
                content = f'    <group android:rotation="{rotation}" android:pivotX="{cx}" android:pivotY="{cy}">\n{content}\n    </group>'
            groups.append(content)
        translation = PIVOT - SOURCE_CENTER
        content = (
            f'    <group android:pivotX="{SOURCE_CENTER}" android:pivotY="{SOURCE_CENTER}" '
            f'android:scaleX="{SCALE}" android:scaleY="{SCALE}" '
            f'android:translateX="{translation}" android:translateY="{translation}">\n'
            + '\n'.join(groups) + '\n    </group>'
        )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- LibriVox Mobile icon -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        + content + '\n</vector>\n'
    )


def preview() -> str:
    return showcase_svg(foreground, BACKGROUND, STORE_VIEWBOX)


def main() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    background = f'<rect width="108" height="108" fill="{BACKGROUND}"/>'
    play = svg(background + foreground(), 512, STORE_VIEWBOX)
    sources = {
        'adaptive_background.svg': svg(background),
        'adaptive_foreground.svg': svg(foreground()),
        'adaptive_monochrome.svg': svg(foreground(True)),
        'play_store_icon.svg': play,
    }
    for name, markup in sources.items():
        (ASSETS / name).write_text(markup)
    png(play, 512, ASSETS / 'play_store_icon_512.png')
    preview_markup = preview()
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    (PREVIEWS / 'icon_preview_sheet.svg').write_text(preview_markup)
    (PREVIEWS / 'icon_preview_sheet.png').write_bytes(resvg_py.svg_to_bytes(svg_string=preview_markup))
    drawable = ROOT / 'app/src/main/res/drawable'
    (drawable / 'ic_launcher_background.xml').write_text(vector(background=True))
    (drawable / 'ic_launcher_foreground.xml').write_text(vector())
    (drawable / 'ic_launcher_monochrome.xml').write_text(vector(monochrome=True))
    (ROOT / 'docs/favicon.svg').write_text(play)
    png(play, 32, ROOT / 'docs/favicon-32.png')
    png(play, 180, ROOT / 'docs/apple-touch-icon.png', opaque_rgb=True)
    with Image.open(ASSETS / 'play_store_icon_512.png') as icon:
        icon.save(ROOT / 'docs/favicon.ico', sizes=[(16, 16), (32, 32), (48, 48)])
    for folder in [ROOT / 'docs/google-play', ROOT / 'fastlane/metadata/android/en-US/images']:
        name = 'play-store-icon.png' if folder.name == 'google-play' else 'icon.png'
        shutil.copy2(ASSETS / 'play_store_icon_512.png', folder / name)
    (ROOT / 'docs/google-play/play-store-icon.svg').write_text(play)
    png(play, 1024, ROOT / 'ios/LibriVoxMobile/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png', opaque_rgb=True)
    print('Generated Android, iOS, website, and Play icons. Previews are in artifacts/icon-previews/.')


if __name__ == '__main__':
    main()
