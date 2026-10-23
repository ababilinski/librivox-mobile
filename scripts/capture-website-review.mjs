#!/usr/bin/env node
/**
 * Capture the frozen website and its localized, adaptive-theme successor.
 * Uses Playwright from NODE_PATH; never modifies the pages being reviewed.
 *
 * NODE_PATH=/path/to/node_modules node scripts/capture-website-review.mjs
 * BASELINE_URL=http://127.0.0.1:4175 UPDATED_URL=http://127.0.0.1:4174
 */
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const reviewLanguage = process.env.REVIEW_LANGUAGE ?? 'en';
if (!['en', 'pl'].includes(reviewLanguage)) throw new Error('REVIEW_LANGUAGE must be en or pl.');
const output = path.resolve(root, process.env.REVIEW_OUTPUT ?? `artifacts/theme-review/screenshots${reviewLanguage === 'pl' ? '-pl' : ''}`);
const baseline = process.env.BASELINE_URL ?? 'http://127.0.0.1:4175';
const updated = process.env.UPDATED_URL ?? 'http://127.0.0.1:4174';
const behaviorOnly = process.env.REVIEW_BEHAVIOR_ONLY === '1';
const themeKey = 'librivox-site-theme';
const routes = [
  { name: 'home', title: 'Home', route: '/', detail: '.hero-artwork', app: '.hero-phone' },
  { name: 'features', title: 'Features', route: '/features/', detail: '#discovery', app: '#discovery .phone' },
  { name: 'sources', title: 'Sources', route: '/sources/', detail: '.source-primary', app: '.source-primary' }
];
const viewports = [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'phone', width: 390, height: 844 }
];
const variants = ['current', 'light', 'dark'];
const checks = [];
const captures = [];
const errors = [];
const browser = await chromium.launch({ headless: true, channel: process.env.REVIEW_BROWSER_CHANNEL ?? 'chrome' });

async function check(name, action) {
  try { await action(); checks.push({ name, result: 'pass' }); }
  catch (error) { checks.push({ name, result: 'fail', error: String(error.message ?? error) }); }
}
function assert(value, message) { if (!value) throw new Error(message); }
async function settle(page) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    for (const image of document.images) image.loading = 'eager';
    await Promise.all([...document.images].map(image => image.decode().catch(() => {})));
  });
  // Wait for reveal animations by visiting the page, rather than altering page CSS.
  await page.evaluate(async () => {
    for (let y = 0; y < document.documentElement.scrollHeight; y += innerHeight * .8) {
      scrollTo(0, y);
      await new Promise(requestAnimationFrame);
    }
    scrollTo(0, 0);
  });
  await page.waitForTimeout(100);
}
async function saveCapture(page, file, metadata, options = {}) {
  const filename = path.join(output, file);
  await page.screenshot({ path: filename, animations: 'disabled', ...options });
  const sha256 = createHash('sha256').update(await readFile(filename)).digest('hex');
  captures.push({ ...metadata, file, sha256 });
}
async function elementCapture(page, selector, file, metadata, maxHeight) {
  const element = page.locator(selector).first();
  await element.scrollIntoViewIfNeeded();
  await page.waitForTimeout(50);
  const box = await element.boundingBox();
  assert(box, `${selector} has no visible box`);
  const clip = {
    x: Math.max(0, box.x), y: Math.max(0, box.y),
    width: Math.min(box.width, page.viewportSize().width - Math.max(0, box.x)),
    height: Math.min(box.height, maxHeight ?? page.viewportSize().height)
  };
  // Page screenshots support clip rectangles below the current viewport.
  await saveCapture(page, file, metadata, { clip, captureBeyondViewport: true });
}

try {
  await mkdir(output, { recursive: true });
  if (!behaviorOnly) {
  for (const viewport of viewports) {
    for (const route of routes) {
      for (const variant of variants) {
        const context = await browser.newContext({
          viewport: { width: viewport.width, height: viewport.height },
          deviceScaleFactor: 2, locale: reviewLanguage === 'pl' ? 'pl-PL' : 'en-US', colorScheme: variant === 'dark' ? 'dark' : 'light', reducedMotion: 'reduce'
        });
        await context.addInitScript(({ themeKey, variant, reviewLanguage }) => {
          localStorage.setItem('librivox-site-language', reviewLanguage);
          if (variant !== 'current') localStorage.setItem(themeKey, variant);
        }, { themeKey, variant, reviewLanguage });
        const page = await context.newPage();
        page.on('pageerror', error => errors.push({ variant, route: route.route, viewport: viewport.name, message: error.message }));
        const url = `${variant === 'current' ? baseline : updated}${reviewLanguage === 'pl' ? '/pl' : ''}${route.route}?lang=${reviewLanguage}`;
        await page.goto(url, { waitUntil: 'networkidle' });
        await settle(page);
        const prefix = `${route.name}-${viewport.name}-${variant}`;
        const metadata = { page: route.name, title: route.title, route: route.route, viewport: viewport.name, width: viewport.width, height: viewport.height, variant, url, language: reviewLanguage };
        const state = await page.evaluate(() => ({
          language: document.documentElement.lang,
          theme: document.documentElement.dataset.theme,
          overflow: document.documentElement.scrollWidth > innerWidth + 1,
          brokenImages: [...document.images].filter(image => !image.complete || !image.naturalWidth).map(image => image.currentSrc),
          appCaptures: [...document.querySelectorAll('picture[data-app-capture] img')].map(image => image.currentSrc)
        }));
        await check(`${prefix}: ${reviewLanguage}, intact images, no horizontal overflow`, () => {
          assert(state.language === reviewLanguage, `Unexpected language ${state.language}`);
          assert(!state.overflow, 'Horizontal overflow');
          assert(!state.brokenImages.length, `Broken images: ${state.brokenImages.join(', ')}`);
          if (variant !== 'current') {
            assert(state.theme === variant, `Expected ${variant}, got ${state.theme}`);
            for (const image of state.appCaptures) assert(image.includes(`/product/${reviewLanguage}/${variant}/`), `App image does not match language and theme: ${image}`);
          }
        });
        await page.evaluate(() => scrollTo(0, 0));
        await saveCapture(page, `${prefix}-viewport.png`, { ...metadata, kind: 'viewport', scrollY: 0 });
        await saveCapture(page, `${prefix}-full.png`, { ...metadata, kind: 'full' }, { fullPage: true });
        await elementCapture(page, route.detail, `${prefix}-detail.png`, { ...metadata, kind: 'detail', selector: route.detail }, viewport.name === 'phone' ? 844 : 720);
        await elementCapture(page, route.app, `${prefix}-app.png`, { ...metadata, kind: 'app', selector: route.app }, viewport.name === 'phone' ? 700 : 800);
        await elementCapture(page, '.site-footer', `${prefix}-footer.png`, { ...metadata, kind: 'footer', selector: '.site-footer' }, viewport.name === 'phone' ? 844 : 520);
        const switcher = page.locator('.appearance-switch');
        if (await switcher.count()) {
          await elementCapture(page, '.appearance-switch', `${prefix}-control.png`, { ...metadata, kind: 'control', selector: '.appearance-switch' });
        }
        await context.close();
      }
    }
  }
  }

  // Check behavior independently from the pinned-theme comparison captures.
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'en-US', colorScheme: 'dark', reducedMotion: 'reduce' });
  const page = await context.newPage();
  await page.goto(`${updated}/?lang=en`);
  await check('System is the default and follows browser appearance changes', async () => {
    assert(await page.getAttribute('html', 'data-theme-preference') === 'system', 'Default is not System');
    assert(await page.getAttribute('html', 'data-theme') === 'dark', 'Dark browser preference not applied');
    await page.emulateMedia({ colorScheme: 'light' });
    await page.waitForFunction(() => document.documentElement.dataset.theme === 'light');
  });
  await check('Manual Dark persists through browser changes, reloads and Polish navigation', async () => {
    await page.locator('.appearance-switch input[value="dark"]').check();
    await page.emulateMedia({ colorScheme: 'light' });
    assert(await page.getAttribute('html', 'data-theme') === 'dark', 'Manual Dark lost');
    await page.reload();
    assert(await page.getAttribute('html', 'data-theme') === 'dark', 'Reload lost Dark');
    await page.goto(`${updated}/pl/features/`);
    assert(await page.getAttribute('html', 'lang') === 'pl', 'Polish route lost language');
    assert(await page.getAttribute('html', 'data-theme') === 'dark', 'Language navigation lost Dark');
    assert(await page.locator('.appearance-switch legend').innerText() === 'Wygląd', 'Missing localized appearance control');
  });
  await check('The header exposes only sun and moon, with language selection in the footer', async () => {
    const state = await page.locator('.appearance-switch').evaluate(element => {
      const box = element.getBoundingClientRect();
      const header = element.closest('header');
      const headerBox = header?.getBoundingClientRect();
      const menu = document.querySelector('.menu-toggle').getBoundingClientRect();
      return { values: [...element.querySelectorAll('input')].map(input => input.value), insideHeader: !!header, insideBounds: !!headerBox && box.top >= headerBox.top && box.bottom <= headerBox.bottom && box.right <= innerWidth, clearOfMenu: box.right <= menu.left, headerLanguage: !!header?.querySelector('.language-switch'), footerLanguage: document.querySelectorAll('footer .language-switch a[data-locale]').length };
    });
    assert(state.values.join(',') === 'light,dark', 'Unexpected visible appearance option');
    assert(state.insideHeader && state.insideBounds && state.clearOfMenu && !state.headerLanguage && state.footerLanguage === 2, `Unexpected header or footer controls: ${JSON.stringify(state)}`);
  });
  await check('Clearing a saved choice restores automatic browser appearance', async () => {
    await page.evaluate(key => localStorage.removeItem(key), themeKey);
    await page.reload();
    await page.emulateMedia({ colorScheme: 'light' });
    await page.waitForFunction(() => document.documentElement.dataset.theme === 'light');
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.waitForFunction(() => document.documentElement.dataset.theme === 'dark');
  });
  await check('Clicking the active icon locks that mode instead of following later browser changes', async () => {
    await page.locator('.appearance-switch input[value="dark"]').click();
    assert(await page.getAttribute('html', 'data-theme-preference') === 'dark', 'Active moon click did not save Dark');
    await page.emulateMedia({ colorScheme: 'light' });
    assert(await page.getAttribute('html', 'data-theme') === 'dark', 'Explicit Dark followed the browser');
  });
  await check('Theme radios support keyboard selection', async () => {
    await page.locator('.appearance-switch input[value="light"]').check();
    await page.locator('.appearance-switch input[value="light"]').focus();
    await page.keyboard.press('ArrowRight');
    assert(await page.locator('.appearance-switch input[value="dark"]').isChecked(), 'ArrowRight did not select Dark');
  });
  await check('Reduced motion disables theme and menu transitions', async () => {
    const durations = await page.evaluate(() => {
      const control = document.querySelector('.appearance-switch');
      return [
        getComputedStyle(control, '::before').transitionDuration,
        getComputedStyle(document.querySelector('.nav-panel')).transitionDuration,
        getComputedStyle(document.querySelector('.menu-icon span')).transitionDuration,
        getComputedStyle(document.body).transitionDuration
      ];
    });
    assert(durations.every(value => value.split(',').every(duration => parseFloat(duration) === 0)), `Unexpected reduced-motion durations: ${durations.join('; ')}`);
  });
  await check('Mobile menu supports keyboard opening, navigation, Escape and focus restoration', async () => {
    const toggle = page.locator('.menu-toggle');
    await toggle.focus();
    await page.keyboard.press('Enter');
    assert(await toggle.getAttribute('aria-expanded') === 'true', 'Enter did not open menu');
    assert(await page.locator('main').evaluate(element => element.inert), 'Covered page is not inert');
    await page.keyboard.press('Tab');
    assert(await page.locator('.nav-panel').evaluate(element => element.contains(document.activeElement)), 'Tab did not enter menu options');
    await page.keyboard.press('Escape');
    assert(await toggle.getAttribute('aria-expanded') === 'false', 'Escape did not close menu');
    assert(await toggle.evaluate(element => element === document.activeElement), 'Escape did not restore toggle focus');
    assert(!await page.locator('main').evaluate(element => element.inert), 'Page remained inert after closing menu');
  });
  await context.close();

  for (const locale of ['en', 'pl']) {
    const restricted = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'dark', locale: locale === 'pl' ? 'pl-PL' : 'en-US', reducedMotion: 'reduce' });
    await restricted.addInitScript(() => {
      Object.defineProperty(window, 'localStorage', {
        configurable: true,
        get() { throw new DOMException('Storage is unavailable for this test.', 'SecurityError'); }
      });
    });
    const restrictedPage = await restricted.newPage();
    await restrictedPage.goto(`${updated}/${locale === 'pl' ? 'pl/' : ''}?lang=${locale}`);
    await check(`${locale}: blocked storage retains working System and manual appearance choices`, async () => {
      assert(await restrictedPage.getAttribute('html', 'data-theme-preference') === 'system', 'Blocked storage did not default to System');
      assert(await restrictedPage.getAttribute('html', 'data-theme') === 'dark', 'Blocked storage did not follow dark browser appearance');
      await restrictedPage.emulateMedia({ colorScheme: 'light' });
      await restrictedPage.waitForFunction(() => document.documentElement.dataset.theme === 'light');
      await restrictedPage.locator('.appearance-switch input[value="dark"]').check();
      assert(await restrictedPage.getAttribute('html', 'data-theme') === 'dark', 'Manual Dark failed with blocked storage');
      await restrictedPage.reload();
      assert(await restrictedPage.getAttribute('html', 'data-theme-preference') === 'system', 'Reload persisted a choice despite blocked storage');
      assert(await restrictedPage.getAttribute('html', 'data-theme') === 'light', 'Reload did not return to the browser appearance');
    });
    await restricted.close();

    const noScript = await browser.newContext({ javaScriptEnabled: false, colorScheme: 'dark', locale: locale === 'pl' ? 'pl-PL' : 'en-US' });
    const page = await noScript.newPage();
    await page.goto(`${updated}/${locale === 'pl' ? 'pl/' : ''}`);
    await check(`${locale}: dark theme works without JavaScript`, async () => {
      const color = await page.locator('body').evaluate(element => getComputedStyle(element).backgroundColor);
      const rgb = color.match(/[\d.]+/g)?.map(Number) ?? [];
      assert(rgb.length >= 3 && Math.max(...rgb.slice(0, 3)) < 80, `Expected dark body, got ${color}`);
    });
    await check(`${locale}: no-JavaScript app captures match page language and dark appearance`, async () => {
      const images = await page.locator('picture[data-app-capture] img').evaluateAll(async elements => {
        for (const image of elements) image.loading = 'eager';
        await Promise.all(elements.map(image => image.decode().catch(() => {})));
        return elements.map(image => ({ src: image.currentSrc, complete: image.complete && image.naturalWidth > 0 }));
      });
      assert(images.length > 0, 'No adaptive app pictures found');
      for (const image of images) {
        assert(image.complete, `App capture failed to load: ${image.src}`);
        assert(image.src.includes(`/product/${locale}/dark/`), `Incorrect no-JavaScript app capture: ${image.src}`);
      }
      assert(await page.locator('.appearance-switch').evaluate(element => getComputedStyle(element).display === 'none'), 'Inert appearance control remains visible without JavaScript');
    });
    await noScript.close();
  }
} finally {
  await browser.close();
  const report = { generatedAt: new Date().toISOString(), baseline, updated, viewports, routes, captures, checks, pageErrors: errors };
  await writeFile(path.join(output, behaviorOnly ? 'behavior.json' : 'manifest.json'), `${JSON.stringify(report, null, 2)}\n`);
  const failed = checks.filter(check => check.result === 'fail');
  console.log(JSON.stringify({ output, captures: captures.length, checks: checks.length, failed, pageErrors: errors }, null, 2));
  if (failed.length || errors.length) process.exitCode = 1;
}
