#!/usr/bin/env node
// verify-app.mjs — PostToolUse hook for Edit|Write.
// Maps the edited file to backend/ or frontend/ and runs that side's compile or lint.
// Informational only: always exits 0, never blocks. Result goes back to the model as
// additionalContext and is echoed to stdout. Prints "SKIP <reason>" when the toolchain
// or dependencies are missing, or when the same app was verified in the last 20 s.

import { readFileSync, existsSync, writeFileSync, mkdirSync, readdirSync } from "node:fs";
import { resolve, relative, sep, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const WIN = process.platform === "win32";
const DEBOUNCE_MS = 20_000;
const TIMEOUT_MS = 240_000;

// first path segment -> how to verify it.
//
// Only the platform is verified. legacy/ is frozen reference and is never built here:
// it resolves to "legacy", which is not a key, so an edit there is skipped. guard-edit
// blocks such an edit outright anyway.
const APPS = {
  "backend":  { kind: "maven", label: "compile" },
  "frontend": { kind: "npm",   label: "lint", cmd: ["npm", "run", "lint", "--silent"] },
};

function out(msg) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: `verify-app: ${msg}` },
    }) + "\n",
  );
  process.exit(0);
}

let input = {};
try { input = JSON.parse(readFileSync(0, "utf8") || "{}"); } catch { out("SKIP malformed hook input"); }

const target = input?.tool_input?.file_path ?? input?.tool_input?.path;
if (!target) out("SKIP no file_path in tool input");

const rel = relative(ROOT, resolve(input.cwd ?? ROOT, target)).split(sep).join("/");
const app = rel.split("/")[0];
const spec = APPS[app];
if (!spec) out(`SKIP ${rel} is not inside an app folder (harness/docs edit)`);

const appDir = join(ROOT, app);

// debounce: many edits in a row should not trigger many compiles
const stampDir = join(ROOT, "agents", "outputs", ".verify-cache");
const stamp = join(stampDir, `${app}.last`);
try {
  mkdirSync(stampDir, { recursive: true });
  if (existsSync(stamp) && Date.now() - Number(readFileSync(stamp, "utf8")) < DEBOUNCE_MS)
    out(`SKIP ${app} verified <${DEBOUNCE_MS / 1000}s ago (debounced)`);
  writeFileSync(stamp, String(Date.now()));
} catch { /* cache is best-effort */ }

// toolchain / dependency checks -> SKIP, never fail
const env = { ...process.env };
let cmd, args;
if (spec.kind === "maven") {
  if (!env.JAVA_HOME) {
    const guess = WIN ? "C:\\Program Files\\Microsoft" : "/usr/lib/jvm";
    try {
      const jdk = readdirSync(guess).find((d) => /^jdk-21/.test(d));
      if (jdk) env.JAVA_HOME = join(guess, jdk);
    } catch { /* ignore */ }
  }
  if (!env.JAVA_HOME) out(`SKIP JAVA_HOME not set and no JDK 21 found — install JDK 21 (see build-baseline)`);
  env.PATH = `${join(env.JAVA_HOME, "bin")}${WIN ? ";" : ":"}${env.PATH ?? ""}`;
  // Prefer the installed Maven (MAVEN_HOME, else C:\Tools\apache-maven-3.9.*). The committed
  // Payroll mvnw.cmd breaks on the space in %USERPROFILE% ("C:\Users\S Banerjee"), so the
  // wrapper is only a fallback when no system Maven exists.
  let mvnHome = env.MAVEN_HOME;
  if (!mvnHome && WIN) {
    try {
      const d = readdirSync("C:\\Tools").find((x) => /^apache-maven-3\.9/.test(x));
      if (d) mvnHome = join("C:\\Tools", d);
    } catch { /* ignore */ }
  }
  const sysMvn = mvnHome ? join(mvnHome, "bin", WIN ? "mvn.cmd" : "mvn") : null;
  const wrapper = join(appDir, WIN ? "mvnw.cmd" : "mvnw");
  if (sysMvn && existsSync(sysMvn)) cmd = sysMvn;
  else if (existsSync(wrapper) && existsSync(join(appDir, ".mvn", "wrapper", "maven-wrapper.properties"))) cmd = wrapper;
  else out(`SKIP ${app}: no Maven found (MAVEN_HOME unset, C:\\Tools\\apache-maven-* absent, .mvn/wrapper missing)`);
  args = ["-q", "compile"];
} else {
  if (!existsSync(join(appDir, "node_modules")))
    out(`SKIP ${app}/node_modules missing — run npm install`);
  [cmd, ...args] = spec.cmd;
  if (WIN) cmd += ".cmd";
}

const t0 = Date.now();
const r = spawnSync(cmd, args, { cwd: appDir, env, encoding: "utf8", timeout: TIMEOUT_MS, shell: WIN, windowsHide: true });
const secs = ((Date.now() - t0) / 1000).toFixed(1);

if (r.error && r.error.code === "ETIMEDOUT") out(`SKIP ${app} ${spec.label} timed out after ${TIMEOUT_MS / 1000}s`);
if (r.error) out(`SKIP ${app} could not start ${cmd}: ${r.error.message}`);

const text = `${r.stdout ?? ""}${r.stderr ?? ""}`.trim();
const tail = text.split("\n").slice(-25).join("\n");
if (r.status === 0) {
  const summary = spec.kind === "npm" ? (text.match(/✖ .*$/m)?.[0] ?? "clean") : "clean";
  out(`PASS ${app} ${spec.label} (${secs}s) — ${summary}`);
}
out(`FAIL ${app} ${spec.label} (exit ${r.status}, ${secs}s) — triggered by ${rel}\n${tail}`);
