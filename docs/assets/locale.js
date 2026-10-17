(() => {
  const script = document.currentScript;
  if (!script || !/^https?:$/.test(location.protocol)) return;
  const base = new URL("../", script.src);
  const current = new URL(location.href);
  if (!current.pathname.startsWith(base.pathname)) return;
  const path = current.pathname.slice(base.pathname.length);
  const locale = path === "pl" || path.startsWith("pl/") ? "pl" : "en";
  const key = "librivox-site-language";
  const supported = value => value === "en" || value === "pl";
  const remember = language => { try { localStorage.setItem(key, language); } catch {} };
  let saved;
  try { saved = localStorage.getItem(key); } catch {}
  const requested = current.searchParams.get("lang");
  if (supported(requested)) {
    saved = requested;
    remember(requested);
    current.searchParams.delete("lang");
    // Language links retain readable URLs after an explicit selection.
    history.replaceState(null, "", current.href);
    if (requested !== locale) {
      const page = locale === "pl" ? path.replace(/^pl\/?/, "") : path;
      const target = new URL(base.href + (requested === "pl" ? "pl/" : "") + page);
      target.search = current.search;
      target.hash = current.hash;
      location.replace(target.href);
      return;
    }
  }
  // Only the unprefixed landing page detects a preference. Deep links keep their language.
  if (path === "" || path === "index.html") {
    const preferences = navigator.languages?.length ? navigator.languages : [navigator.language];
    const detected = preferences.map(value => (value || "").toLowerCase().split("-")[0]).find(supported) || "en";
    if ((supported(saved) ? saved : detected) === "pl") {
      const target = new URL("pl/", base);
      target.search = current.search;
      target.hash = current.hash;
      location.replace(target.href);
      return;
    }
  }
  const updateLinks = () => {
    document.querySelectorAll(".language-switch a[data-locale]").forEach(link => {
      const target = new URL(link.href);
      target.search = current.search;
      target.searchParams.set("lang", link.dataset.locale);
      target.hash = location.hash;
      link.href = target.href;
    });
  };
  document.addEventListener("DOMContentLoaded", updateLinks);
  window.addEventListener("hashchange", updateLinks);
  document.addEventListener("click", event => {
    const link = event.target.closest?.(".language-switch a[data-locale]");
    if (link && supported(link.dataset.locale)) remember(link.dataset.locale);
  });
})();
