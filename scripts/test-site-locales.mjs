import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

const source = await readFile(new URL("../docs/assets/locale.js", import.meta.url), "utf8");
const base = "https://example.test/librivox-mobile/";

function visit({ path = "", languages = ["en-US"], language = "en", saved = null, blocked = false, file = false } = {}) {
  const current = new URL(path, base);
  const documentEvents = new Map();
  const windowEvents = new Map();
  const result = { redirected: null, remembered: saved, cleaned: null, documentEvents, windowEvents };
  const location = {
    href: current.href, pathname: current.pathname, hash: current.hash,
    protocol: file ? "file:" : "https:",
    replace: value => { result.redirected = value; },
  };
  result.location = location;
  result.links = [
    { href: new URL("features/?lang=en", base).href, dataset: { locale: "en" } },
    { href: new URL("pl/features/?lang=pl", base).href, dataset: { locale: "pl" } },
  ];
  vm.runInNewContext(source, {
    URL, location, navigator: { languages, language },
    localStorage: {
      getItem: () => { if (blocked) throw new Error("Storage disabled"); return saved; },
      setItem: (key, value) => { if (blocked) throw new Error("Storage disabled"); result.remembered = value; },
    },
    history: { replaceState: (_, __, url) => { result.cleaned = url; } },
    document: {
      currentScript: { src: new URL("assets/locale.js", base).href },
      addEventListener: (name, fn) => documentEvents.set(name, fn),
      querySelectorAll: () => result.links,
    },
    window: { addEventListener: (name, fn) => windowEvents.set(name, fn) },
  });
  return result;
}

test("Polish browser preferences choose /pl/ on landing, preserving query and hash", () => {
  assert.equal(visit({ path: "?campaign=books#open-source", languages: ["pl-PL", "en"] }).redirected,
    base + "pl/?campaign=books#open-source");
});
test("language matching uses the first supported browser preference", () => {
  assert.equal(visit({ languages: ["de-DE", "pl-PL", "en"] }).redirected, base + "pl/");
  assert.equal(visit({ languages: ["en-US", "pl-PL"] }).redirected, null);
  assert.equal(visit({ languages: ["fr"] }).redirected, null);
});
test("navigator.language is a fallback when the languages list is unavailable", () => {
  assert.equal(visit({ languages: [], language: "pl-PL" }).redirected, base + "pl/");
});
test("a saved selection takes priority over browser preferences", () => {
  assert.equal(visit({ saved: "en", languages: ["pl"] }).redirected, null);
  assert.equal(visit({ saved: "pl", languages: ["en"] }).redirected, base + "pl/");
});
test("an invalid stored preference falls back to the browser", () => {
  assert.equal(visit({ saved: "de", languages: ["pl"] }).redirected, base + "pl/");
});
test("direct language and deep links stay where their URLs point", () => {
  assert.equal(visit({ path: "pl/", saved: "en" }).redirected, null);
  assert.equal(visit({ path: "pl/features/#library", saved: "en" }).redirected, null);
  assert.equal(visit({ path: "features/#library", saved: "pl" }).redirected, null);
});
test("an English selection defeats automatic Polish routing and cleans the URL", () => {
  const page = visit({ path: "?lang=en&campaign=books#open-source", saved: "pl", languages: ["pl"] });
  assert.equal(page.redirected, null);
  assert.equal(page.remembered, "en");
  assert.equal(page.cleaned, base + "?campaign=books#open-source");
});
test("explicit language queries also switch direct page links", () => {
  assert.equal(visit({ path: "features/?lang=pl#library" }).redirected, base + "pl/features/#library");
  assert.equal(visit({ path: "pl/features/?lang=en#library" }).redirected, base + "features/#library");
});
test("disabled storage still allows explicit choices without redirect loops", () => {
  assert.equal(visit({ blocked: true, languages: ["pl"] }).redirected, base + "pl/");
  assert.equal(visit({ blocked: true, path: "?lang=en", languages: ["pl"] }).redirected, null);
  assert.equal(visit({ blocked: true, path: "pl/", languages: ["pl"] }).redirected, null);
});
test("unusual page paths cannot redirect a language selection to another origin", () => {
  for (const path of ["pl///other.example/?lang=en", "pl/https://other.example/?lang=en"]) {
    const target = new URL(visit({ path }).redirected);
    assert.equal(target.origin, new URL(base).origin);
    assert.ok(target.pathname.startsWith(new URL(base).pathname));
  }
});
test("language links preserve page, query and section and follow hash changes", () => {
  const page = visit({ path: "features/?campaign=books#library" });
  page.documentEvents.get("DOMContentLoaded")();
  assert.equal(page.links[1].href, base + "pl/features/?campaign=books&lang=pl#library");
  page.location.hash = "#listening";
  page.windowEvents.get("hashchange")();
  assert.equal(page.links[0].href, base + "features/?campaign=books&lang=en#listening");
});
test("selecting a language remembers it before navigation", () => {
  const page = visit();
  page.documentEvents.get("click")({ target: { closest: () => page.links[1] } });
  assert.equal(page.remembered, "pl");
});
test("local file previews avoid automatic URL changes", () => {
  assert.equal(visit({ file: true, languages: ["pl"] }).redirected, null);
});
