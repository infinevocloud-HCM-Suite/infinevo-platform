#!/usr/bin/env node
// prune-outputs.mjs — find spent files in .agents/outputs/.
//
//   node .agents/scripts/prune-outputs.mjs            # report only
//   node .agents/scripts/prune-outputs.mjs --delete   # remove the ORPHANs

import { readFileSync, readdirSync, statSync, rmSync } from "node:fs";
import { resolve, join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const OUTPUTS = join(ROOT, ".agents", "outputs");
const DELETE = process.argv.includes("--delete");

const SOURCES = [
  "docs",
  ".agents/work",
  ".agents/skills",
  "CONTRIBUTING.md",
  "GEMINI.md",
  "AGENTS.md",
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

const citations = [];
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

let files = [];
try {
  files = readdirSync(OUTPUTS)
    .filter((n) => !n.startsWith("."))
    .filter((n) => n.endsWith(".md") || n.endsWith(".patch"))
    .filter((n) => statSync(join(OUTPUTS, n)).isFile());
} catch {
  files = [];
}

const sessionLogs = files.filter((n) => /-session-[0-9a-f]+\.md$/.test(n));
let current = undefined;
if (sessionLogs.length) {
  current = sessionLogs
    .map((n) => ({ n, m: statSync(join(OUTPUTS, n)).mtimeMs }))
    .sort((a, b) => b.m - a.m)[0].n;
}

const today = new Date().toISOString().slice(0, 10);

const rows = [];
for (const name of files) {
  const isSession = /-session-[0-9a-f]+.md$/.test(name);
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

const order = { ORPHAN: 0, REVIEW: 1, SKIP: 2, KEEP: 3 };
rows.sort((a, b) => order[a.verdict] - order[b.verdict] || a.name.localeCompare(b.name));

if (rows.length) {
  const w = Math.max(...rows.map((r) => r.name.length));
  console.log();
  for (const r of rows) console.log(`${r.verdict.padEnd(7)} ${r.name.padEnd(w)}  ${r.detail}`);
  console.log();
}

const orphans = rows.filter((r) => r.verdict === "ORPHAN");
const review = rows.filter((r) => r.verdict === "REVIEW");

if (!orphans.length) {
  console.log("No orphans. Every report here is cited, current, or ticket-scoped.");
} else if (DELETE) {
  for (const r of orphans) rmSync(join(OUTPUTS, r.name));
  console.log(`Deleted ${orphans.length} orphan${orphans.length === 1 ? "" : "s"}.`);
} else {
  console.log(`${orphans.length} orphan${orphans.length === 1 ? "" : "s"}. Run with --delete to remove them.`);
}
if (review.length) {
  console.log(`${review.length} REVIEW — never deleted by --delete; close the ticket and /sync-docs first.`);
}
