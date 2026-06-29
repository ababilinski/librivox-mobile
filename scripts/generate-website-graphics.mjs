import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceDir = path.join(repoRoot, "docs/image-resources/ui-captures");
const outputDir = path.join(repoRoot, "docs/assets/websiteGraphics");

const singleAssets = [
  {
    file: "chapters-captioned.svg",
    source: "13-player-chapters-panel.png",
    title: ["Jump between", "chapters"],
    subtitle: "Move through long audiobooks without losing the player.",
    alt: "Captioned LibriVox Mobile graphic showing the player chapters panel.",
  },
  {
    file: "bookmark-notes-captioned.svg",
    source: "14-player-bookmark-notes.png",
    title: ["Bookmarks", "with notes"],
    subtitle: "Save the moments you want to return to later.",
    alt: "Captioned LibriVox Mobile graphic showing bookmark notes in the player.",
  },
  {
    file: "sleep-timer-captioned.svg",
    source: "16-sleep-timer.png",
    title: ["Sleep timer", "for listening"],
    subtitle: "Set playback to stop on its own.",
    alt: "Captioned LibriVox Mobile graphic showing the sleep timer sheet.",
  },
];

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function hrefFor(source) {
  const sourcePath = path.join(sourceDir, source);
  return path.relative(outputDir, sourcePath).replaceAll(path.sep, "/");
}

function textLines(lines, { x, y, lineHeight, size, weight = 760, fill, anchor = "middle" }) {
  return lines
    .map((line, index) => {
      const yy = y + index * lineHeight;
      return `<text x="${x}" y="${yy}" text-anchor="${anchor}" font-family="Inter, Arial, Helvetica, sans-serif" font-size="${size}" font-weight="${weight}" fill="${fill}">${escapeHtml(line)}</text>`;
    })
    .join("\n");
}

function singleSvg(asset) {
  const imageHref = hrefFor(asset.source);
  const title = textLines(asset.title, {
    x: 540,
    y: 118,
    lineHeight: 74,
    size: 66,
    fill: "#004F59",
  });
  const subtitle = `<text x="540" y="286" text-anchor="middle" font-family="Inter, Arial, Helvetica, sans-serif" font-size="28" font-weight="520" fill="#315F65">${escapeHtml(asset.subtitle)}</text>`;

  return `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920" role="img" aria-label="${escapeHtml(asset.alt)}">
  <defs>
    <linearGradient id="background" x1="0" y1="0" x2="0" y2="1920" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#E9FFFD"/>
      <stop offset="1" stop-color="#FFF4E4"/>
    </linearGradient>
    <linearGradient id="phoneFrame" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#006D78"/>
      <stop offset="1" stop-color="#004F59"/>
    </linearGradient>
    <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="22" stdDeviation="22" flood-color="#004F59" flood-opacity="0.22"/>
    </filter>
    <clipPath id="screenClip">
      <rect x="213" y="364" width="654" height="1468" rx="44" ry="44"/>
    </clipPath>
  </defs>
  <rect width="1080" height="1920" fill="url(#background)"/>
  <path d="M0 0h1080v346H0z" fill="#D7F5F2"/>
  <path d="M0 316c230 76 438 82 630 20s342-70 450-24v180H0z" fill="#F8E8CE" opacity="0.58"/>
  ${title}
  ${subtitle}
  <rect x="194" y="345" width="692" height="1506" rx="64" fill="url(#phoneFrame)" filter="url(#softShadow)"/>
  <rect x="208" y="359" width="664" height="1478" rx="50" fill="#111111"/>
  <image x="213" y="364" width="654" height="1468" href="${imageHref}" preserveAspectRatio="xMidYMid meet" clip-path="url(#screenClip)"/>
  <rect x="213" y="364" width="654" height="1468" rx="44" fill="none" stroke="#E9FFFD" stroke-opacity="0.22" stroke-width="2"/>
</svg>
`;
}

function bannerSvg() {
  const phoneHeight = 638;
  const phoneWidth = 284;
  const phones = [
    { source: "13-player-chapters-panel.png", x: 1036, label: "Chapters" },
    { source: "14-player-bookmark-notes.png", x: 1322, label: "Notes" },
    { source: "16-sleep-timer.png", x: 1608, label: "Timer" },
  ];
  const phoneNodes = phones
    .map((phone, index) => {
      const href = hrefFor(phone.source);
      const y = 58 + (index % 2) * 22;
      const clipId = `phoneClip${index}`;
      return `
    <clipPath id="${clipId}">
      <rect x="${phone.x}" y="${y}" width="${phoneWidth}" height="${phoneHeight}" rx="32" ry="32"/>
    </clipPath>
    <g filter="url(#phoneShadow)">
      <rect x="${phone.x - 6}" y="${y - 6}" width="${phoneWidth + 12}" height="${phoneHeight + 12}" rx="40" fill="#004F59"/>
      <rect x="${phone.x}" y="${y}" width="${phoneWidth}" height="${phoneHeight}" rx="32" fill="#101010"/>
      <image x="${phone.x}" y="${y}" width="${phoneWidth}" height="${phoneHeight}" href="${href}" preserveAspectRatio="xMidYMid meet" clip-path="url(#${clipId})"/>
    </g>
    <text x="${phone.x + phoneWidth / 2}" y="${y + phoneHeight + 42}" text-anchor="middle" font-family="Inter, Arial, Helvetica, sans-serif" font-size="25" font-weight="760" fill="#004F59">${escapeHtml(phone.label)}</text>`;
    })
    .join("\n");

  return `<svg xmlns="http://www.w3.org/2000/svg" width="2000" height="760" viewBox="0 0 2000 760" role="img" aria-label="LibriVox Mobile banner showing chapters, bookmark notes, and sleep timer screens.">
  <defs>
    <linearGradient id="background" x1="0" y1="0" x2="2000" y2="760" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#E9FFFD"/>
      <stop offset="1" stop-color="#FFF4E4"/>
    </linearGradient>
    <filter id="phoneShadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="18" stdDeviation="18" flood-color="#004F59" flood-opacity="0.2"/>
    </filter>
  </defs>
  <rect width="2000" height="760" fill="url(#background)"/>
  <path d="M0 0h960v760H0z" fill="#D7F5F2"/>
  <path d="M0 580c270-86 470-88 680-20s368 60 560-58v258H0z" fill="#F8E8CE" opacity="0.72"/>
  <text x="86" y="164" font-family="Inter, Arial, Helvetica, sans-serif" font-size="34" font-weight="800" letter-spacing="3" fill="#007986">LISTENING TOOLS</text>
  <text x="86" y="272" font-family="Inter, Arial, Helvetica, sans-serif" font-size="76" font-weight="820" fill="#004F59">Chapters, notes,</text>
  <text x="86" y="352" font-family="Inter, Arial, Helvetica, sans-serif" font-size="76" font-weight="820" fill="#004F59">and a sleep timer</text>
  <text x="90" y="424" font-family="Inter, Arial, Helvetica, sans-serif" font-size="30" font-weight="520" fill="#315F65">Move around long books, save moments,</text>
  <text x="90" y="466" font-family="Inter, Arial, Helvetica, sans-serif" font-size="30" font-weight="520" fill="#315F65">and stop playback when you are done.</text>
  <rect x="90" y="518" width="342" height="58" rx="29" fill="#007986"/>
  <text x="261" y="556" text-anchor="middle" font-family="Inter, Arial, Helvetica, sans-serif" font-size="25" font-weight="760" fill="#FFFFFF">Listen anywhere</text>
  ${phoneNodes}
</svg>
`;
}

function triptychSvg() {
  const cards = [
    { source: "13-player-chapters-panel.png", x: 96, title: "Chapters", body: "Jump to the right part." },
    { source: "14-player-bookmark-notes.png", x: 644, title: "Bookmark notes", body: "Save moments with context." },
    { source: "16-sleep-timer.png", x: 1192, title: "Sleep timer", body: "Stop playback automatically." },
  ];
  const cardNodes = cards
    .map((card, index) => {
      const href = hrefFor(card.source);
      const clipId = `triptychClip${index}`;
      return `
    <clipPath id="${clipId}">
      <rect x="${card.x + 72}" y="236" width="340" height="762" rx="34" ry="34"/>
    </clipPath>
    <g>
      <rect x="${card.x}" y="124" width="484" height="1012" rx="46" fill="#FFFDF8" stroke="#DCCFC0"/>
      <text x="${card.x + 242}" y="196" text-anchor="middle" font-family="Inter, Arial, Helvetica, sans-serif" font-size="42" font-weight="800" fill="#004F59">${escapeHtml(card.title)}</text>
      <rect x="${card.x + 58}" y="222" width="368" height="790" rx="46" fill="#004F59" opacity="0.96"/>
      <image x="${card.x + 72}" y="236" width="340" height="762" href="${href}" preserveAspectRatio="xMidYMid meet" clip-path="url(#${clipId})"/>
      <text x="${card.x + 242}" y="1070" text-anchor="middle" font-family="Inter, Arial, Helvetica, sans-serif" font-size="25" font-weight="560" fill="#315F65">${escapeHtml(card.body)}</text>
    </g>`;
    })
    .join("\n");

  return `<svg xmlns="http://www.w3.org/2000/svg" width="1780" height="1220" viewBox="0 0 1780 1220" role="img" aria-label="LibriVox Mobile collection graphic with chapters, bookmark notes, and sleep timer screens.">
  <rect width="1780" height="1220" fill="#F8F4EA"/>
  <path d="M0 0h1780v420H0z" fill="#E9FFFD"/>
  <text x="890" y="84" text-anchor="middle" font-family="Inter, Arial, Helvetica, sans-serif" font-size="40" font-weight="800" letter-spacing="3" fill="#007986">LIBRIVOX MOBILE</text>
  ${cardNodes}
</svg>
`;
}

async function main() {
  await mkdir(outputDir, { recursive: true });

  for (const asset of singleAssets) {
    await writeFile(path.join(outputDir, asset.file), singleSvg(asset), "utf8");
  }
  await writeFile(path.join(outputDir, "listening-tools-banner.svg"), bannerSvg(), "utf8");
  await writeFile(path.join(outputDir, "listening-tools-triptych.svg"), triptychSvg(), "utf8");

  await writeFile(
    path.join(outputDir, "README.md"),
    `# Website Graphics

Generated from real LibriVox Mobile UI captures in \`docs/image-resources/ui-captures/\`.

Run:

\`\`\`bash
node scripts/generate-website-graphics.mjs
\`\`\`

| File | Purpose |
| --- | --- |
| \`chapters-captioned.svg\` | Captioned chapters panel graphic. |
| \`bookmark-notes-captioned.svg\` | Captioned bookmark notes graphic. |
| \`sleep-timer-captioned.svg\` | Captioned sleep timer graphic. |
| \`listening-tools-banner.svg\` | Wide website banner combining chapters, notes, and sleep timer. |
| \`listening-tools-triptych.svg\` | Three-card collection graphic for website or publishing use. |
`,
    "utf8",
  );

  console.log(`Wrote ${path.relative(repoRoot, outputDir)}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
