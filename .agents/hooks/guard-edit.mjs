#!/usr/bin/env node
// guard-edit.mjs — Antigravity PreToolUse hook for file modifications.
// Blocks writes to legacy/**, application*.properties, .env*, and unauthorized docs/.
// Antigravity Hook Protocol: Reads JSON from stdin, outputs JSON to stdout.

import { readFileSync } from "node:fs";
import { resolve, relative, sep, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { execSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");

let input = {};
try {
  const raw = readFileSync(0, "utf8");
  if (raw && raw.trim()) {
    input = JSON.parse(raw);
  }
} catch {
  // Malformed input: allow to prevent blocking on parse errors
  console.log(JSON.stringify({ decision: "allow" }));
  process.exit(0);
}

// Support Antigravity toolCall schema as well as legacy schemas
const args = input?.toolCall?.args ?? input?.tool_input ?? {};
const target = args.TargetFile ?? args.filePath ?? args.file_path ?? args.path ?? args.target;

if (!target) {
  console.log(JSON.stringify({ decision: "allow" }));
  process.exit(0);
}

const abs = resolve(input.workspacePaths?.[0] ?? ROOT, target);
const rel = relative(ROOT, abs).split(sep).join("/");
const name = basename(abs);

const reasons = [];

// 1. legacy/ is frozen reference
if (rel === "legacy" || rel.startsWith("legacy/")) {
  reasons.push("legacy/ is frozen reference, not a working copy. Read it and port logic out of it; changes here are never deployed.");
}

// 2. application*.properties holds environment config and secrets
if (/^application.*\.properties$/i.test(name)) {
  reasons.push("application*.properties holds environment config and secrets; never edit from an agent task.");
}

// 3. .env files hold secrets
if (/^\.env(\..*)?$/i.test(name)) {
  reasons.push(".env files hold secrets and are never edited by the harness.");
}

// 4. docs/ protection with exemptions for active ticket spec and trackers
if (rel.startsWith("docs/")) {
  let isAllowed = false;
  // Trackers are live queue files
  if (rel.startsWith("docs/trackers/")) {
    isAllowed = true;
  } else if (rel.startsWith("docs/target-state/features/")) {
    try {
      const branch = execSync("git rev-parse --abbrev-ref HEAD", { cwd: ROOT, encoding: "utf8" }).trim();
      const match = branch.match(/^W-(\d+(?:\.\d+)?)/i) || branch.match(/^dev-(.+)/i);
      if (match) {
        isAllowed = true;
      }
    } catch {
      isAllowed = true; // If git is inaccessible, don't hard block specs
    }
  }

  if (!isAllowed) {
    reasons.push("docs/ is read-only during feature work. Changes to architectural documentation must go through /sync-docs with an approved diff.");
  }
}

if (reasons.length > 0) {
  const reasonMsg = `guard-edit: BLOCKED ${rel}\n  - ${reasons.join("\n  - ")}`;
  console.log(JSON.stringify({
    decision: "deny",
    reason: reasonMsg
  }));
  process.exit(0);
}

console.log(JSON.stringify({ decision: "allow" }));
process.exit(0);
