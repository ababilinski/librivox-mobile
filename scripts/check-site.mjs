import { access, readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const siteRoot = path.resolve(repoRoot, process.env.SITE_ROOT ?? "docs");
const errors = [];

const localSchemes = new Set(["", "file:"]);
const skippedSchemes = new Set(["http:", "https:", "mailto:", "tel:"]);

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function walk(dir, matcher, found = []) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      await walk(fullPath, matcher, found);
    } else if (matcher(fullPath)) {
      found.push(fullPath);
    }
  }
  return found;
}

function parseAttributes(tag) {
  const attrs = {};
  const pattern = /([^\s"'<>/=]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))/g;
  for (const match of tag.matchAll(pattern)) {
    attrs[match[1].toLowerCase()] = match[2] ?? match[3] ?? match[4] ?? "";
  }
  return attrs;
}

function stripTags(value) {
  return value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}

async function resolveLocalReference(fromFile, rawRef, label) {
  if (!rawRef || rawRef.startsWith("#")) return;

  let url;
  try {
    url = new URL(rawRef, "file:///");
  } catch {
    errors.push(`${fromFile}: invalid ${label} reference "${rawRef}"`);
    return;
  }

  if (skippedSchemes.has(url.protocol)) return;
  if (!localSchemes.has(url.protocol)) {
    errors.push(`${fromFile}: unsupported ${label} reference "${rawRef}"`);
    return;
  }

  const cleanRef = rawRef.split("#")[0].split("?")[0];
  if (!cleanRef) return;

  const decodedRef = decodeURIComponent(cleanRef);
  const target = path.resolve(path.dirname(fromFile), decodedRef);
  const relativeToRoot = path.relative(siteRoot, target);

  if (relativeToRoot.startsWith("..") || path.isAbsolute(relativeToRoot)) {
    errors.push(`${fromFile}: ${label} reference leaves docs directory: "${rawRef}"`);
    return;
  }

  let targetStat;
  try {
    targetStat = await stat(target);
  } catch {
    errors.push(`${fromFile}: missing ${label} reference "${rawRef}"`);
    return;
  }

  if (targetStat.isDirectory() && !(await exists(path.join(target, "index.html")))) {
    errors.push(`${fromFile}: directory ${label} reference has no index.html: "${rawRef}"`);
  }
}

async function checkHtml(filePath) {
  const html = await readFile(filePath, "utf8");
  const h1Count = (html.match(/<h1\b/gi) ?? []).length;
  if (h1Count !== 1) {
    errors.push(`${filePath}: expected 1 h1, found ${h1Count}`);
  }

  for (const match of html.matchAll(/<img\b[^>]*>/gi)) {
    const attrs = parseAttributes(match[0]);
    if (!attrs.alt?.trim()) {
      errors.push(`${filePath}: image missing alt text`);
    }
    await resolveLocalReference(filePath, attrs.src, "image");
  }

  for (const match of html.matchAll(/<link\b[^>]*>/gi)) {
    const attrs = parseAttributes(match[0]);
    await resolveLocalReference(filePath, attrs.href, "link");
  }

  for (const match of html.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/gi)) {
    const attrs = parseAttributes(match[0]);
    const text = stripTags(match[2]);
    const accessibleName = text || attrs["aria-label"] || "";
    if (!attrs.href?.trim()) {
      errors.push(`${filePath}: anchor missing href`);
    }
    if (!accessibleName.trim()) {
      errors.push(`${filePath}: anchor missing accessible text`);
    }
    await resolveLocalReference(filePath, attrs.href, "anchor");
  }
}

async function checkCss(filePath) {
  const css = await readFile(filePath, "utf8");
  for (const match of css.matchAll(/url\((['"]?)(.*?)\1\)/gi)) {
    const ref = match[2].trim();
    if (!ref || ref.startsWith("data:")) continue;
    await resolveLocalReference(filePath, ref, "CSS asset");
  }
}

const htmlFiles = await walk(siteRoot, (filePath) => filePath.endsWith(".html"));
const cssFiles = await walk(siteRoot, (filePath) => filePath.endsWith(".css"));

for (const filePath of htmlFiles) {
  await checkHtml(filePath);
}

for (const filePath of cssFiles) {
  await checkCss(filePath);
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exit(1);
}

console.log(`Static site check passed: ${htmlFiles.length} HTML pages, ${cssFiles.length} CSS files.`);
