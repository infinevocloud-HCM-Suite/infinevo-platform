#!/usr/bin/env node
// guard-merge.mjs — PreToolUse hook for run_command.
// Guards against pushes to main without a passing check-done receipt.
// Outputs JSON: {"decision": "deny", "reason": "..."} or {"decision": "allow"}.

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const RECEIPTS = join(ROOT, ".agents", "outputs", ".merge-receipts");

function reply(decision, reason) {
  const out = { decision };
  if (reason) out.reason = reason;
  process.stdout.write(JSON.stringify(out) + "\n");
  process.exit(0);
}

let input = {};
try {
  input = JSON.parse(readFileSync(0, "utf8") || "{}");
} catch {
  reply("allow");
}

const args = input?.toolCall?.args ?? input?.tool_input ?? input;
const raw = args?.CommandLine ?? args?.commandLine ?? args?.command;
if (typeof raw !== "string" || !raw.trim()) reply("allow");

function commandOnly(text) {
  let t = text;
  const HEREDOC = new RegExp("<<-?\\s*'?\"?([A-Za-z_][A-Za-z0-9_]*)'?\"?[\\s\\S]*?^\\1\\s*$", "gm");
  t = t.replace(HEREDOC, " <<STRIPPED ");
  t = t.replace(/'[^']*'/g, " '' ");
  t = t.replace(/"[^"]*"/g, ' "" ');
  return t;
}
const cmd = commandOnly(raw);

function git(gitArgs) {
  const r = spawnSync("git", gitArgs, { cwd: ROOT, encoding: "utf8" });
  return (r.stdout ?? "").trim();
}

if (!/\bgit\s+push\b/.test(cmd)) reply("allow");

const branch = git(["rev-parse", "--abbrev-ref", "HEAD"]);
const targetsMain =
  /\bgit\s+push\b[^|;&]*\bmain\b/.test(cmd) || (branch === "main" && !/\s-\S*[bu]\s/.test(cmd));
if (!targetsMain) reply("allow");

const changed = git(["diff", "--name-only", "origin/main..HEAD"]).split("\n").filter(Boolean);
const guarded = changed.filter((f) => f.startsWith("code/") || f.startsWith("docs/"));
if (!guarded.length) reply("allow");

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
  const denyLines = [
    `guard-merge: BLOCKED push to main without passing check-done receipt`,
    `This push puts ${guarded.length} file(s) under code/ or docs/ onto main:`,
    ...guarded.slice(0, 5).map((f) => `    ${f}`),
    guarded.length > 5 ? `    ... and ${guarded.length - 5} more` : "",
    "",
    near
      ? `A passing check exists (${near.branch ?? near.file}) but it was written for different content.`
      : "No passing done-check exists for this content.",
    "Something changed after it was checked, or it was never run.",
    "",
    "  Run:  node .agents/scripts/check-done.mjs",
  ].filter(Boolean);

  reply("deny", denyLines.join("\n"));
}

reply("allow");
