"""Prepare proportional release captures and original website cover artwork."""
from pathlib import Path
from hashlib import sha256
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/assets/product"
OUT.mkdir(parents=True, exist_ok=True)
import argparse
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--platform-device', choices=['android-phone', 'ios-iphone', 'ios-ipad'], default='android-phone')
parser.add_argument('--capture-root', type=Path, help='Override the selected platform capture folder.')
parser.add_argument('--locale', action='append', choices=['en', 'pl'])
parser.add_argument('--theme', action='append', choices=['light', 'dark'])
parser.add_argument('--scene', action='append', choices=['browse', 'casting', 'player', 'downloads', 'library', 'chapters', 'bookmarks', 'sleep-timer'])
parser.add_argument('--legacy', action='store_true', help='Recreate the frozen English-only release derivatives.')
args = parser.parse_args()
SCENES = [
    ("01-browse.png", "browse"), ("02-casting.png", "casting"),
    ("03-player.png", "player"), ("04-downloads.png", "downloads"),
    ("05-library.png", "library"), ("06-chapters.png", "chapters"),
    ("07-bookmarks.png", "bookmarks"), ("08-sleep-timer.png", "sleep-timer"),
]
if args.scene:
    SCENES = [(filename, name) for filename, name in SCENES if name in args.scene]
capture_root = args.capture_root or ROOT / 'docs/marketing/captures' / args.platform_device
product_root = OUT if args.platform_device == 'android-phone' else OUT / args.platform_device
variants = [(locale, theme) for locale in (args.locale or ['en', 'pl']) for theme in (args.theme or ['light', 'dark'])]
if args.legacy:
    variants = [(None, None)]
sources = [(locale, theme, filename, name,
            ROOT / 'docs/google-play/source-screenshots/release-2026-10-08' / filename if args.legacy else capture_root / locale / theme / filename)
           for locale, theme in variants for filename, name in SCENES]
missing = [str(source) for *_, source in sources if not source.is_file()]
if missing:
    raise SystemExit('Missing authentic app captures; no language/theme substitution is allowed: ' + ', '.join(missing))
manifest = []
for locale, theme, filename, name, source in sources:
    destination = OUT if args.legacy else product_root / locale / theme
    destination.mkdir(parents=True, exist_ok=True)
    with Image.open(source) as capture:
        for width in (360, 720):
            size = (width, round(capture.height * width / capture.width))
            target = destination / f"{name}-{width}.webp"
            capture.resize(size, Image.Resampling.LANCZOS).save(target, "WEBP", quality=88, method=6)
            manifest.append({"file": str(target.relative_to(OUT)), "source": str(source.relative_to(ROOT)),
                             "platform": args.platform_device, "locale": locale or "en", "theme": theme or "dark",
                             "source_sha256": sha256(source.read_bytes()).hexdigest(),
                             "output_sha256": sha256(target.read_bytes()).hexdigest(),
                             "dimensions": size, "transformation": "Proportional resize and WebP encoding; complete UI preserved."})

with Image.open(ROOT / "docs/google-play/play-store-icon.png") as icon:
    icon.resize((80, 80), Image.Resampling.LANCZOS).save(OUT / "app-icon.webp", "WEBP", quality=94)
manifest.append({"file": "app-icon.webp", "source": "docs/google-play/play-store-icon.png",
                 "source_sha256": sha256((ROOT / "docs/google-play/play-store-icon.png").read_bytes()).hexdigest(),
                 "dimensions": [80, 80], "transformation": "Proportional resize and WebP encoding."})

covers = {
    "moby": ("#193f4c", "#f1d7a4", "HERMAN MELVILLE", ["Moby", "Dick"], "1851", "https://www.gutenberg.org/ebooks/2701"),
    "alice": ("#a74e36", "#fff0d0", "LEWIS CARROLL", ["Alice in", "Wonderland"], "1865", "https://www.gutenberg.org/ebooks/11"),
    "sherlock": ("#35473a", "#e7ce9f", "ARTHUR CONAN DOYLE", ["Sherlock", "Holmes"], "1892", "https://www.gutenberg.org/ebooks/1661"),
    "pride": ("#493b54", "#f3dfba", "JANE AUSTEN", ["Pride and", "Prejudice"], "1813", "https://www.gutenberg.org/ebooks/1342"),
    "tadeusz": ("#405746", "#f3dfb2", "ADAM MICKIEWICZ", ["Pan", "Tadeusz"], "1834", "https://wolnelektury.pl/katalog/lektura/pan-tadeusz/"),
    "huckleberry": ("#315c66", "#f2dfb8", "MARK TWAIN", ["Huckleberry", "Finn"], "1884", "https://etc.usf.edu/lit2go/21/the-adventures-of-huckleberry-finn/"),
    "frankenstein": ("#514c38", "#f1deb2", "MARY SHELLEY", ["Frankenstein", ""], "1818", "https://www.gutenberg.org/ebooks/84"),
    "lalka": ("#784838", "#f4e0bf", "BOLESŁAW PRUS", ["Lalka", ""], "", "https://wolnelektury.pl/katalog/lektura/lalka/"),
}
for name, (bg, ink, author, lines, year, source_url) in covers.items():
    if name == "moby":
        art = ''.join(f'<path d="M-30 {y} Q100 {y-70} 240 {y} T510 {y}" fill="none" stroke="{ink}" stroke-width="2"/>' for y in range(335, 565, 28))
        art += f'<circle cx="240" cy="410" r="108" fill="{ink}"/><path d="M120 436 Q235 325 363 437 Q250 488 120 436" fill="{bg}"/><path d="M310 431l52-52-8 75" fill="{bg}"/>'
    elif name == "alice":
        art = f'<path d="M240 310 L375 445 240 580 105 445Z" fill="none" stroke="{ink}" stroke-width="2"/><circle cx="240" cy="445" r="91" fill="{ink}"/><path d="M222 452c-25-29-23-83-7-81 15 2 25 53 24 70 6-29 24-67 35-57 10 10-9 48-23 64 37 20 33 56-12 59-41 2-53-34-17-55Z" fill="{bg}"/><circle cx="252" cy="462" r="3" fill="{ink}"/>'
    elif name == "pride":
        art = f'<path d="M240 565V350M240 470Q140 470 145 395Q225 390 240 470M240 420Q325 420 332 340Q252 340 240 420" fill="none" stroke="{ink}" stroke-width="3"/>'
        art += ''.join(f'<ellipse cx="240" cy="330" rx="26" ry="58" fill="none" stroke="{ink}" stroke-width="2" transform="rotate({angle} 240 330)"/>' for angle in range(0, 180, 30))
    elif name == "tadeusz":
        art = f'<circle cx="240" cy="410" r="110" fill="none" stroke="{ink}" stroke-width="2"/><path d="M135 455L240 374 345 455M164 437v80h152v-80M228 517v-50h24v50M180 444h26v28h-26M274 444h26v28h-26" fill="none" stroke="{ink}" stroke-width="5"/>'
        art += ''.join(f'<path d="M100 {y}Q240 {y-32} 380 {y}" fill="none" stroke="{ink}" stroke-width="2"/>' for y in range(537, 582, 14))
    elif name == "huckleberry":
        art = ''.join(f'<path d="M75 {y}Q160 {y-24} 240 {y}T405 {y}" fill="none" stroke="{ink}" stroke-width="2"/>' for y in range(340, 582, 30))
        art += f'<path d="M151 466h178l-38 31H183Z" fill="{ink}"/><path d="M228 453V313l77 113h-77" fill="{ink}"/><circle cx="328" cy="333" r="27" fill="{ink}"/>'
    elif name == "frankenstein":
        art = f'<circle cx="240" cy="432" r="112" fill="none" stroke="{ink}" stroke-width="2"/><path d="M210 300h65l-48 109h55l-80 155 17-118h-56Z" fill="{ink}"/>'
    elif name == "lalka":
        art = f'<path d="M127 555V348Q127 285 190 285h100q63 0 63 63v207M140 548h200M140 439h200M240 296v252" fill="none" stroke="{ink}" stroke-width="3"/><path d="M169 373h142v66H169Z" fill="{ink}"/><path d="M145 466h190v22H145Z" fill="{ink}"/>'
    else:
        art = f'<path d="M139 558V382Q139 286 240 286T341 382V558Z" fill="none" stroke="{ink}" stroke-width="3"/><path d="M154 546V382Q154 306 240 306T326 382V546Z" fill="{ink}"/><path d="M240 306v240M154 407h172M154 478h172" stroke="{bg}" stroke-width="9"/><circle cx="281" cy="351" r="21" fill="{bg}"/>'
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="480" height="680" viewBox="0 0 480 680">
<rect width="480" height="680" fill="{bg}"/><rect x="22" y="22" width="436" height="636" fill="none" stroke="{ink}" stroke-opacity=".55"/>
<text x="240" y="83" text-anchor="middle" fill="{ink}" font-family="Georgia,serif" font-size="14" letter-spacing="2">{author}</text>
<text x="240" y="174" text-anchor="middle" fill="{ink}" font-family="Georgia,serif" font-size="52">{lines[0]}</text>
<text x="240" y="235" text-anchor="middle" fill="{ink}" font-family="Georgia,serif" font-size="52">{lines[1]}</text>
{art}<text x="240" y="621" text-anchor="middle" fill="{ink}" font-family="Georgia,serif" font-size="14" letter-spacing="4">{year + ' · ' if year else ''}A CLASSIC</text></svg>'''
    target = OUT / f"cover-{name}.svg"
    target.write_text(svg)
    manifest.append({"file": target.name, "creator": "Original geometric illustration and typesetting for LibriVox Mobile", "license": "MIT", "work_reference": source_url})

def font(size, serif=False):
    candidates = [Path('/System/Library/Fonts/Supplemental/Georgia.ttf') if serif else Path('/System/Library/Fonts/Supplemental/Arial.ttf'), Path('/System/Library/Fonts/Supplemental/Arial.ttf')]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    for name in (["DejaVuSerif.ttf", "Georgia.ttf"] if serif else ["DejaVuSans.ttf", "Arial.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    raise RuntimeError("Social artwork needs Georgia/Arial or DejaVu fonts installed.")

social = Image.new("RGB", (1200, 630), "#fbf8f1")
draw = ImageDraw.Draw(social)
draw.text((68, 64), "LIBRIVOX MOBILE", font=font(20), fill="#8a422b")
draw.text((64, 168), "Great books.", font=font(80, True), fill="#23251f")
draw.text((64, 262), "Read to you.", font=font(80, True), fill="#23251f")
draw.text((68, 410), "Free audiobooks. Open-source software.", font=font(25), fill="#57584f")
draw.rounded_rectangle((68, 494, 330, 554), radius=30, fill="#8a422b")
draw.text((98, 511), "Android + iOS in development", font=font(16), fill="#ffffff")
for x, y, bg, title in [(756, 104, '#193f4c', 'Moby\nDick'), (938, 203, '#a74e36', 'Alice in\nWonderland')]:
    draw.rectangle((x+8, y+12, x+198, y+294), fill='#ddd4c3')
    draw.rectangle((x, y, x+190, y+282), fill=bg)
    draw.rectangle((x+12, y+12, x+178, y+270), outline='#edd5a5', width=1)
    draw.multiline_text((x+24, y+48), title, font=font(25, True), fill='#fff0d0', spacing=8)
    draw.ellipse((x+54, y+152, x+138, y+236), outline='#edd5a5', width=2)
social.save(OUT / "social-preview.png", optimize=True)
manifest.append({"file": "social-preview.png", "creator": "Original LibriVox Mobile typographic composition",
                 "license": "MIT", "dimensions": [1200, 630]})
product_root.mkdir(parents=True, exist_ok=True)
(product_root / "manifest.json").write_text(json.dumps(manifest, indent=2) + '\n')
print(f"Prepared {len(manifest)} website assets in {OUT}")
