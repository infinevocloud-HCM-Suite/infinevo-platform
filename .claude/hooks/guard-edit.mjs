#!/usr/bin/env node
// guard-edit.mjs — PreToolUse hook for Edit|Write.
// Blocks writes to protected paths: docs/**, legacy/**, any application*.properties, any .env* file.
// Exit 2 = deny (Claude Code shows the stderr reason to the model). Exit 0 = allow.
// Docs change only via the `sync-docs` skill with a founder-approved diff (root CLAUDE.md, hard rule 3).

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

// The one sanctioned docs/ write: a ticket's own spec. `review-spec` moves an approved
// draft to docs/target-state/features/W-nn-<slug>.md and sets its Status, and
// check-done.mjs:384 already allows a ticket's pull request to change docs/ only under
// that prefix. Everything else in docs/ - including the two TEMPLATE files, which do not
// match W-nn- - stays read-only and travels through `sync-docs`.
const isTicketSpec = /^docs\/target-state\/features\/W-\d+-[^/]+\.md$/.test(rel);

const reasons = [];
if ((rel === "docs" || rel.startsWith("docs/")) && !isTicketSpec)
  reasons.push("docs/ is read-only during feature work; propose changes via the sync-docs skill and get founder approval");
if (rel === "legacy" || rel.startsWith("legacy/"))
  reasons.push("legacy/ is frozen reference, not a working copy. Read it and port logic out of it; a change here is not deployed anywhere and will be deleted. If it is a genuine production defect, raise it with the founder (see legacy/README.md)");
if (/^application.*\.properties$/i.test(name))
  reasons.push("application*.properties holds environment config and secrets; never edit from a task (hard rule 3)");
if (/^\.env(\..*)?$/i.test(name))
  reasons.push(".env files hold secrets and are never edited by the harness");

if (reasons.length) {
  process.stderr.write(`guard-edit: BLOCKED ${rel}\n  - ${reasons.join("\n  - ")}\n`);
  process.exit(2);
}
process.exit(0);
