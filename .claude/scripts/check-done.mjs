#!/usr/bin/env node
// check-done.mjs — the definition of done, made machine-checkable.
//
//   node .claude/scripts/check-done.mjs            (reads the branch you are on)
//
// Runs every gate, prints a table, and on success writes a receipt:
//   .claude/outputs/.merge-receipts/<branch>.json
//
// The guard-merge hook refuses a push to main without a fresh passing receipt for the
// commit at HEAD. Branch protection is unavailable on the GitHub Free plan (D-43), so
// this is the enforcement that is actually available to us.
//
// There is no pull request in this flow. A branch is squashed onto main by /merge, and
// everything a PR used to prove - what changed, that CI was green for it - is proved
// here from the branch itself.
//
// Exit 0 = every gate passed, receipt written. Exit 1 = at least one failed, no receipt.

import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync } from "node:fs";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const RECEIPTS = join(ROOT, ".claude", "outputs", ".merge-receipts");

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

// Which work item, if any, a branch belongs to. Every gate that asks must answer the
// same way: a branch that gate 5 treats as a ticket while gate 2 does not is incoherent,
// and the disagreement is exactly where a smuggled change fits.
//
// The match is unanchored and the separator optional, so W followed by up to four digits
// counts wherever it appears, in any case. That deliberately over-matches: "flow12"
// contains "w12" and is read as a ticket. The cost of that misfire is bounded and
// visible - the gate names the ticket it thinks you are on, and the fix is to rename the
// branch - whereas the cost of under-matching is a smuggled docs/ edit nobody sees.
//
// An unreadable branch name is its own answer, and fails closed. A name that opens "W-"
// with no number behind it is ticket-shaped but names no ticket, and is refused for the
// same reason rather than read as a non-ticket branch.
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
// The content sha, not the commit sha. /merge squashes this branch onto main, which
// produces a NEW commit for the same bytes - so a receipt pinned to the commit could
// never match the push it is meant to authorise. The tree is what was actually checked,
// and it survives the squash. If main moved underneath, the tree differs and the check
// has to run again, which is correct: those are different bytes.
const tree = sh("git", ["rev-parse", "HEAD^{tree}"]).out.trim();
const { item, unknown, why } = ticketOf(branch);

// Everything this branch adds on top of main. The three-dot form is against the merge
// base, so a main that moved on underneath does not show up as this branch's work.
const changed = sh("git", ["diff", "--name-only", "main...HEAD"]).out
  .split(NL).map((s) => s.trim()).filter(Boolean);

// ── 1. the branch says what it is ───────────────────────────────────────────
gate("Branch names a ticket", () => {
  if (unknown) return { ok: false, detail: `${why} - rename it W-nn-<slug> or docs-<slug>` };
  if (item) return { ok: true, detail: `${branch} -> ${item}` };
  if (/^docs[-/]/i.test(branch)) return { ok: true, detail: `${branch} -> docs-only branch` };
  // Harness, tooling and process work is real work. There is no W-nn for it because
  // there is no work item, and requiring one would mean inventing a fake ticket - a gate
  // people fake is worse than no gate. It still has to say what it is.
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
  const dir = join(ROOT, ".claude", "outputs");
  if (!existsSync(dir)) return { ok: true, detail: "no reports" };
  if (!item) return { ok: true, detail: "not a W-nn ticket; no findings to match" };
  const reports = readdirSync(dir).filter(
    (f) => f.endsWith(".md") && (f.includes(`verify-${item}`) || f.includes(`review-${item}`)),
  );
  const open = [];
  for (const f of reports) {
    for (const line of readFileSync(join(dir, f), "utf8").split(NL)) {
      // | F-1 | High | ... | OPEN |
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
// One rule, read off the branch name: a ticket branch may change its own spec and
// nothing else under docs/; a docs branch may change docs/ and nothing else. The point
// is that nobody rewrites the design documents while shipping a feature - a docs change
// travels on its own, reviewed for what it says rather than waved through with code.
//
// This replaces a 280-line approval-file mechanism (blob shas, markdown fence stripping,
// added-not-edited checks) whose whole purpose was to police a pull request diff. With
// no pull request there is nothing for it to read, and the founder reads the docs at
// merge anyway. It was a process gate, never a security boundary, and it said so.
gate("docs/ changed only by a recognised route", () => {
  if (unknown) return { ok: false, detail: "cannot classify the branch - see gate 1" };
  const docs = changed.filter((f) => f === "docs" || f.startsWith("docs/"));
  const isDocsBranch = /^docs[-/]/i.test(branch);

  if (isDocsBranch) {
    // What must not ride along is code and infrastructure - a docs change that quietly
    // ships a behaviour change is exactly what this gate exists to stop. The harness is
    // a different matter: /sync-docs updates .claude/work/active-work.md as its last
    // step and writes its patch under .claude/outputs/, so refusing those would refuse
    // the skill's own documented output.
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
  // Scope and pattern kept identical to ci.yml's static job (W-03, F-20) - if these two
  // ever disagree, one of them is lying about the tree. legacy/ is excluded because the
  // frozen apps really do set ddl-auto; docs/, .md and .claude/ because they discuss it
  // in prose. Everything else - infra/, .github/, the repo root - is in scope, since
  // DDL_AUTO as a container or App Service env var is exactly how it would come back.
  const r = sh("git", ["grep", "-nE",
    "^[^#]*(ddl-auto|DDL_AUTO)([[:space:]]*[:=]|[^A-Za-z0-9]*$)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).claude/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split(NL)[0] }
    : { ok: true };
});

// ── 7. no floating point money ──────────────────────────────────────────────
gate("No float or double money field", () => {
  const r = sh("git", ["grep", "-nE",
    "(private|public|protected)[[:space:]]+(Double|Float|double|float)[[:space:]]+[a-zA-Z]*(amount|salary|pay|Pay|Amount|Salary|deduction|Deduction|tax|Tax)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).claude/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split(NL)[0] }
    : { ok: true };
});

// ── 8. CI is green for the exact commit being merged ────────────────────────
// This is also where the build and the tests are checked. They used to run again here,
// on the same bytes CI had just built, which answered a question already answered and
// cost ten minutes every time. CI runs backend verify, frontend lint and build, and the
// static checks; this gate refuses unless that run went green for THIS commit.
//
// GitHub refuses branch protection on a private repository on the Free plan (D-43), so a
// red CI run cannot be a required check. The enforcement therefore lives here. Pinning to
// the SHA matters - a green run on an earlier commit says nothing about what is being
// merged, which is the same reasoning guard-merge uses to void a receipt after a commit.
gate("CI green for this commit", () => {
  const sha = head;
  if (!sha) return { ok: false, detail: "cannot resolve HEAD" };
  const short = sha.slice(0, 7);
  const FIELDS = "status,conclusion,url,headSha,workflowName";
  const r = sh("gh", ["run", "list", "--workflow=ci.yml", "--commit", sha,
                      "--json", FIELDS, "--limit", "10"]);
  if (r.code !== 0) {
    // gh missing, unauthenticated, or the API refused. All of those are a failure to
    // prove CI, not a pass by default.
    return { ok: false, detail: `gh run list failed: ${lastLine(r.out) || `exit ${r.code}`}`.slice(0, 160) };
  }
  let runs;
  try {
    runs = JSON.parse(r.out);
  } catch {
    return { ok: false, detail: `gh run list returned unparseable output: ${lastLine(r.out)}`.slice(0, 160) };
  }
  // Belt and braces: --commit is a server-side filter, but the gate is worthless if it
  // ever credits a run from another commit, so check the SHA that came back too.
  const mine = (Array.isArray(runs) ? runs : []).filter((x) => x.headSha === sha);
  if (!mine.length) return { ok: false, detail: `no CI run for ${short} - push the branch and wait for ci.yml` };
  // Every run for the commit must be finished and green. One green run alongside a red
  // one is a red commit; gh lists the latest attempt per run, so a re-run that fixed a
  // failure shows as success here rather than leaving the old failure behind.
  const pending = mine.find((x) => x.status !== "completed");
  if (pending) {
    return { ok: false, detail: `CI still ${pending.status} for ${short}: ${pending.url ?? ""}`.slice(0, 160) };
  }
  // "skipped" is NOT a pass. A skipped run verified nothing about this commit, which is
  // precisely what this gate exists to prevent - and it is indistinguishable from green
  // in the Checks UI.
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
