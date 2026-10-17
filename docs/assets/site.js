(() => {
  document.documentElement.classList.add("has-js");
  const menu = document.querySelector(".menu-toggle");
  const panel = document.querySelector(".nav-panel");
  if (menu && panel) {
    const header = menu.closest(".site-header");
    const backdrop = header.querySelector(".menu-backdrop");
    const mobile = matchMedia("(max-width: 760px)");
    const background = [...document.querySelectorAll("main, footer, .skip-link")];
    const previousInert = new Map();
    const labels = document.documentElement.lang === "pl"
      ? { open: "Otwórz menu", close: "Zamknij menu" }
      : { open: "Open menu", close: "Close menu" };
    menu.hidden = false;
    const setOpen = (open, restoreFocus = false) => {
      open = open && mobile.matches;
      if (restoreFocus) menu.focus();
      menu.setAttribute("aria-expanded", String(open));
      menu.setAttribute("aria-label", open ? labels.close : labels.open);
      header.classList.toggle("menu-open", open);
      document.documentElement.classList.toggle("menu-active", open);
      // Inert keeps hidden links and the covered page out of keyboard and screen-reader navigation.
      panel.inert = mobile.matches && !open;
      background.forEach(element => {
        if (open) {
          if (!previousInert.has(element)) previousInert.set(element, element.inert);
          element.inert = true;
        } else if (previousInert.has(element)) {
          element.inert = previousInert.get(element);
          previousInert.delete(element);
        }
      });
    };
    menu.addEventListener("click", () => {
      setOpen(menu.getAttribute("aria-expanded") !== "true");
    });
    document.addEventListener("keydown", event => {
      if (menu.getAttribute("aria-expanded") !== "true") return;
      if (event.key === "Escape") { setOpen(false, true); return; }
      if (event.key !== "Tab") return;
      const focusable = [...header.querySelectorAll("a[href], button:not([hidden])")];
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    backdrop.addEventListener("click", () => setOpen(false, true));
    header.addEventListener("click", event => { if (event.target.closest("a")) setOpen(false); });
    mobile.addEventListener("change", () => {
      if (!mobile.matches && document.activeElement === menu) header.querySelector(".brand").focus();
      else if (mobile.matches && panel.contains(document.activeElement)) menu.focus();
      setOpen(false);
    });
    setOpen(false);
  }
  // Motion is an enhancement: content remains visible without JavaScript or with reduced motion.
  const motionPreference = matchMedia("(prefers-reduced-motion: reduce)");
  if (!motionPreference.matches) {
    document.querySelectorAll(".hero-copy > *, .page-hero > *").forEach((element, index) => {
      element.style.setProperty("--entrance-delay", `${Math.min(index, 5) * 65}ms`);
      element.classList.add("page-entrance");
    });
  }
  if ("IntersectionObserver" in window && !motionPreference.matches) {
    const reveal = element => { element.classList.remove("is-pending"); observer.unobserve(element); };
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) reveal(entry.target);
      });
    }, { threshold: 0.08 });
    const targets = document.querySelectorAll("[data-reveal], .feature-chapter > *, .cover-gallery > figure, .tool-card, .resource-banner, .link-card, .faq-preview, .legal > article > *, .section-header, .notice, .source-benefits, .collections");
    targets.forEach(element => {
      if (element.getBoundingClientRect().top > innerHeight) { element.classList.add("reveal-ready", "is-pending"); observer.observe(element); }
    });
    // Keyboard navigation must never land on content that is still faded out.
    document.addEventListener("focusin", event => {
      for (let element = event.target; element instanceof Element; element = element.parentElement) {
        if (element.classList.contains("is-pending")) reveal(element);
      }
    });
    motionPreference.addEventListener("change", event => {
      if (event.matches) { targets.forEach(reveal); observer.disconnect(); }
    });
  }
})();
