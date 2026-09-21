#!/usr/bin/env node
// guard-edit.mjs — PreToolUse hook for replace_file_content|write_to_file|multi_replace_file_content.
// Blocks writes to protected paths: docs/**, legacy/**, any application*.properties, any .env* file.
// Outputs JSON: {"decision": "deny", "reason": "..."} or {"decision": "allow"}.

import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { resolve, relative, sep, basename } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");

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
const target = args?.TargetFile ?? args?.targetFile ?? args?.file_path ?? args?.path;
if (!target) reply("allow");

const abs = resolve(input.cwd ?? ROOT, target);
const rel = relative(ROOT, abs).split(sep).join("/");
const name = basename(abs);

let ownTicket = "";
try {
  const branch = execFileSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
    cwd: ROOT,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
  }).trim();
  ownTicket = (/^(W-\d+)-/i.exec(branch) ?? [])[1]?.toUpperCase() ?? "";
} catch {
  ownTicket = "";
}

const isTicketSpec =
  ownTicket !== "" &&
  new RegExp(`^docs/target-state/features/${ownTicket}-[^/]+\\.md$`, "i").test(rel);

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
  const msg = `guard-edit: BLOCKED ${rel}\n  - ${reasons.join("\n  - ")}`;
  reply("deny", msg);
} else {
  reply("allow");
}
