#!/usr/bin/env node
// verify-app.mjs — PostToolUse hook for replace_file_content|write_to_file|multi_replace_file_content.
// Maps the edited file to backend/ or frontend/ and runs that side's compile or lint.
// Informational only: always exits 0, never blocks. Outputs {} to stdout.

import { readFileSync, existsSync, writeFileSync, mkdirSync, readdirSync } from "node:fs";
import { resolve, relative, sep, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const WIN = process.platform === "win32";
const DEBOUNCE_MS = 20_000;
const TIMEOUT_MS = 240_000;

const APPS = {
  "code/backend":  { kind: "maven", label: "compile" },
  "code/frontend": { kind: "npm",   label: "lint", cmd: ["npm", "run", "lint", "--silent"] },
};

function out(msg) {
  process.stdout.write("{}\n");
  if (msg) console.log(`verify-app: ${msg}`);
  process.exit(0);
}

let input = {};
try { input = JSON.parse(readFileSync(0, "utf8") || "{}"); } catch { out("SKIP malformed hook input"); }

const args = input?.toolCall?.args ?? input?.tool_input ?? input;
const target = args?.TargetFile ?? args?.targetFile ?? args?.file_path ?? args?.path;
if (!target) out("SKIP no file target in tool call");

const rel = relative(ROOT, resolve(input.cwd ?? ROOT, target)).split(sep).join("/");
const app = Object.keys(APPS).find((k) => rel === k || rel.startsWith(k + "/"));
const spec = app ? APPS[app] : undefined;
if (!spec) out(`SKIP ${rel} is not inside a build folder (harness/docs/legacy edit)`);

const appDir = join(ROOT, app);

const stampDir = join(ROOT, ".agents", "outputs", ".verify-cache");
const stamp = join(stampDir, `${app.replace("/", "-")}.last`);
try {
  mkdirSync(stampDir, { recursive: true });
  if (existsSync(stamp) && Date.now() - Number(readFileSync(stamp, "utf8")) < DEBOUNCE_MS)
    out(`SKIP ${app} verified <${DEBOUNCE_MS / 1000}s ago (debounced)`);
  writeFileSync(stamp, String(Date.now()));
} catch { /* cache is best-effort */ }

const env = { ...process.env };
let cmd, cmdArgs;
if (spec.kind === "maven") {
  if (!env.JAVA_HOME) {
    const guess = WIN ? "C:\\Program Files\\Microsoft" : "/usr/lib/jvm";
    try {
      const jdk = readdirSync(guess).find((d) => /^jdk-21/.test(d));
      if (jdk) env.JAVA_HOME = join(guess, jdk);
    } catch { /* ignore */ }
  }
  if (!env.JAVA_HOME) out(`SKIP JAVA_HOME not set and no JDK 21 found`);
  env.PATH = `${join(env.JAVA_HOME, "bin")}${WIN ? ";" : ":"}${env.PATH ?? ""}`;
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
  else out(`SKIP ${app}: no Maven found`);
  cmdArgs = ["-q", "compile"];
} else {
  if (!existsSync(join(appDir, "node_modules")))
    out(`SKIP ${app}/node_modules missing — run npm install`);
  [cmd, ...cmdArgs] = spec.cmd;
  if (WIN) cmd += ".cmd";
}

const t0 = Date.now();
const r = spawnSync(cmd, cmdArgs, { cwd: appDir, env, encoding: "utf8", timeout: TIMEOUT_MS, shell: WIN, windowsHide: true });
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
