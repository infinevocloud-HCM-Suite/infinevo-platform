#!/usr/bin/env node
// guard-merge.mjs — PreToolUse hook for Bash.
//
// Two things reach main, and both are guarded here:
//
//   1. a PR merge command   — refused unless check-done.mjs wrote a PASSING receipt
//                             for that PR, at the commit currently on the branch
//   2. `git push` to main   — refused when the push carries changes under code/
//
// Branch protection is unavailable on the GitHub Free plan (D-43), so main is otherwise
// held by convention alone. This is the enforcement actually available to us: it runs
// on the machine doing the merge, before the command leaves it.
//
// Exit 2 = deny, with the reason on stderr. Exit 0 = allow.
// Fails OPEN on malformed input - a broken hook must not block all work.

import { existsSync, readFileSync } from "node:fs";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const RECEIPTS = join(ROOT, ".claude", "outputs", ".merge-receipts");
const MAX_AGE_MS = 60 * 60 * 1000; // an hour. Long enough to be convenient, short
                                   // enough that it cannot become a permanent pass.

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

// ── 1. the PR merge command ─────────────────────────────────────────────────
const MERGE = new RegExp("\\bgh\\s+pr\\s+merge(?:\\s+(\\d+))?");
const merge = MERGE.exec(cmd);
if (merge) {
  const pr = merge[1];
  if (!pr) {
    deny([
      "A PR merge without an explicit number is refused, so the done-check can be",
      "  matched to the pull request it was run for. Name it: ... merge <number> --squash",
    ]);
  }

  const path = join(RECEIPTS, `pr-${pr}.json`);
  if (!existsSync(path)) {
    deny([
      `No done-check receipt for PR #${pr}.`,
      "The definition of done has not been verified for this pull request.",
      "",
      `  Run:  node .claude/scripts/check-done.mjs ${pr}`,
      "",
      "It checks the spec is approved, no High finding is open, legacy/ is untouched,",
      "ddl-auto is set nowhere, money is not a floating-point type, and both builds pass.",
      "A receipt is written only when every gate passes.",
    ]);
  }

  let receipt;
  try {
    receipt = JSON.parse(readFileSync(path, "utf8"));
  } catch {
    deny([`Receipt for PR #${pr} is unreadable. Re-run: node .claude/scripts/check-done.mjs ${pr}`]);
  }

  if (receipt.status !== "PASS") {
    deny([`Receipt for PR #${pr} records status ${receipt.status}, not PASS.`]);
  }

  const age = Date.now() - Date.parse(receipt.at);
  if (!(age >= 0) || age > MAX_AGE_MS) {
    deny([
      `Receipt for PR #${pr} is ${Math.round(age / 60000)} minutes old (limit 60).`,
      `Re-run: node .claude/scripts/check-done.mjs ${pr}`,
    ]);
  }

  const head = git(["rev-parse", "HEAD"]);
  if (receipt.head && head && receipt.head !== head) {
    deny([
      `The receipt for PR #${pr} was written for commit ${receipt.head.slice(0, 8)},`,
      `  but HEAD is now ${head.slice(0, 8)}. Code changed after it was checked.`,
      `Re-run: node .claude/scripts/check-done.mjs ${pr}`,
    ]);
  }

  process.exit(0); // receipt is valid, fresh, and for this commit
}

// ── 2. git push straight to main ────────────────────────────────────────────
if (/\bgit\s+push\b/.test(cmd)) {
  const branch = git(["rev-parse", "--abbrev-ref", "HEAD"]);
  const targetsMain =
    /\bgit\s+push\b[^|;&]*\bmain\b/.test(cmd) || (branch === "main" && !/\s-\S*[bu]\s/.test(cmd));

  if (targetsMain) {
    // Only code needs a pull request. Docs and harness commits to main are routine.
    const changed = git(["diff", "--name-only", "origin/main..HEAD"]).split("\n").filter(Boolean);
    const code = changed.filter((f) => f.startsWith("code/"));
    if (code.length) {
      deny(
        [
          `This push puts ${code.length} file(s) under code/ straight onto main:`,
          ...code.slice(0, 5).map((f) => `    ${f}`),
          code.length > 5 ? `    ... and ${code.length - 5} more` : "",
          "",
          "Code reaches main through a reviewed pull request, never directly.",
          "",
          "  git checkout -b W-nn-<slug>",
          "  git push -u origin W-nn-<slug>",
          "  gh pr create --fill",
          "",
          "Docs and harness changes on main are fine - this only guards code/.",
        ].filter(Boolean),
      );
    }
  }
}

process.exit(0);
