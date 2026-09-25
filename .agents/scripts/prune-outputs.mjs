#!/usr/bin/env node
// prune-outputs.mjs — find spent files in .agents/outputs/.
//
//   node .agents/scripts/prune-outputs.mjs            # report only
//   node .agents/scripts/prune-outputs.mjs --delete   # remove the ORPHANs
//
// A file here is disposable when nothing live cites it. NOT when it is old — the
// oldest reports (2026-09-11-*) are the evidence the whole design rests on, while a
// patch from last week is already superseded by the commit that applied it.
//
// Citations are read from docs/, .agents/work/, .agents/skills/, CONTRIBUTING.md,
// GEMINI.md and AGENTS.md. Globs count: docs/target-state/README.md cites `.agents/outputs/2026-09-11-*`
// as a set, never by filename. A literal-only match would call those four orphans and
// delete them. That is the whole reason this is a script and not a shell one-liner.
//
// Exit 0 = report produced (or deletions done). Exit 1 = could not read the citation
// sources, so no conclusion is trustworthy and nothing is deleted.

import { readFileSync, readdirSync, statSync, rmSync, existsSync } from "node:fs";
import { resolve, join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const OUTPUTS = join(ROOT, ".agents", "outputs");
const DELETE = process.argv.includes("--delete");

if (!existsSync(OUTPUTS)) {
  console.log("No .agents/outputs directory found.");
  process.exit(0);
}

// Where a citation may live. Directories are walked for .md; files are read directly.
const SOURCES = [
  "docs",
  ".agents/work",
  ".agents/skills",
  "CONTRIBUTING.md",
  "GEMINI.md",
  "AGENTS.md"
];

function walk(p, acc = []) {
  let st;
  try { st = statSync(p); } catch { return acc; }
  if (st.isFile()) { if (p.endsWith(".md")) acc.push(p); return acc; }
  for (const e of readdirSync(p)) {
    if (e.startsWith(".")) continue;
    walk(join(p, e), acc);
  }
  return acc;
}

// ---------------------------------------------------------------------------
// 1. Collect every `.agents/outputs/<pattern>` reference, with where it was found.
// ---------------------------------------------------------------------------
const citations = []; // { pattern, where }
let scanned = 0;

for (const s of SOURCES) {
  for (const file of walk(join(ROOT, s))) {
    let text;
    try { text = readFileSync(file, "utf8"); } catch { continue; }
    scanned++;
    text.split(/\r?\n/).forEach((line, i) => {
      for (const m of line.matchAll(/\.agents\/outputs\/([A-Za-z0-9._*-]+)/g)) {
        citations.push({
          pattern: m[1],
          where: `${relative(ROOT, file).split(sep).join("/")}:${i + 1}`,
        });
      }
      for (const m of line.matchAll(/\.claude\/outputs\/([A-Za-z0-9._*-]+)/g)) {
        citations.push({
          pattern: m[1],
          where: `${relative(ROOT, file).split(sep).join("/")}:${i + 1}`,
        });
      }
    });
  }
}

if (scanned === 0) {
  console.error("prune-outputs: read no citation sources — refusing to judge anything.");
  process.exit(1);
}

function toRegex(pattern) {
  const escaped = pattern.replace(/[.+^${}()|[\\\]\\\\]/g, "\\\\$&").replace(/\*/g, ".*");
  return new RegExp(`^${escaped}$`);
}
const matchers = citations.map((c) => ({ ...c, re: toRegex(c.pattern) }));

// ---------------------------------------------------------------------------
// 2. Classify each file in .agents/outputs/
// ---------------------------------------------------------------------------
const files = readdirSync(OUTPUTS)
  .filter((n) => !n.startsWith("."))
  .filter((n) => n.endsWith(".md") || n.endsWith(".patch"))
  .filter((n) => statSync(join(OUTPUTS, n)).isFile());

const sid = (process.env.SESSION_ID ?? "").slice(0, 8);
const sessionLogs = files.filter((n) => /-session-[0-9a-f-]+\.md$/.test(n));
let current = sid && sessionLogs.find((n) => n.includes(sid));
if (!current && sessionLogs.length) {
  current = sessionLogs
    .map((n) => ({ n, m: statSync(join(OUTPUTS, n)).mtimeMs }))
    .sort((a, b) => b.m - a.m)[0].n;
}

const today = new Date().toISOString().slice(0, 10);

const rows = [];
for (const name of files) {
  const isSession = /-session-[0-9a-f-]+\.md$/.test(name);
  const hit = matchers.find((m) => m.pattern === name)
    ?? (isSession ? undefined : matchers.find((m) => m.re.test(name)));
  if (hit) {
    const via = hit.pattern === name ? "" : ` (${hit.pattern})`;
    rows.push({ name, verdict: "KEEP", detail: `cited: ${hit.where}${via}` });
  } else if (isSession && (name === current || name.startsWith(today))) {
    const why = name === current ? "current session, still being written" : "logged today — may still be open elsewhere";
    rows.push({ name, verdict: "SKIP", detail: why });
  } else if (/W-\d{2}/.test(name)) {
    const t = name.match(/W-\d{2}/)[0];
    rows.push({ name, verdict: "REVIEW", detail: `${t} output — keep until ${t} is closed and synced` });
  } else {
    rows.push({ name, verdict: "ORPHAN", detail: "no citation found" });
  }
}

// ---------------------------------------------------------------------------
// 3. Report
// ---------------------------------------------------------------------------
const order = { ORPHAN: 0, REVIEW: 1, SKIP: 2, KEEP: 3 };
rows.sort((a, b) => order[a.verdict] - order[b.verdict] || a.name.localeCompare(b.name));

const w = Math.max(...rows.map((r) => r.name.length), 10);
console.log();
for (const r of rows) console.log(`${r.verdict.padEnd(7)} ${r.name.padEnd(w)}  ${r.detail}`);
console.log();

const orphans = rows.filter((r) => r.verdict === "ORPHAN");
const review = rows.filter((r) => r.verdict === "REVIEW");

if (!orphans.length) {
  console.log("No orphans. Every report here is cited, current, or ticket-scoped.");
} else if (DELETE) {
  for (const r of orphans) rmSync(join(OUTPUTS, r.name));
  console.log(`Deleted ${orphans.length} orphan${orphans.length === 1 ? "" : "s"}.`);
} else {
  console.log(`Outputs check: ${rows.length} report(s), ${orphans.length} orphan(s).`);
}
if (review.length) {
  console.log(`${review.length} REVIEW — keep until tickets are closed and synced.`);
}
