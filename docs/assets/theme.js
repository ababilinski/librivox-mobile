/* Runs before stylesheets so the first frame uses the visitor's appearance. */
(() => {
  const root = document.documentElement;
  const key = 'librivox-site-theme';
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const choices = ['light', 'system', 'dark'];
  let preference = 'system';
  let transitionTimer;
  try {
    const saved = localStorage.getItem(key);
    if (choices.includes(saved)) preference = saved;
  } catch { /* Appearance still works when storage is unavailable. */ }

  function apply() {
    const effective = preference === 'system' ? (system.matches ? 'dark' : 'light') : preference;
    root.dataset.theme = effective;
    root.dataset.themePreference = preference;
    document.querySelectorAll('.appearance-switch').forEach(control => {
      control.style.setProperty('--selected', effective === 'dark' ? 1 : 0);
      control.querySelectorAll('input').forEach(input => { input.checked = input.value === effective; });
    });
    document.querySelectorAll('picture[data-app-capture]').forEach(picture => {
      picture.querySelectorAll('source[data-theme]').forEach(source => {
        source.media = source.dataset.theme === effective ? 'all' : 'not all';
      });
    });
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = effective === 'dark' ? '#151517' : '#fbf8f1';
  }
  function choose(value) {
    if (!['light', 'dark'].includes(value) || preference === value) return;
    preference = value;
    try { localStorage.setItem(key, preference); } catch {}
    root.classList.add('theme-changing');
    apply();
    window.clearTimeout(transitionTimer);
    transitionTimer = window.setTimeout(() => root.classList.remove('theme-changing'), 260);
  }
  apply();
  // The parser can encounter pictures before DOMContentLoaded. Resolve their media
  // immediately as they arrive, including a saved override of the browser's theme.
  const pictures = new MutationObserver(records => {
    if (records.some(record => record.addedNodes.length)) {
      const effective = root.dataset.theme;
      document.querySelectorAll('picture[data-app-capture] source[data-theme]').forEach(source => {
        const media = source.dataset.theme === effective ? 'all' : 'not all';
        if (source.media !== media) source.media = media;
      });
    }
  });
  pictures.observe(root, { childList: true, subtree: true });
  system.addEventListener('change', () => { if (preference === 'system') apply(); });
  window.addEventListener('storage', event => {
    if (event.key !== key) return;
    preference = choices.includes(event.newValue) ? event.newValue : 'system';
    apply();
  });
  document.addEventListener('DOMContentLoaded', () => {
    pictures.disconnect();
    document.querySelectorAll('.appearance-switch').forEach(control => {
      control.hidden = false;
      control.addEventListener('change', event => choose(event.target.value));
      // Clicking the already-selected icon also makes the browser-derived mode
      // an explicit choice, so it persists when the browser appearance changes.
      control.addEventListener('click', event => {
        if (event.target.matches('input')) choose(event.target.value);
      });
    });
    apply();
  });
})();
