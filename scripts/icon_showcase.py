"""Build a standalone icon showcase from the production SVG foregrounds.

The caller owns the artwork and file exports. This module has no third-party
dependencies and does not write files when imported.
"""
from __future__ import annotations

from collections.abc import Callable
from html import escape
import re


def _scope_ids(markup: str, prefix: str) -> str:
    """Keep SVG masks, gradients, and filters local to each repeated icon."""
    identifiers = set(re.findall(r"\bid\s*=\s*[\"']([^\"']+)[\"']", markup))
    for identifier in sorted(identifiers, key=len, reverse=True):
        replacement = f"{prefix}-{identifier}"
        old = re.escape(identifier)
        markup = re.sub(
            rf"(\bid\s*=\s*)([\"']){old}\2",
            lambda match: f"{match[1]}{match[2]}{replacement}{match[2]}",
            markup,
        )
        markup = re.sub(
            rf"url\(\s*([\"']?)#{old}\1\s*\)",
            lambda _: f"url(#{replacement})",
            markup,
        )
        markup = re.sub(
            rf"((?:xlink:)?href\s*=\s*)([\"'])#{old}\2",
            lambda match: f"{match[1]}{match[2]}#{replacement}{match[2]}",
            markup,
        )
    return markup


def showcase_svg(
    foreground: Callable[[bool], str],
    background: str,
    store_viewbox: str,
) -> str:
    """Return a 1600×1100 SVG using the supplied 108×108 production artwork.

    ``foreground(False)`` supplies the color mark; ``foreground(True)`` supplies
    its transparent monochrome silhouette. The themed examples tint that
    silhouette by alpha, so internal transparent details remain intact.
    """
    color_mark = foreground(False)
    mono_mark = foreground(True)
    nodes: list[str] = []

    def text(x: float, y: float, value: str, size: int = 22,
             weight: int = 500, fill: str = "#645C68", anchor: str = "start") -> None:
        nodes.append(
            f'<text x="{x:g}" y="{y:g}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}">{escape(value)}</text>'
        )

    def card(x: int, y: int, width: int, height: int) -> None:
        nodes.append(
            f'<rect x="{x}" y="{y}" width="{width}" height="{height}" '
            'rx="28" fill="#FFFDFA" stroke="#E8E1DC"/>'
        )

    def icon(identifier: str, x: float, y: float, size: float,
             shape: str = "square", viewbox: str = "18 18 72 72",
             surface: str | None = None, tint: str | None = None) -> None:
        surface = background if surface is None else surface
        if shape == "circle":
            mask = f'<circle cx="{x + size / 2:g}" cy="{y + size / 2:g}" r="{size / 2:g}"/>'
        elif shape == "squircle":
            def point(a: float, b: float) -> str:
                return f"{x + size * a:g} {y + size * b:g}"
            mask = (
                f'<path d="M{point(.5, 0)} '
                f'C{point(.86, 0)} {point(1, .14)} {point(1, .5)} '
                f'C{point(1, .86)} {point(.86, 1)} {point(.5, 1)} '
                f'C{point(.14, 1)} {point(0, .86)} {point(0, .5)} '
                f'C{point(0, .14)} {point(.14, 0)} {point(.5, 0)}Z"/>'
            )
        else:
            radius = size * {"square": 0, "rounded": .18, "play": .30}[shape]
            mask = f'<rect x="{x:g}" y="{y:g}" width="{size:g}" height="{size:g}" rx="{radius:g}"/>'
        artwork = _scope_ids(mono_mark if tint is not None else color_mark, f"{identifier}-art")
        definitions = f'<clipPath id="{identifier}-clip">{mask}</clipPath>'
        if tint is not None:
            definitions += (
                f'<filter id="{identifier}-tint" x="-10%" y="-10%" width="120%" height="120%" '
                'color-interpolation-filters="sRGB">'
                f'<feFlood flood-color="{escape(tint, quote=True)}"/>'
                '<feComposite in2="SourceGraphic" operator="in"/></filter>'
            )
            artwork = f'<g filter="url(#{identifier}-tint)">{artwork}</g>'
        nodes.append(
            f'<defs>{definitions}</defs><g clip-path="url(#{identifier}-clip)">'
            f'<svg x="{x:g}" y="{y:g}" width="{size:g}" height="{size:g}" '
            f'viewBox="{escape(viewbox, quote=True)}">'
            f'<rect width="108" height="108" fill="{escape(surface, quote=True)}"/>'
            f'{artwork}</svg></g>'
        )

    nodes.append('<rect width="1600" height="1100" fill="#F5F1EC"/>')
    text(48, 68, "LibriVox Mobile", 44, 750, "#302442")
    text(48, 108, "Color, shape, and wallpaper themes", 24)

    card(48, 144, 350, 388)
    icon("primary", 87, 172, 272, viewbox=store_viewbox)
    text(223, 480, "App icon", 27, 700, "#302442", "middle")
    text(223, 512, "Full-color artwork", 21, anchor="middle")

    card(426, 144, 1126, 388)
    text(460, 190, "Icon shapes", 29, 700, "#302442")
    text(460, 225, "Android launcher and Play Store previews.", 22)
    for index, (label, shape) in enumerate([
        ("Circle", "circle"), ("Squircle", "squircle"),
        ("Rounded square", "rounded"), ("Play mask", "play"),
    ]):
        x = 473 + 262 * index
        icon(f"mask-{shape}", x, 253, 220, shape,
             store_viewbox if shape == "play" else "18 18 72 72")
        text(x + 110, 508, label, 22, 600, "#514757", "middle")

    card(48, 560, 1504, 310)
    text(82, 606, "Wallpaper colors", 29, 700, "#302442")
    text(82, 641, "Light and dark examples using the same monochrome artwork.", 22)
    palettes = [
        ("Lavender", "#E7DFF6", "#493D62", "#3A3049", "#E8DFF6"),
        ("Sage", "#DDEAD8", "#35523D", "#263C2C", "#DCEAD6"),
        ("Clay", "#F7DDD1", "#704838", "#50352B", "#FFDBC9"),
        ("Blue", "#DCE6FA", "#354D70", "#253953", "#D7E4FE"),
    ]
    for index, (label, light_bg, light_fg, dark_bg, dark_fg) in enumerate(palettes):
        x = 94 + index * 372
        text(x + 128, 682, label, 23, 650, "#514757", "middle")
        icon(f"theme-{index}-light", x, 700, 112, "circle", surface=light_bg, tint=light_fg)
        icon(f"theme-{index}-dark", x + 144, 700, 112, "circle", surface=dark_bg, tint=dark_fg)
        text(x + 56, 843, "Light", 20, anchor="middle")
        text(x + 200, 843, "Dark", 20, anchor="middle")

    card(48, 898, 1504, 158)
    text(82, 948, "Small-size check", 28, 700, "#302442")
    text(82, 984, "Shown at actual size in this image.", 21)
    for identifier, x, size, label, tint in [
        ("small-32", 567, 32, "32 px · Color", None),
        ("small-48", 895, 48, "48 px · Color", None),
        ("small-theme", 1223, 48, "48 px · Themed", "#493D62"),
    ]:
        nodes.append(f'<rect x="{x - 24}" y="920" width="96" height="96" rx="20" fill="#F0EBF2"/>')
        icon(identifier, x + 24 - size / 2, 968 - size / 2, size, "circle",
             surface="#E7DFF6" if tint else None, tint=tint)
        text(x + 24, 1040, label, 21, 600, "#514757", "middle")

    return (
        '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
        'width="1600" height="1100" viewBox="0 0 1600 1100" role="img" '
        'aria-labelledby="showcase-title showcase-description">'
        '<title id="showcase-title">LibriVox Mobile icon showcase</title>'
        '<desc id="showcase-description">The app icon in color, four launcher masks, '
        'four light and dark wallpaper palettes, and actual-size small previews.</desc>'
        '<g font-family="Arial, Helvetica, sans-serif">'
        + "".join(nodes) + '</g></svg>\n'
    )
