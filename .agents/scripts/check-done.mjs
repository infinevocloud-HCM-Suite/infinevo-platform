#!/usr/bin/env node
// check-done.mjs — the definition of done, made machine-checkable for Antigravity IDE.
//
//   node .agents/scripts/check-done.mjs            (reads the branch you are on)
//
// Runs every gate, prints a table, and on success writes a receipt:
//   .agents/outputs/.merge-receipts/<branch>.json
//
// Exit 0 = every gate passed, receipt written. Exit 1 = at least one failed, no receipt.

import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync } from "node:fs";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const RECEIPTS = join(ROOT, ".agents", "outputs", ".merge-receipts");

const results = [];
function gate(name, fn) {
  let ok = false, detail = "";
  try {
    const r = fn();
    ok = r.ok;
    detail = r.detail ?? "";
  } catch (e) {
    ok = false;
    detail = String(e.message ?? e).slice(0, 160);
  }
  results.push({ name, ok, detail });
}

function sh(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, {
    cwd: opts.cwd ? join(ROOT, opts.cwd) : ROOT,
    encoding: "utf8",
    shell: process.platform === "win32",
    timeout: opts.timeout ?? 600_000,
  });
  return { code: r.status, out: `${r.stdout ?? ""}${r.stderr ?? ""}` };
}

const NL = String.fromCharCode(10);
function lastLine(out) {
  const l = String(out || "").trim().split(NL);
  return (l[l.length - 1] || "").slice(0, 140);
}

function ticketOf(branch) {
  const b = String(branch ?? "").trim();
  if (!b || b === "HEAD")
    return { item: null, unknown: true, why: "cannot resolve a branch name - are you on a detached HEAD?" };
  if (/(?:^|\/)w-(?!\d)/i.test(b))
    return { item: null, unknown: true, why: `branch "${b}" starts "W-" but names no ticket number` };
  const m = /w[-_. ]?(\d{1,4})/i.exec(b);
  return { item: m ? `W-${m[1]}` : null, unknown: false };
}

const branch = sh("git", ["rev-parse", "--abbrev-ref", "HEAD"]).out.trim();
const head = sh("git", ["rev-parse", "HEAD"]).out.trim();
const tree = sh("git", ["rev-parse", "HEAD^{tree}"]).out.trim();
const { item, unknown, why } = ticketOf(branch);

const changed = sh("git", ["diff", "--name-only", "main...HEAD"]).out
  .split(NL).map((s) => s.trim()).filter(Boolean);

// ── 1. the branch says what it is ───────────────────────────────────────────
gate("Branch names a ticket", () => {
  if (unknown) return { ok: false, detail: `${why} - rename it W-nn-<slug> or docs-<slug>` };
  if (item) return { ok: true, detail: `${branch} -> ${item}` };
  if (/^docs[-/]/i.test(branch)) return { ok: true, detail: `${branch} -> docs-only branch` };
  return { ok: true, detail: `${branch} -> not a ticket; harness or tooling work` };
});

// ── 2. the spec exists and is approved ──────────────────────────────────────
gate("Approved spec exists", () => {
  if (unknown) return { ok: false, detail: "no branch, so no spec can be found - see gate 1" };
  if (!item) return { ok: true, detail: "not a W-nn ticket; no spec expected" };
  const dir = join(ROOT, "docs", "target-state", "features");
  if (!existsSync(dir)) return { ok: false, detail: "features folder missing" };
  const spec = readdirSync(dir).find((f) => f.startsWith(item + "-"));
  if (!spec) return { ok: false, detail: `no spec for ${item}` };
  const body = readFileSync(join(dir, spec), "utf8");
  if (!/\*\*Approved/i.test(body) && !/Status\*\*.*Approved/i.test(body))
    return { ok: false, detail: `${spec} is not marked approved` };
  return { ok: true, detail: spec };
});

// ── 3. no High finding left open ────────────────────────────────────────────
gate("No open High findings", () => {
  const dir = join(ROOT, ".agents", "outputs");
  if (!existsSync(dir)) return { ok: true, detail: "no reports" };
  if (!item) return { ok: true, detail: "not a W-nn ticket; no findings to match" };
  const reports = readdirSync(dir).filter(
    (f) => f.endsWith(".md") && (f.includes(`verify-${item}`) || f.includes(`review-${item}`)),
  );
  const open = [];
  for (const f of reports) {
    for (const line of readFileSync(join(dir, f), "utf8").split(NL)) {
      if (/^\|\s*`?F-\d+/.test(line) && /\bHigh\b/i.test(line) && /\bOPEN\b/.test(line)) {
        open.push(`${f}: ${(/`?(F-\d+)/.exec(line) ?? [])[1]}`);
      }
    }
  }
  return open.length
    ? { ok: false, detail: open.join(", ") }
    : { ok: true, detail: reports.length ? `${reports.length} report(s), none High/OPEN` : "no reports" };
});

// ── 4. nothing frozen was edited ────────────────────────────────────────────
gate("legacy/ untouched", () => {
  const bad = changed.filter((f) => f.startsWith("legacy/"));
  return bad.length
    ? { ok: false, detail: bad.slice(0, 3).join(", ") }
    : { ok: true, detail: `${changed.length} files changed` };
});

// ── 5. docs/ changed only by a recognised route ─────────────────────────────
gate("docs/ changed only by a recognised route", () => {
  if (unknown) return { ok: false, detail: "cannot classify the branch - see gate 1" };
  const docs = changed.filter((f) => f === "docs" || f.startsWith("docs/"));
  const isDocsBranch = /^docs[-/]/i.test(branch);

  if (isDocsBranch) {
    const shipped = changed.filter((f) => f.startsWith("code/") || f.startsWith("infra/"));
    return shipped.length
      ? { ok: false, detail: `docs branch also ships ${shipped.slice(0, 3).join(", ")} - split it` }
      : { ok: true, detail: `${docs.length} document(s), nothing shipped` };
  }

  if (!docs.length) return { ok: true, detail: "no docs/ changes" };

  if (!item) {
    return {
      ok: false,
      detail: `${branch} is neither a ticket nor a docs branch, so it may not change docs/ (${docs[0]}) - move them to a docs-<slug> branch`,
    };
  }
  const ownSpec = new RegExp(`^docs/target-state/features/${item}-[^/]+\\.md$`, "i");
  const strays = docs.filter((f) => !ownSpec.test(f));
  return strays.length
    ? { ok: false, detail: `${item} may change only its own spec; also changed ${strays.slice(0, 3).join(", ")} - move them to a docs-<slug> branch` }
    : { ok: true, detail: `only ${item}'s own spec` };
});

// ── 6. ddl-auto is set nowhere ──────────────────────────────────────────────
gate("ddl-auto set nowhere", () => {
  const r = sh("git", ["grep", "-nE",
    "^[^#]*(ddl-auto|DDL_AUTO)([[:space:]]*[:=]|[^A-Za-z0-9]*$)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).agents/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split(NL)[0] }
    : { ok: true };
});

// ── 7. no floating point money ──────────────────────────────────────────────
gate("No float or double money field", () => {
  const r = sh("git", ["grep", "-nE",
    "(private|public|protected)[[:space:]]+(Double|Float|double|float)[[:space:]]+[a-zA-Z]*(amount|salary|pay|Pay|Amount|Salary|deduction|Deduction|tax|Tax)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).agents/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split(NL)[0] }
    : { ok: true };
});

// ── 8. CI is green for the exact commit being merged ────────────────────────
gate("CI green for this commit", () => {
  const sha = head;
  if (!sha) return { ok: false, detail: "cannot resolve HEAD" };
  const short = sha.slice(0, 7);
  const FIELDS = "status,conclusion,url,headSha,workflowName";
  const r = sh("gh", ["run", "list", "--workflow=ci.yml", "--commit", sha,
                      "--json", FIELDS, "--limit", "10"]);
  if (r.code !== 0) {
    return { ok: false, detail: `gh run list failed: ${lastLine(r.out) || `exit ${r.code}`}`.slice(0, 160) };
  }
  let runs;
  try {
    runs = JSON.parse(r.out);
  } catch {
    return { ok: false, detail: `gh run list returned unparseable output: ${lastLine(r.out)}`.slice(0, 160) };
  }
  const mine = (Array.isArray(runs) ? runs : []).filter((x) => x.headSha === sha);
  if (!mine.length) return { ok: false, detail: `no CI run for ${short} - push the branch and wait for ci.yml` };
  const pending = mine.find((x) => x.status !== "completed");
  if (pending) {
    return { ok: false, detail: `CI still ${pending.status} for ${short}: ${pending.url ?? ""}`.slice(0, 160) };
  }
  const skipped = mine.find((x) => x.conclusion === "skipped");
  if (skipped) {
    return { ok: false, detail: `CI was SKIPPED for ${short} - it verified nothing: ${skipped.url ?? ""}`.slice(0, 160) };
  }
  const bad = mine.find((x) => x.conclusion !== "success");
  if (bad) {
    return { ok: false, detail: `CI ${bad.conclusion ?? "had no conclusion"} for ${short}: ${bad.url ?? ""}`.slice(0, 160) };
  }
  return { ok: true, detail: `${mine.length} run(s) success for ${short} - backend, frontend and static all green` };
});

// ── report ──────────────────────────────────────────────────────────────────
const w = Math.max(...results.map((r) => r.name.length));
console.log(`\nDefinition of done — ${branch} @ ${head.slice(0, 8)}\n`);
for (const r of results) {
  console.log(`  ${r.ok ? "PASS" : "FAIL"}  ${r.name.padEnd(w)}  ${r.detail}`);
}

const failed = results.filter((r) => !r.ok);
console.log(`\n  ${results.length - failed.length}/${results.length} gates passed\n`);

if (failed.length) {
  console.log("  MERGE BLOCKED. Fix these, then run this check again:\n");
  for (const f of failed) console.log(`    - ${f.name}${f.detail ? `: ${f.detail}` : ""}`);
  console.log("");
  process.exit(1);
}

mkdirSync(RECEIPTS, { recursive: true });
const receipt = {
  branch,
  ticket: item,
  status: "PASS",
  at: new Date().toISOString(),
  head,
  tree,
  gates: results.map(({ name, ok, detail }) => ({ name, ok, detail })),
};
writeFileSync(join(RECEIPTS, `${branch.replace(/[^A-Za-z0-9._-]/g, "_")}.json`),
              JSON.stringify(receipt, null, 2));
console.log("  Receipt written. The push to main is now unblocked for this commit.\n");
