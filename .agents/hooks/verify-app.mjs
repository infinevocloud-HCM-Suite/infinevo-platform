#!/usr/bin/env node
// verify-app.mjs — Antigravity PostToolUse hook.
// Debounced continuous verification (backend compile / frontend lint) after file edits.

import { readFileSync, writeFileSync, existsSync, statSync, mkdirSync } from "node:fs";
import { resolve, relative, join, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { execSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const CACHE_DIR = join(ROOT, ".agents", "outputs", ".verify-cache");
const DEBOUNCE_MS = 20000;

let input = {};
try {
  const raw = readFileSync(0, "utf8");
  if (raw && raw.trim()) {
    input = JSON.parse(raw);
  }
} catch {
  console.log(JSON.stringify({}));
  process.exit(0);
}

const args = input?.toolCall?.args ?? input?.tool_input ?? {};
const target = args.TargetFile ?? args.filePath ?? args.file_path ?? args.path ?? args.target;

if (!target) {
  console.log(JSON.stringify({}));
  process.exit(0);
}

const abs = resolve(input.workspacePaths?.[0] ?? ROOT, target);
const rel = relative(ROOT, abs).split(sep).join("/");

if (!existsSync(CACHE_DIR)) {
  mkdirSync(CACHE_DIR, { recursive: true });
}

function shouldRun(name) {
  const stampFile = join(CACHE_DIR, `${name}.stamp`);
  if (!existsSync(stampFile)) return true;
  try {
    const last = parseInt(readFileSync(stampFile, "utf8"), 10);
    return Date.now() - last > DEBOUNCE_MS;
  } catch {
    return true;
  }
}

function updateStamp(name) {
  const stampFile = join(CACHE_DIR, `${name}.stamp`);
  try {
    writeFileSync(stampFile, String(Date.now()), "utf8");
  } catch {}
}

if (rel.startsWith("code/backend/")) {
  if (shouldRun("backend")) {
    updateStamp("backend");
    try {
      const mvnCmd = process.platform === "win32" ? ".\\mvnw.cmd" : "./mvnw";
      execSync(`${mvnCmd} -B test-compile -q`, {
        cwd: join(ROOT, "code", "backend"),
        encoding: "utf8",
        timeout: 120000
      });
      console.error("[verify-app] code/backend compiled cleanly.");
    } catch (err) {
      console.error(`[verify-app] code/backend compilation failed:\n${err.stdout || err.stderr || err.message}`);
    }
  }
} else if (rel.startsWith("code/frontend/")) {
  if (shouldRun("frontend")) {
    updateStamp("frontend");
    try {
      execSync("npm run lint --silent", {
        cwd: join(ROOT, "code", "frontend"),
        encoding: "utf8",
        timeout: 60000
      });
      console.error("[verify-app] code/frontend lint passed.");
    } catch (err) {
      console.error(`[verify-app] code/frontend lint warnings/errors:\n${err.stdout || err.stderr || err.message}`);
    }
  }
}

console.log(JSON.stringify({}));
process.exit(0);
