#!/usr/bin/env python3
"""Generate static Polish pages from English pages and the reviewed string catalog."""
import argparse
import html
import json
import posixpath
import re
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
CATALOG = DOCS / "i18n/pl.json"
SITE = "https://ababilinski.github.io/librivox-mobile/"
PAGES = [Path("index.html"), *sorted(p.relative_to(DOCS) for p in DOCS.glob("*/index.html") if p.parent.name != "pl"), Path("404.html")]
ATTR = re.compile(r'([:\w-]+)\s*=\s*([\"\'])(.*?)\2', re.S)


def normalized(value):
    return " ".join(html.unescape(value).split())


def route(page):
    return str(page).removesuffix("index.html")


def relative(target, page):
    trailing = target.endswith("/") or target == ""
    result = posixpath.relpath(target or ".", str(page.parent))
    return result + ("/" if trailing else "")


def prepare_english(page):
    source = (DOCS / page).read_text()
    source = re.sub(r'\s*<!-- locale metadata -->.*?<!-- /locale metadata -->', "", source, flags=re.S)
    source = re.sub(r'\s*<link rel="canonical"[^>]*>', "", source)
    url = SITE + route(page)
    alternate = SITE + "pl/" + route(page)
    metadata = f'''\n    <!-- locale metadata -->
    <link rel="canonical" href="{url}">
    <link rel="alternate" hreflang="en" href="{url}">
    <link rel="alternate" hreflang="pl" href="{alternate}">
    <link rel="alternate" hreflang="x-default" href="{url}">
    <meta property="og:locale" content="en_US">
    <meta property="og:locale:alternate" content="pl_PL">
    <!-- /locale metadata -->'''
    source = re.sub(r'[ \t]*</head>', metadata + "\n  </head>", source)
    if "assets/locale.js" not in source:
        script = relative("assets/locale.js", page)
        source = re.sub(r'(<meta charset="utf-8">)', rf'\1\n    <script src="{script}"></script>', source, count=1)
    # Replacing this small shared block keeps the generated language links current.
    source = re.sub(r'\s*<div class="language-switch".*?</div>', "", source, flags=re.S)
    english = relative(route(page), page) + "?lang=en"
    polish = relative("pl/" + route(page), page) + "?lang=pl"
    indent = re.search(r'^([ \t]*)<p class="footer-bottom"', source, re.M).group(1)
    selector = f'''{indent}<div class="language-switch" role="group" aria-label="Website language" translate="no">
{indent}  <a href="{english}" data-locale="en" lang="en" hreflang="en" aria-label="English" aria-current="true">EN</a>
{indent}  <a href="{polish}" data-locale="pl" lang="pl" hreflang="pl" aria-label="Polski">PL</a>
{indent}</div>'''
    source = re.sub(r'^[ \t]*<p class="footer-bottom"', selector + '\n' + indent + '<p class="footer-bottom"', source, count=1, flags=re.M)
    return source


def polish_highlights(source, page):
    """Lead with Polish books and collections while keeping English curation intact."""
    if page == Path("index.html"):
        replacements = {
            "Original Moby-Dick cover illustration": "Original Pan Tadeusz cover illustration",
            "Original illustrated cover for Moby-Dick": "Original illustrated cover for Pan Tadeusz",
            "Original Alice in Wonderland cover illustration": "Original Lalka cover illustration",
            "Original illustrated cover for Alice’s Adventures in Wonderland": "Original illustrated cover for Lalka",
            "cover-moby.svg": "cover-tadeusz.svg",
            "cover-alice.svg": "cover-lalka.svg",
            "Moby-Dick": "Pan Tadeusz",
            "Herman Melville": "Adam Mickiewicz",
            "Alice’s Adventures in Wonderland": "Lalka",
            "Lewis Carroll": "Bolesław Prus",
            "https://www.gutenberg.org/ebooks/2701": "https://wolnelektury.pl/katalog/lektura/pan-tadeusz/",
            "https://www.gutenberg.org/ebooks/11": "https://wolnelektury.pl/katalog/lektura/lalka/",
        }
        for original, replacement in replacements.items():
            source = source.replace(original, replacement)
    elif page == Path("sources/index.html"):
        pattern = r'<article\b[^>]*\bid="(?:librivox|lit2go|wolne-lektury)"[^>]*>[\s\S]*?</article>'
        cards = {re.search(r'\bid="([^"]+)"', match[0])[1]: match[0] for match in re.finditer(pattern, source)}
        cards["librivox"] = cards["librivox"].replace(" source-tile--wide", "", 1)
        cards["wolne-lektury"] = cards["wolne-lektury"].replace('class="source-tile ', 'class="source-tile source-tile--wide ', 1)
        order = {"librivox": "wolne-lektury", "lit2go": "librivox", "wolne-lektury": "lit2go"}
        source = re.sub(pattern, lambda match: cards[order[re.search(r'\bid="([^"]+)"', match[0])[1]]], source)
        source = source.replace('source-primary" aria-labelledby="librivox-title"', 'source-primary" aria-labelledby="wolne-title"', 1)
        source = source.replace('<a href="#librivox">LibriVox</a>', '<a href="#wolne-lektury">Wolne Lektury</a>', 1)
    return source


def localize(source, page, catalog, missing):
    source = polish_highlights(source, page)
    # Capture locale belongs to the page, independently of appearance preference.
    source = source.replace("assets/product/en/", "assets/product/pl/")
    destination = Path("pl") / page
    skip = False
    output = []

    def translate(value):
        key = normalized(value)
        if not key:
            return value
        translated = catalog.get(key)
        if not isinstance(translated, str) or not translated:
            missing.add(key)
            return value
        leading = re.match(r'^\s*', value).group()
        trailing = re.search(r'\s*$', value).group()
        return leading + html.escape(translated, quote=False) + trailing

    def local_reference(value, selector=False):
        parsed = urlsplit(html.unescape(value))
        if parsed.scheme or parsed.netloc or not parsed.path:
            return value
        target = posixpath.normpath(str(page.parent / parsed.path))
        target_page = Path(target) if target.endswith(".html") else Path(target) / "index.html"
        localized = "pl/" + target if target_page in PAGES and not selector else target
        if parsed.path.endswith("/") or target == ".":
            localized += "/" if not localized.endswith("/") else ""
        adjusted = relative(localized, destination)
        return html.escape(urlunsplit(("", "", adjusted, parsed.query, parsed.fragment)), quote=True)

    for segment in re.split(r'(<[^>]+>)', source):
        if segment.startswith("<"):
            if re.match(r'<(?:script|style)\b', segment, re.I): skip = True
            elif re.match(r'</(?:script|style)\b', segment, re.I): skip = False
            language_link = 'data-locale=' in segment
            metadata_text = bool(re.search(r'(?:name|property)=[\"\'](?:description|og:title|og:description|og:image:alt)[\"\']', segment))

            def attribute(match):
                name, quote, value = match.groups()
                if name == "lang" and segment.startswith("<html"):
                    value = "pl"
                elif name in ("href", "src", "poster"):
                    value = local_reference(value, language_link)
                    if value.startswith(SITE) and 'og:image' not in segment and not ('rel="alternate"' in segment):
                        value = SITE + "pl/" + value[len(SITE):]
                elif name == "srcset":
                    value = ", ".join(local_reference(part.strip().split()[0]) + " " + " ".join(part.strip().split()[1:]) for part in value.split(","))
                elif (name in ("alt", "aria-label", "title") and not language_link) or (name == "content" and metadata_text):
                    value = html.escape(html.unescape(translate(value)), quote=True)
                elif name == "content" and 'property="og:url"' in segment:
                    value = SITE + "pl/" + route(page)
                elif name == "content" and 'property="og:locale"' in segment:
                    value = "pl_PL"
                elif name == "content" and 'property="og:locale:alternate"' in segment:
                    value = "en_US"
                return f'{name}={quote}{value}{quote}'

            segment = ATTR.sub(attribute, segment)
            if language_link:
                segment = re.sub(r' aria-current="true"', "", segment)
                if 'data-locale="pl"' in segment:
                    segment = segment.removesuffix(">") + ' aria-current="true">'
            output.append(segment)
        else:
            output.append(segment if skip else translate(segment))
    return "".join(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check catalog coverage and committed generated output")
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text())
    output = {}
    missing = set()
    for page in PAGES:
        english = prepare_english(page)
        output[DOCS / page] = english
        output[DOCS / "pl" / page] = localize(english, page, catalog, missing)
    if missing:
        raise SystemExit("Missing Polish translations:\n" + "\n".join(sorted(missing)))
    changed = [str(path.relative_to(ROOT)) for path, value in output.items() if not path.exists() or path.read_text() != value]
    if args.check and changed:
        raise SystemExit("Run python3 scripts/localize-site.py to refresh:\n" + "\n".join(changed))
    if not args.check:
        for path, value in output.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
    print(f"Localization {'check' if args.check else 'generation'} passed: {len(PAGES)} English/Polish page pairs, {len(catalog)} catalog entries.")


if __name__ == "__main__":
    main()
