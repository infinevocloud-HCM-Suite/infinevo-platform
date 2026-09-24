#!/usr/bin/env node
// guard-edit.mjs — PreToolUse hook for Edit|Write.
// Blocks writes to legacy/**, any application*.properties and any .env* file.
// Exit 2 = deny (Claude Code shows the stderr reason to the model). Exit 0 = allow.
// docs/ is writable: the founder writes specs straight into docs/target-state/features/.
import { readFileSync } from "node:fs";
import { resolve, relative, sep, basename } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");

let input = {};
try {
  input = JSON.parse(readFileSync(0, "utf8") || "{}");
} catch {
  process.exit(0); // malformed input: never block on our own bug
}
const target = input?.tool_input?.file_path ?? input?.tool_input?.path;
if (!target) process.exit(0);

const abs = resolve(input.cwd ?? ROOT, target);
const rel = relative(ROOT, abs).split(sep).join("/");
const name = basename(abs);

const reasons = [];
if (rel === "legacy" || rel.startsWith("legacy/"))
  reasons.push("legacy/ is frozen reference, not a working copy. Read it and port logic out of it; a change here is not deployed anywhere");
if (/^application.*\.properties$/i.test(name))
  reasons.push("application*.properties holds environment config and secrets; never edit from a task");
if (/^\.env(\..*)?$/i.test(name))
  reasons.push(".env files hold secrets and are never edited by the harness");

if (reasons.length) {
  process.stderr.write(`guard-edit: BLOCKED ${rel}\n  - ${reasons.join("\n  - ")}\n`);
  process.exit(2);
}
process.exit(0);
