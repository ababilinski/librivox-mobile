#!/usr/bin/env node
/** Rendered appearance audit. Requires Playwright from NODE_PATH and a local preview. */
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const base = process.env.UPDATED_URL ?? 'http://127.0.0.1:4174';
const out = path.resolve(root, process.env.REVIEW_REPORT ?? 'artifacts/theme-review/contrast-audit.json');
const widths = process.env.REVIEW_WIDTHS?.split(',').map(Number) ?? [1440, 390];
if (widths.some(width => !Number.isInteger(width) || width < 280)) throw new Error('REVIEW_WIDTHS must contain viewport widths of at least 280px.');
const routes = ['/', '/features/', '/sources/', '/faq/', '/support/', '/resources/', '/publishing/', '/privacy-policy/', '/terms-of-service/', '/404.html'];
const findings = [];
let pages = 0;
const browser = await chromium.launch({ headless: true, channel: process.env.REVIEW_BROWSER_CHANNEL ?? 'chrome' });

/** This audit covers solid-color text, including alpha-blended ancestor backgrounds.
 * Artwork is reviewed visually; this does not claim to certify accessibility. */
function inspectText() {
  const rgba = value => {
    const numbers = value.match(/[\d.]+/g)?.map(Number) ?? [0, 0, 0, 0];
    return numbers.length === 3 ? [...numbers, 1] : numbers;
  };
  const blend = (front, back) => front.slice(0, 3).map((value, i) => value * front[3] + back[i] * (1 - front[3])).concat(1);
  function background(element) {
    if (!element) return [255, 255, 255, 1];
    const color = rgba(getComputedStyle(element).backgroundColor);
    return color[3] === 1 ? color : blend(color, background(element.parentElement));
  }
  function luminance(color) {
    return color.slice(0, 3).map(value => value / 255)
      .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4)
      .reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0);
  }
  function contrast(front, back) {
    const a = luminance(front), b = luminance(back);
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  }
  const lowContrast = [];
  const iterator = document.createNodeIterator(document.body, NodeFilter.SHOW_TEXT);
  for (let node = iterator.nextNode(); node; node = iterator.nextNode()) {
    const text = node.textContent.trim();
    const element = node.parentElement;
    if (!text || !element) continue;
    const style = getComputedStyle(element);
    const box = element.getBoundingClientRect();
    if (['SCRIPT', 'STYLE', 'LEGEND'].includes(element.tagName) || style.visibility !== 'visible' || style.display === 'none' || style.opacity === '0' || !box.width || !box.height) continue;
    const back = background(element);
    const ratio = contrast(blend(rgba(style.color), back), back);
    const large = parseFloat(style.fontSize) >= 24 || (parseFloat(style.fontSize) >= 18.66 && parseInt(style.fontWeight) >= 700);
    if (ratio < (large ? 3 : 4.5) - .01) lowContrast.push({ text: text.slice(0, 80), element: `${element.tagName}.${element.className}`, color: style.color, background: back, ratio: +ratio.toFixed(2) });
  }
  return {
    lowContrast,
    overflow: document.documentElement.scrollWidth > innerWidth + 1,
    language: document.documentElement.lang,
    theme: document.documentElement.dataset.theme,
    control: !!document.querySelector('.appearance-switch'),
    touchTargets: [...document.querySelectorAll('.appearance-switch input')].map(input => {
      const box = input.getBoundingClientRect();
      return { choice: input.value, width: box.width, height: box.height };
    })
  };
}

try {
  for (const theme of ['light', 'dark']) for (const width of widths) {
    const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, reducedMotion: 'reduce' });
    const page = await context.newPage();
    for (const locale of ['en', 'pl']) for (const route of routes) {
      const url = `${base}${locale === 'pl' ? '/pl' : ''}${route}?lang=${locale}`;
      await page.goto(url);
      await page.evaluate(() => document.fonts.ready);
      const result = await page.evaluate(inspectText);
      const problems = [];
      if (result.lowContrast.length) problems.push('text contrast');
      if (result.overflow) problems.push('horizontal overflow');
      if (result.language !== locale || result.theme !== theme) problems.push('language or theme mismatch');
      if (!result.control) problems.push('missing appearance control');
      if (result.touchTargets.some(target => target.width < 24 || target.height < 44)) problems.push('appearance target below 24px wide or 44px tall');
      if (problems.length) findings.push({ url, theme, width, problems, ...result });
      pages += 1;
    }
    await context.close();
  }
} finally {
  await browser.close();
  await mkdir(path.dirname(out), { recursive: true });
  await writeFile(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), pages, findings }, null, 2)}\n`);
  console.log(JSON.stringify({ pages, findings, report: out }, null, 2));
  if (findings.length) process.exitCode = 1;
}
