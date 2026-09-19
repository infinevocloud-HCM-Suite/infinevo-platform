#!/usr/bin/env node
// guard-merge.mjs — PreToolUse hook for Bash.
//
// One thing reaches main: a push. This decides whether it may.
//
//   a push to main carrying code/ or docs/  — refused unless check-done.mjs wrote a
//                                             PASSING receipt for the exact content
//                                             being pushed
//   anything else                           — allowed
//
// There is no pull request in this flow. /merge squashes the ticket branch onto main
// locally and pushes, so the push IS the merge and this is the only place to stand.
//
// Branch protection is unavailable on the GitHub Free plan (D-43), so main is otherwise
// held by convention alone. This is the enforcement actually available to us: it runs on
// the machine doing the merge, before the command leaves it.
//
// Exit 2 = deny, with the reason on stderr. Exit 0 = allow.
// Fails OPEN on malformed input - a broken hook must not block all work.

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const RECEIPTS = join(ROOT, ".claude", "outputs", ".merge-receipts");

let input = {};
try {
  input = JSON.parse(readFileSync(0, "utf8") || "{}");
} catch {
  process.exit(0); // fail open
}

const raw = input?.tool_input?.command;
if (typeof raw !== "string" || !raw.trim()) process.exit(0);

// Strip heredoc bodies and quoted spans before matching.
//
// Without this the hook fires on the WORDS inside a file being written - documentation
// that describes merging is not merging. It blocked an edit to CONTRIBUTING.md that
// merely explained the command, and then blocked its own fix. A guard with false
// positives gets switched off, and then it guards nothing.
function commandOnly(text) {
  let t = text;
  const HEREDOC = new RegExp("<<-?\\s*'?\"?([A-Za-z_][A-Za-z0-9_]*)'?\"?[\\s\\S]*?^\\1\\s*$", "gm");
  t = t.replace(HEREDOC, " <<STRIPPED ");
  t = t.replace(/'[^']*'/g, " '' ");
  t = t.replace(/"[^"]*"/g, ' "" ');
  return t;
}
const cmd = commandOnly(raw);

function deny(lines) {
  process.stderr.write(`guard-merge: BLOCKED\n  - ${lines.join("\n  - ")}\n`);
  process.exit(2);
}

function git(args) {
  const r = spawnSync("git", args, { cwd: ROOT, encoding: "utf8" });
  return (r.stdout ?? "").trim();
}

// ── the push to main ────────────────────────────────────────────────────────
if (!/\bgit\s+push\b/.test(cmd)) process.exit(0);

const branch = git(["rev-parse", "--abbrev-ref", "HEAD"]);
const targetsMain =
  /\bgit\s+push\b[^|;&]*\bmain\b/.test(cmd) || (branch === "main" && !/\s-\S*[bu]\s/.test(cmd));
if (!targetsMain) process.exit(0);

// What this push would add to main. Only code/ and docs/ need a receipt - a harness or
// tooling commit straight to main is routine and always has been.
const changed = git(["diff", "--name-only", "origin/main..HEAD"]).split("\n").filter(Boolean);
const guarded = changed.filter((f) => f.startsWith("code/") || f.startsWith("docs/"));
if (!guarded.length) process.exit(0);

// The receipt is matched on the TREE, not the commit. /merge squashes the ticket branch
// onto main, so the commit is new even though the bytes are the ones that were checked.
// The tree is exactly "what was checked", and it survives the squash - while a main that
// moved underneath changes it, which correctly forces a re-check.
const tree = git(["rev-parse", "HEAD^{tree}"]);

let receipts = [];
try {
  receipts = readdirSync(RECEIPTS)
    .filter((f) => f.endsWith(".json"))
    .map((f) => {
      try {
        return { file: f, ...JSON.parse(readFileSync(join(RECEIPTS, f), "utf8")) };
      } catch {
        return null;
      }
    })
    .filter(Boolean);
} catch {
  receipts = [];
}

const match = receipts.find((r) => r.status === "PASS" && r.tree && tree && r.tree === tree);

if (!match) {
  const passing = receipts.filter((r) => r.status === "PASS");
  const near = passing.find((r) => r.tree && r.tree !== tree);
  deny(
    [
      `This push puts ${guarded.length} file(s) under code/ or docs/ onto main:`,
      ...guarded.slice(0, 5).map((f) => `    ${f}`),
      guarded.length > 5 ? `    ... and ${guarded.length - 5} more` : "",
      "",
      near
        ? `A passing check exists (${near.branch ?? near.file}) but it was written for different content.`
        : "No passing done-check exists for this content.",
      "Something changed after it was checked, or it was never run.",
      "",
      "  Run:  node .claude/scripts/check-done.mjs",
      "",
      "It checks the spec is approved, no High finding is open, legacy/ is untouched,",
      "docs/ changed only by a recognised route, ddl-auto is set nowhere, money is not a",
      "floating-point type, and CI is green for this exact commit. A receipt is written",
      "only when every gate passes.",
    ].filter(Boolean),
  );
}

process.exit(0); // receipt is passing, and for exactly this content
