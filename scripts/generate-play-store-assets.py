#!/usr/bin/env python3
from __future__ import annotations

import os
from dataclasses import dataclass
from html import escape
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps
except ImportError as exc:
    raise SystemExit("Install Pillow to generate Play Store PNG assets: python3 -m pip install Pillow") from exc


ROOT = Path(__file__).resolve().parents[1]
PLAY_IMAGES = ROOT / "fastlane/metadata/android/en-US/images"
PHONE_SCREENSHOTS = PLAY_IMAGES / "phoneScreenshots"
FEATURE_PNG = PLAY_IMAGES / "featureGraphic.png"
PLAY_ICON_PNG = PLAY_IMAGES / "icon.png"
GOOGLE_PLAY_DOCS = ROOT / "docs/google-play"
FEATURE_SVG = GOOGLE_PLAY_DOCS / "feature-graphic.svg"
PLAY_ICON_SVG = GOOGLE_PLAY_DOCS / "play-store-icon.svg"
PLAY_ICON_PREVIEW = GOOGLE_PLAY_DOCS / "play-store-icon-preview.png"
SCREENSHOT_SVG_DIR = GOOGLE_PLAY_DOCS / "screenshots"

FEATURE_SIZE = (1024, 500)
SCREENSHOT_SIZE = (1080, 1920)
PLAY_ICON_SIZE = 512
TITLE = "LibriVox\nMobile"
TAGLINE_LINES = ["Listen to LibriVox", "audiobooks on the go"]
BUTTON_LABEL = "Listen anywhere"


@dataclass(frozen=True)
class PhoneSlot:
    source: Path
    x: int
    y: int
    height: int
    clip_id: str


@dataclass(frozen=True)
class StoreScreenshot:
    source: Path
    png_name: str
    svg_name: str
    caption: tuple[str, ...]
    crop_top: int = 132


PHONE_SLOTS = [
    PhoneSlot(ROOT / "docs/screenshots/04-discover.png", 452, 44, 416, "phone-discover"),
    PhoneSlot(ROOT / "docs/screenshots/05-book-detail.png", 618, 24, 452, "phone-detail"),
    PhoneSlot(ROOT / "docs/screenshots/07-player.png", 806, 54, 398, "phone-player"),
]

STORE_SCREENSHOTS = [
    StoreScreenshot(
        ROOT / "docs/screenshots/04-discover.png",
        "01-browse-librivox.png",
        "01-browse-librivox.svg",
        ("Browse LibriVox", "audiobooks"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/05-book-detail.png",
        "02-book-details.png",
        "02-book-details.svg",
        ("Preview books", "and chapters"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/07-player.png",
        "03-player-controls.png",
        "03-player-controls.svg",
        ("Audiobook controls", "that stay handy"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/06-playing-detail.png",
        "04-chapter-list.png",
        "04-chapter-list.svg",
        ("Keep chapters", "in view"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/03-library.png",
        "05-library-progress.png",
        "05-library-progress.svg",
        ("Track your", "listening library"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/01-onboarding-downloads.png",
        "06-offline-listening.png",
        "06-offline-listening.svg",
        ("Save books for", "offline listening"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/02-settings.png",
        "07-settings.png",
        "07-settings.svg",
        ("Tune playback", "and downloads"),
    ),
    StoreScreenshot(
        ROOT / "docs/screenshots/00-onboarding.png",
        "08-onboarding.png",
        "08-onboarding.svg",
        ("LibriVox Mobile", "setup"),
    ),
]

FONT_PATHS = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "/Library/Fonts/Arial.ttf",
]


def load_font(size: int, *, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = FONT_PATHS if bold else list(reversed(FONT_PATHS))
    for raw_path in candidates:
        path = Path(raw_path)
        if not path.exists():
            continue
        if bold and "Bold" not in path.name:
            continue
        try:
            return ImageFont.truetype(str(path), size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def svg_href(path: Path, svg_path: Path) -> str:
    return os.path.relpath(path, svg_path.parent).replace(os.sep, "/")


def phone_size(path: Path, target_height: int) -> tuple[int, int]:
    with Image.open(path) as image:
        return round(target_height * image.width / image.height), target_height


def draw_centered_text(
    draw: ImageDraw.ImageDraw,
    bounds: tuple[int, int, int, int],
    text: str,
    font: ImageFont.FreeTypeFont | ImageFont.ImageFont,
    fill: str,
) -> None:
    left, top, right, bottom = bounds
    bbox = draw.textbbox((0, 0), text, font=font)
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = left + (right - left - width) / 2 - bbox[0]
    y = top + (bottom - top - height) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=fill)


def draw_caption(
    draw: ImageDraw.ImageDraw,
    lines: tuple[str, ...],
    *,
    y: int,
    font: ImageFont.FreeTypeFont | ImageFont.ImageFont,
    fill: str,
    line_height: int,
) -> None:
    total_height = line_height * len(lines)
    current_y = y - total_height // 2
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=font)
        x = (SCREENSHOT_SIZE[0] - (bbox[2] - bbox[0])) / 2 - bbox[0]
        draw.text((x, current_y - bbox[1]), line, font=font, fill=fill)
        current_y += line_height


def screenshot_crop(source: Path, crop_top: int) -> Image.Image:
    image = Image.open(source).convert("RGB")
    width, height = image.size
    crop_height = min(1900, height - crop_top)
    return image.crop((0, crop_top, width, crop_top + crop_height))


def save_screenshot_insert(source: Path, crop_top: int, target_size: tuple[int, int]) -> Image.Image:
    cropped = screenshot_crop(source, crop_top)
    return ImageOps.fit(cropped, target_size, method=Image.Resampling.LANCZOS, centering=(0.5, 0.0))


def clear_generated_phone_screenshots() -> None:
    PHONE_SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    for path in PHONE_SCREENSHOTS.glob("*.png"):
        path.unlink()


def render_captioned_screenshot(asset: StoreScreenshot) -> None:
    width, height = SCREENSHOT_SIZE
    canvas = Image.new("RGB", SCREENSHOT_SIZE, "#F6FFF5")
    draw = ImageDraw.Draw(canvas)

    for y in range(height):
        t = y / (height - 1)
        r = round(246 * (1 - t) + 224 * t)
        g = round(255 * (1 - t) + 246 * t)
        b = round(245 * (1 - t) + 232 * t)
        draw.line([(0, y), (width, y)], fill=(r, g, b))

    accent = Image.new("RGBA", SCREENSHOT_SIZE, (0, 0, 0, 0))
    accent_draw = ImageDraw.Draw(accent)
    accent_draw.ellipse((-230, -120, 410, 520), fill=(42, 129, 91, 34))
    accent_draw.ellipse((720, 1160, 1260, 2050), fill=(255, 181, 122, 28))
    canvas = Image.alpha_composite(canvas.convert("RGBA"), accent).convert("RGB")
    draw = ImageDraw.Draw(canvas)

    caption_font = load_font(64, bold=True)
    draw_caption(draw, asset.caption, y=168, font=caption_font, fill="#123B2D", line_height=72)

    screenshot_bounds = (122, 350, 958, 1846)
    screenshot_size = (
        screenshot_bounds[2] - screenshot_bounds[0],
        screenshot_bounds[3] - screenshot_bounds[1],
    )
    insert = save_screenshot_insert(asset.source, asset.crop_top, screenshot_size)
    radius = 42

    shadow = Image.new("RGBA", (screenshot_size[0] + 48, screenshot_size[1] + 48), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (24, 24, 24 + screenshot_size[0], 24 + screenshot_size[1]),
        radius=radius,
        fill=(18, 54, 40, 54),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    canvas.paste(
        shadow.convert("RGB"),
        (screenshot_bounds[0] - 24, screenshot_bounds[1] - 10),
        shadow.split()[3],
    )

    mask = Image.new("L", screenshot_size, 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle((0, 0, screenshot_size[0], screenshot_size[1]), radius=radius, fill=255)
    canvas.paste(insert, (screenshot_bounds[0], screenshot_bounds[1]), mask)

    outline = Image.new("RGBA", SCREENSHOT_SIZE, (0, 0, 0, 0))
    outline_draw = ImageDraw.Draw(outline)
    outline_draw.rounded_rectangle(screenshot_bounds, radius=radius, outline=(31, 67, 49, 58), width=2)
    canvas = Image.alpha_composite(canvas.convert("RGBA"), outline).convert("RGB")

    canvas.save(PHONE_SCREENSHOTS / asset.png_name, optimize=True)


def render_captioned_screenshot_svg(asset: StoreScreenshot) -> None:
    svg_path = SCREENSHOT_SVG_DIR / asset.svg_name
    width, height = SCREENSHOT_SIZE
    x1, y1, x2, y2 = (122, 350, 958, 1846)
    insert_width = x2 - x1
    insert_height = y2 - y1
    text_nodes = []
    line_height = 72
    start_y = 168 - (line_height * len(asset.caption)) // 2 + 48
    for index, line in enumerate(asset.caption):
        text_nodes.append(
            f'<text x="{width / 2:.0f}" y="{start_y + index * line_height}" text-anchor="middle" '
            f'font-family="Arial, Helvetica, sans-serif" font-size="64" font-weight="700" '
            f'fill="#123B2D">{escape(line)}</text>'
        )

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
    <defs>
        <linearGradient id="background" x1="0" y1="0" x2="0" y2="{height}" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#F6FFF5"/>
            <stop offset="1" stop-color="#E0F6E8"/>
        </linearGradient>
        <clipPath id="appScreenshot">
            <rect x="{x1}" y="{y1}" width="{insert_width}" height="{insert_height}" rx="42" ry="42"/>
        </clipPath>
        <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="14" stdDeviation="14" flood-color="#123628" flood-opacity="0.22"/>
        </filter>
    </defs>
    <rect width="{width}" height="{height}" fill="url(#background)"/>
    <circle cx="90" cy="120" r="320" fill="#2A815B" opacity="0.13"/>
    <circle cx="990" cy="1650" r="330" fill="#FFB57A" opacity="0.12"/>
    {''.join(text_nodes)}
    <rect x="{x1}" y="{y1}" width="{insert_width}" height="{insert_height}" rx="42" fill="#123628" opacity="0.18" filter="url(#softShadow)"/>
    <image x="{x1}" y="{y1}" width="{insert_width}" height="{insert_height}" preserveAspectRatio="xMidYMin slice" clip-path="url(#appScreenshot)" href="{svg_href(asset.source, svg_path)}"/>
    <rect x="{x1}" y="{y1}" width="{insert_width}" height="{insert_height}" rx="42" fill="none" stroke="#1F4331" stroke-opacity="0.23" stroke-width="2"/>
</svg>
"""
    SCREENSHOT_SVG_DIR.mkdir(parents=True, exist_ok=True)
    svg_path.write_text(svg, encoding="utf-8")


def write_play_screenshots() -> None:
    clear_generated_phone_screenshots()
    SCREENSHOT_SVG_DIR.mkdir(parents=True, exist_ok=True)
    for old_svg in SCREENSHOT_SVG_DIR.glob("*.svg"):
        old_svg.unlink()
    for asset in STORE_SCREENSHOTS:
        render_captioned_screenshot(asset)
        render_captioned_screenshot_svg(asset)


def render_feature_png() -> None:
    width, height = FEATURE_SIZE
    image = Image.new("RGB", FEATURE_SIZE, "#E9FFFD")
    draw = ImageDraw.Draw(image)

    for y in range(height):
        t = y / (height - 1)
        r = round(233 * (1 - t) + 213 * t)
        g = round(255 * (1 - t) + 243 * t)
        b = round(253 * (1 - t) + 241 * t)
        draw.line([(0, y), (width, y)], fill=(r, g, b))

    draw.rounded_rectangle((402, 24, 1000, 476), radius=44, fill="#BFE9E6")
    draw.rounded_rectangle((422, 44, 980, 456), radius=36, outline="#78C8C5", width=2)

    title_font = load_font(58, bold=True)
    body_font = load_font(27)
    button_font = load_font(21, bold=True)

    draw.multiline_text((54, 96), TITLE, font=title_font, fill="#004F59", spacing=2)
    draw.multiline_text((58, 248), "\n".join(TAGLINE_LINES), font=body_font, fill="#006973", spacing=8)

    button_bounds = (58, 374, 254, 420)
    draw.rounded_rectangle(button_bounds, radius=23, fill="#007986")
    draw_centered_text(draw, button_bounds, BUTTON_LABEL, button_font, "#FFFFFF")

    for slot in PHONE_SLOTS:
        with Image.open(slot.source) as screenshot:
            screenshot = screenshot.convert("RGB")
            phone_width, phone_height = phone_size(slot.source, slot.height)
            phone = screenshot.resize((phone_width, phone_height), Image.Resampling.LANCZOS)

        radius = 28
        mask = Image.new("L", (phone_width, phone_height), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.rounded_rectangle((0, 0, phone_width, phone_height), radius=radius, fill=255)

        shadow = Image.new("RGBA", (phone_width + 34, phone_height + 34), (0, 0, 0, 0))
        shadow_draw = ImageDraw.Draw(shadow)
        shadow_draw.rounded_rectangle(
            (17, 17, 17 + phone_width, 17 + phone_height),
            radius=radius + 2,
            fill=(0, 63, 70, 68),
        )
        shadow = shadow.filter(ImageFilter.GaussianBlur(10))
        image.paste(shadow.convert("RGB"), (slot.x - 17, slot.y - 12), shadow.split()[3])

        frame = Image.new("RGB", (phone_width + 8, phone_height + 8), "#005965")
        frame_mask = Image.new("L", (phone_width + 8, phone_height + 8), 0)
        frame_draw = ImageDraw.Draw(frame_mask)
        frame_draw.rounded_rectangle(
            (0, 0, phone_width + 8, phone_height + 8),
            radius=radius + 5,
            fill=255,
        )
        image.paste(frame, (slot.x - 4, slot.y - 4), frame_mask)
        image.paste(phone, (slot.x, slot.y), mask)

    PLAY_IMAGES.mkdir(parents=True, exist_ok=True)
    image.save(FEATURE_PNG, optimize=True)


def svg_tspans(lines: list[str], *, x: int, line_height: int) -> str:
    parts = []
    for index, line in enumerate(lines):
        dy = 0 if index == 0 else line_height
        parts.append(f'<tspan x="{x}" dy="{dy}">{escape(line)}</tspan>')
    return "".join(parts)


def render_feature_svg() -> None:
    width, height = FEATURE_SIZE
    defs = [
        """
        <linearGradient id="background" x1="0" y1="0" x2="0" y2="500" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#E9FFFD"/>
            <stop offset="1" stop-color="#D5F3F1"/>
        </linearGradient>
        <filter id="phoneShadow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="10" stdDeviation="8" flood-color="#003F46" flood-opacity="0.28"/>
        </filter>
        """
    ]

    phone_nodes = []
    for slot in PHONE_SLOTS:
        phone_width, phone_height = phone_size(slot.source, slot.height)
        radius = 28
        defs.append(
            f'<clipPath id="{slot.clip_id}">'
            f'<rect x="{slot.x}" y="{slot.y}" width="{phone_width}" height="{phone_height}" rx="{radius}" ry="{radius}"/>'
            "</clipPath>"
        )
        phone_nodes.append(
            f"""
            <g>
                <rect x="{slot.x - 4}" y="{slot.y - 4}" width="{phone_width + 8}" height="{phone_height + 8}" rx="{radius + 5}" fill="#005965" filter="url(#phoneShadow)"/>
                <image x="{slot.x}" y="{slot.y}" width="{phone_width}" height="{phone_height}" preserveAspectRatio="none" clip-path="url(#{slot.clip_id})" href="{svg_href(slot.source, FEATURE_SVG)}"/>
            </g>
            """
        )

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
    <defs>
        {''.join(defs)}
    </defs>
    <rect width="{width}" height="{height}" fill="url(#background)"/>
    <rect x="402" y="24" width="598" height="452" rx="44" fill="#BFE9E6"/>
    <rect x="422" y="44" width="558" height="412" rx="36" fill="none" stroke="#78C8C5" stroke-width="2"/>
    <text x="54" y="148" fill="#004F59" font-family="Arial, Helvetica, sans-serif" font-size="58" font-weight="700">
        {svg_tspans(TITLE.splitlines(), x=54, line_height=58)}
    </text>
    <text x="58" y="274" fill="#006973" font-family="Arial, Helvetica, sans-serif" font-size="27" font-weight="400">
        {svg_tspans(TAGLINE_LINES, x=58, line_height=34)}
    </text>
    <rect x="58" y="374" width="196" height="46" rx="23" fill="#007986"/>
    <text x="156" y="398" fill="#FFFFFF" font-family="Arial, Helvetica, sans-serif" font-size="21" font-weight="700" text-anchor="middle" dominant-baseline="middle">{escape(BUTTON_LABEL)}</text>
    {''.join(phone_nodes)}
</svg>
"""
    FEATURE_SVG.parent.mkdir(parents=True, exist_ok=True)
    FEATURE_SVG.write_text(svg, encoding="utf-8")


def play_icon_svg() -> str:
    return """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
    <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="512" y2="512" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#007986"/>
            <stop offset="1" stop-color="#005965"/>
        </linearGradient>
    </defs>
    <rect width="512" height="512" fill="url(#bg)"/>
    <path fill="#F4E7C1" fill-rule="evenodd" d="M148 120h216c25 0 45 20 45 45v182c0 25-20 45-45 45H148c-25 0-45-20-45-45V165c0-25 20-45 45-45zM171 120h29v272h-29zM254 215v82l71-41z"/>
    <path d="M148 120h216c25 0 45 20 45 45v182c0 25-20 45-45 45H148c-25 0-45-20-45-45V165c0-25 20-45 45-45z" fill="none" stroke="#F8EFD6" stroke-opacity="0.38" stroke-width="10"/>
</svg>
"""


def render_play_icon_png() -> None:
    size = PLAY_ICON_SIZE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)

    for y in range(size):
        for x in range(size):
            tx = x / (size - 1)
            ty = y / (size - 1)
            t = (tx + ty) / 2
            r = round(0 * (1 - t) + 0 * t)
            g = round(121 * (1 - t) + 89 * t)
            b = round(134 * (1 - t) + 101 * t)
            image.putpixel((x, y), (r, g, b, 255))

    mark = Image.new("L", (size, size), 0)
    mark_draw = ImageDraw.Draw(mark)
    mark_draw.rounded_rectangle((103, 120, 409, 392), radius=45, fill=255)
    hole = Image.new("L", (size, size), 0)
    hole_draw = ImageDraw.Draw(hole)
    hole_draw.rectangle((171, 120, 200, 392), fill=255)
    hole_draw.polygon([(254, 215), (325, 256), (254, 297)], fill=255)
    mark = Image.composite(Image.new("L", (size, size), 0), mark, hole)

    cream = Image.new("RGBA", (size, size), "#F4E7C1")
    image.alpha_composite(Image.composite(cream, Image.new("RGBA", (size, size), (0, 0, 0, 0)), mark))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((103, 120, 409, 392), radius=45, outline=(248, 239, 214, 96), width=10)

    PLAY_IMAGES.mkdir(parents=True, exist_ok=True)
    image.save(PLAY_ICON_PNG, optimize=True)


def render_play_icon_svg() -> None:
    GOOGLE_PLAY_DOCS.mkdir(parents=True, exist_ok=True)
    PLAY_ICON_SVG.write_text(play_icon_svg(), encoding="utf-8")


def render_play_icon_preview() -> None:
    icon = Image.open(PLAY_ICON_PNG).convert("RGBA")
    preview = Image.new("RGB", (720, 320), "#F6FFF5")
    draw = ImageDraw.Draw(preview)
    label_font = load_font(22, bold=True)
    small_font = load_font(18)

    positions = [
        ("Raw 512", 36, 52, 180, False),
        ("Play mask", 274, 52, 180, True),
        ("72 px", 520, 76, 72, True),
        ("48 px", 610, 88, 48, True),
    ]
    for label, x, y, size, masked in positions:
        scaled = icon.resize((size, size), Image.Resampling.LANCZOS)
        if masked:
            radius = round(size * 0.30)
            mask = Image.new("L", (size, size), 0)
            mask_draw = ImageDraw.Draw(mask)
            mask_draw.rounded_rectangle((0, 0, size, size), radius=radius, fill=255)
            shadow = Image.new("RGBA", (size + 24, size + 24), (0, 0, 0, 0))
            shadow_draw = ImageDraw.Draw(shadow)
            shadow_draw.rounded_rectangle((12, 12, 12 + size, 12 + size), radius=radius, fill=(0, 0, 0, 55))
            shadow = shadow.filter(ImageFilter.GaussianBlur(8))
            preview.paste(shadow.convert("RGB"), (x - 12, y - 4), shadow.split()[3])
            preview.paste(scaled.convert("RGB"), (x, y), mask)
        else:
            preview.paste(scaled.convert("RGB"), (x, y), scaled.split()[3])
        draw.text((x, y + size + 16), label, font=label_font if size > 72 else small_font, fill="#123B2D")

    preview.save(PLAY_ICON_PREVIEW, optimize=True)


def main() -> None:
    write_play_screenshots()
    render_feature_svg()
    render_feature_png()
    render_play_icon_svg()
    render_play_icon_png()
    render_play_icon_preview()
    print(f"Wrote {FEATURE_SVG.relative_to(ROOT)}")
    print(f"Wrote {FEATURE_PNG.relative_to(ROOT)}")
    print(f"Wrote {PLAY_ICON_SVG.relative_to(ROOT)}")
    print(f"Wrote {PLAY_ICON_PNG.relative_to(ROOT)}")
    print(f"Wrote {PLAY_ICON_PREVIEW.relative_to(ROOT)}")
    print(f"Wrote {PHONE_SCREENSHOTS.relative_to(ROOT)}/*.png")
    print(f"Wrote {SCREENSHOT_SVG_DIR.relative_to(ROOT)}/*.svg")


if __name__ == "__main__":
    main()
