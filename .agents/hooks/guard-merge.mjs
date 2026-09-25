#!/usr/bin/env node
// guard-merge.mjs — Antigravity PreToolUse hook for run_command.
// Guards against unverified pushes to main without a passing check-done.mjs receipt.

import { readFileSync, existsSync, readdirSync } from "node:fs";
import { resolve, join } from "node:path";
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
  console.log(JSON.stringify({ decision: "allow" }));
  process.exit(0);
}

const toolName = input?.toolCall?.name ?? input?.tool ?? input?.tool_name ?? "";
const cmd = input?.toolCall?.args?.CommandLine ?? input?.tool_input?.command ?? input?.toolCall?.args?.command ?? "";

if (!cmd || (toolName !== "run_command" && toolName !== "Bash" && toolName !== "execute_command")) {
  console.log(JSON.stringify({ decision: "allow" }));
  process.exit(0);
}

// Detect git push targeting main
const isPushToMain = /\bgit\s+push\b.*(\bmain\b|\bHEAD:main\b)/i.test(cmd) ||
  (/\bgit\s+push\b/i.test(cmd) && (() => {
    try {
      const b = execSync("git rev-parse --abbrev-ref HEAD", { cwd: ROOT, encoding: "utf8" }).trim();
      return b === "main";
    } catch {
      return false;
    }
  })());

if (!isPushToMain) {
  console.log(JSON.stringify({ decision: "allow" }));
  process.exit(0);
}

// Verify receipt in .agents/outputs/.merge-receipts/
try {
  const receiptsDir = join(ROOT, ".agents", "outputs", ".merge-receipts");
  if (!existsSync(receiptsDir)) {
    console.log(JSON.stringify({
      decision: "deny",
      reason: "guard-merge: BLOCKED push to main — no .merge-receipts directory found. Run 'node .agents/scripts/check-done.mjs W-nn' to verify the definition of done first."
    }));
    process.exit(0);
  }

  let headTree = "";
  try {
    headTree = execSync("git rev-parse HEAD:", { cwd: ROOT, encoding: "utf8" }).trim();
  } catch {
    // fallback if HEAD: fails
  }
  const headCommit = execSync("git rev-parse HEAD", { cwd: ROOT, encoding: "utf8" }).trim();

  const receiptFiles = readdirSync(receiptsDir).filter(f => f.endsWith(".json"));
  let validReceipt = false;

  for (const rf of receiptFiles) {
    try {
      const data = JSON.parse(readFileSync(join(receiptsDir, rf), "utf8"));
      if ((data.tree === headTree || data.commit === headCommit) && data.status === "PASS") {
        validReceipt = true;
        break;
      }
    } catch {}
  }

  if (!validReceipt) {
    console.log(JSON.stringify({
      decision: "deny",
      reason: `guard-merge: BLOCKED push to main without a passing check-done receipt for commit ${headCommit.slice(0, 8)}. Run 'node .agents/scripts/check-done.mjs W-nn' before pushing.`
    }));
    process.exit(0);
  }

} catch (err) {
  // Push to main must fail closed if receipt check errors out
  console.log(JSON.stringify({
    decision: "deny",
    reason: `guard-merge: BLOCKED push to main — error verifying receipts: ${err.message}`
  }));
  process.exit(0);
}

console.log(JSON.stringify({ decision: "allow" }));
process.exit(0);
