(() => {
  document.documentElement.classList.add("has-js");
  const menu = document.querySelector(".menu-toggle");
  const links = document.querySelector(".nav-links");
  if (menu && links) {
    menu.hidden = false;
    const close = () => {
      menu.setAttribute("aria-expanded", "false");
      menu.textContent = "Menu";
      links.classList.remove("is-open");
    };
    menu.addEventListener("click", () => {
      const open = menu.getAttribute("aria-expanded") !== "true";
      menu.setAttribute("aria-expanded", String(open));
      menu.textContent = open ? "Close" : "Menu";
      links.classList.toggle("is-open", open);
    });
    document.addEventListener("keydown", event => {
      if (event.key === "Escape" && menu.getAttribute("aria-expanded") === "true") { close(); menu.focus(); }
    });
    links.addEventListener("click", event => { if (event.target.closest("a")) close(); });
    matchMedia("(min-width: 761px)").addEventListener("change", close);
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
