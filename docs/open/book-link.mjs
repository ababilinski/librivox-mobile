const packageName = "com.librivox.mobile";
const knownBooks = {
  "wolnelektury-antek": { title: "Antek", author: "Bolesław Prus" },
  "wolnelektury-katarynka": { title: "Katarynka", author: "Bolesław Prus" },
};

export function sourcePage(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.port) return null;
    const host = url.hostname.replace(/^www\./, "");
    if (!(host === "wolnelektury.pl" && /^\/katalog\/lektura\/[a-z0-9-]+(?:\.html|\/)?$/.test(url.pathname)) &&
        !(host === "librivox.org" && /^\/[a-z0-9-]+\/?$/.test(url.pathname)) &&
        !(host === "etc.usf.edu" && /^\/lit2go\/\d+\/[a-z0-9-]+\/?$/.test(url.pathname))) return null;
    url.search = "";
    url.hash = "";
    return url.href;
  } catch { return null; }
}

export function bookLink(search) {
  if (search.length > 8192) return null;
  const params = new URLSearchParams(search);
  const bookId = params.get("bookId");
  if (!bookId || bookId.length > 512 || /[\u0000-\u001f]/.test(bookId)) return null;
  const chapterId = params.get("chapterId");
  if (chapterId && (chapterId.length > 512 || /[\u0000-\u001f]/.test(chapterId))) return null;
  const slug = bookId.startsWith("wolnelektury-") ? bookId.slice("wolnelektury-".length) : null;
  const source = sourcePage(params.get("source")) ||
    (slug && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) ? `https://wolnelektury.pl/katalog/lektura/${slug}/` : null);
  const query = new URLSearchParams({ bookId });
  if (chapterId) query.set("chapterId", chapterId);
  if (source) query.set("source", source);
  const fallback = source || "https://ababilinski.github.io/librivox-mobile/";
  return {
    ...knownBooks[bookId], source,
    intent: `intent://open?${query}#Intent;scheme=audiobook;package=${packageName};S.browser_fallback_url=${encodeURIComponent(fallback)};end`,
  };
}

if (typeof document !== "undefined") {
  const link = bookLink(location.search);
  if (link) {
    const title = link.title || "Open this book";
    document.title = `${title} · LibriVox Mobile`;
    document.getElementById("title").textContent = title;
    if (link.author) {
      const author = document.getElementById("author");
      author.textContent = link.author;
      author.hidden = false;
    }
    const open = document.getElementById("open-app");
    open.href = link.intent;
    open.textContent = "Open in LibriVox for Android";
    if (link.source) {
      const source = document.getElementById("source");
      source.href = link.source;
      source.hidden = false;
    }
  } else {
    document.getElementById("message").textContent = "This link is missing a valid book. Open LibriVox and search for the title, or ask for a new sharing link.";
  }
}
