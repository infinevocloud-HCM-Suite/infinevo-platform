#!/usr/bin/env node
// check-done.mjs — the definition of done, made machine-checkable.
//
//   node .agents/scripts/check-done.mjs W-nn     (a feature: its spec must exist)
//   node .agents/scripts/check-done.mjs          (harness or tooling work, no ticket)
//
// Run by /merge on the developer's branch (dev-<name>) before the founder merges it.
// The ticket comes from the argument, not the branch name: one developer branch carries
// one feature at a time, and the branch is reused for the next.
//
// Exit 0 = every gate passed. Exit 1 = at least one failed.

import { existsSync, readdirSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync, execSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");

const results = [];
function gate(name, fn) {
  let ok = false, detail = "";
  try {
    const r = fn();
    ok = r.ok;
    detail = r.detail ?? "";
  } catch (e) {
    ok = false;
    detail = String(e.message ?? e).slice(0, 160);
  }
  results.push({ name, ok, detail });
}

function sh(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, {
    cwd: opts.cwd ? join(ROOT, opts.cwd) : ROOT,
    encoding: "utf8",
    shell: opts.shell !== undefined ? opts.shell : process.platform === "win32",
    timeout: opts.timeout ?? 600_000,
    input: opts.input,
  });
  return { code: r.status, out: `${r.stdout ?? ""}${r.stderr ?? ""}` };
}

const NL = String.fromCharCode(10);
function lastLine(out) {
  const l = String(out || "").trim().split(NL);
  return (l[l.length - 1] || "").slice(0, 140);
}

const arg = (process.argv[2] ?? "").trim();
const m = /^w-?(\d{1,4}(?:\.\d{1,2})?)$/i.exec(arg);
if (arg && !m) {
  console.error(`check-done: "${arg}" is not a ticket - pass W-nn (e.g. W-12 or W-12.1), or nothing for harness work`);
  process.exit(1);
}
const item = m ? `W-${m[1]}` : null;

const branch = sh("git", ["rev-parse", "--abbrev-ref", "HEAD"]).out.trim();
const head = sh("git", ["rev-parse", "HEAD"]).out.trim();

// Everything this branch adds on top of main, against the merge base.
const changed = sh("git", ["diff", "--name-only", "main...HEAD"]).out
  .split(NL).map((s) => s.trim()).filter(Boolean);

// ── 1. the spec exists ──────────────────────────────────────────────────────
gate("Spec exists", () => {
  if (!item) return { ok: true, detail: "no ticket given; harness or tooling work" };
  const dir = join(ROOT, "docs", "target-state", "features");
  if (!existsSync(dir)) return { ok: false, detail: "features folder missing" };
  const dotted = item.replace(".", "-");
  const spec = readdirSync(dir).find((f) => f.startsWith(item + "-") || f.startsWith(dotted + "-"));
  return spec ? { ok: true, detail: spec } : { ok: false, detail: `no spec for ${item} in docs/target-state/features/` };
});

// ── 2. nothing frozen was edited ────────────────────────────────────────────
gate("legacy/ untouched", () => {
  const bad = changed.filter((f) => f.startsWith("legacy/"));
  return bad.length
    ? { ok: false, detail: bad.slice(0, 3).join(", ") }
    : { ok: true, detail: `${changed.length} files changed` };
});

// ── 3. ddl-auto is set nowhere ──────────────────────────────────────────────
gate("ddl-auto set nowhere", () => {
  const r = sh("git", ["grep", "-nE",
    "^[^#]*(ddl-auto|DDL_AUTO)([[:space:]]*[:=]|[^A-Za-z0-9]*$)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).agents/", ":(exclude).claude/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split(NL)[0] }
    : { ok: true };
});

// ── 4. no floating point money ──────────────────────────────────────────────
gate("No float or double money field", () => {
  const r = sh("git", ["grep", "-nE",
    "(private|public|protected)[[:space:]]+(Double|Float|double|float)[[:space:]]+[a-zA-Z]*(amount|salary|pay|Pay|Amount|Salary|deduction|Deduction|tax|Tax)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).agents/", ":(exclude).claude/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split(NL)[0] }
    : { ok: true };
});

// ── 5. CI is green for the exact commit being merged ────────────────────────
gate("CI green for this commit", () => {
  if (!head) return { ok: false, detail: "cannot resolve HEAD" };
  const short = head.slice(0, 7);

  const IGNORED = (f) =>
    f.startsWith("docs/") || f.startsWith(".agents/") || f.startsWith(".claude/") || f.startsWith("legacy/") || f.endsWith(".md");
  if (changed.length && changed.every(IGNORED)) {
    return { ok: true, detail: `${changed.length} file(s) changed, none CI covers - no run expected` };
  }

  const coveredUnchangedSince = (anc) => {
    const d = sh("git", ["diff", "--name-only", anc, head]);
    if (d.code !== 0) return false;
    return d.out.split(NL).map((x) => x.trim()).filter(Boolean).every(IGNORED);
  };
  const FIELDS = "status,conclusion,url,headSha,workflowName";
  const runsFor = (sha) => {
    const res = sh("gh", ["run", "list", "--workflow=ci.yml", "--commit", sha, "--json", FIELDS, "--limit", "10"]);
    if (res.code === 0) return res;
    // Fall back to GitHub REST API if gh CLI is not available
    const token = (process.env.GITHUB_TOKEN || process.env.GH_TOKEN || (() => {
      const cred = sh("git", ["credential", "fill"], { input: "protocol=https\nhost=github.com\n\n" });
      return (cred.out.match(/password=([^\r\n]+)/) || [])[1];
    })())?.trim();
    const curl = sh("curl.exe", [
      "-s",
      "-A",
      "check-done",
      ...(token ? ["-H", `Authorization: token ${token}`] : []),
      `https://api.github.com/repos/infinevocloud-HCM-Suite/infinevo-platform/actions/workflows/ci.yml/runs?head_sha=${encodeURIComponent(sha)}&per_page=10`,
    ], { shell: false });
    if (curl.code === 0 && curl.out.trim()) {
      try {
        const data = JSON.parse(curl.out);
        const mapped = (data.workflow_runs || []).map((w) => ({
          status: w.status,
          conclusion: w.conclusion,
          url: w.html_url,
          headSha: w.head_sha,
          workflowName: w.name,
        }));
        return { code: 0, out: JSON.stringify(mapped) };
      } catch {}
    }
    return res;
  };

  let r = runsFor(head);
  let credited = head;
  let note = "";
  if (r.code === 0) {
    let probe;
    try { probe = JSON.parse(r.out); } catch { probe = null; }
    if (!(Array.isArray(probe) ? probe : []).some((x) => x.headSha === head)) {
      const anc = sh("git", ["rev-list", "--max-count=25", `${head}^`]);
      const candidates = anc.code === 0 ? anc.out.split(NL).map((x) => x.trim()).filter(Boolean) : [];
      for (const c of candidates) {
        if (!coveredUnchangedSince(c)) break;
        const cr = runsFor(c);
        if (cr.code !== 0) continue;
        let cruns;
        try { cruns = JSON.parse(cr.out); } catch { continue; }
        if ((Array.isArray(cruns) ? cruns : []).some((x) => x.headSha === c)) {
          r = cr;
          credited = c;
          note = ` (no run for ${short}; credited ${c.slice(0, 7)}, nothing CI covers changed since)`;
          break;
        }
      }
    }
  }
  if (r.code !== 0) {
    return { ok: false, detail: `gh run list failed: ${lastLine(r.out) || `exit ${r.code}`}`.slice(0, 160) };
  }
  let runs;
  try { runs = JSON.parse(r.out); } catch {
    return { ok: false, detail: `gh run list returned unparseable output: ${lastLine(r.out)}`.slice(0, 160) };
  }
  const mine = (Array.isArray(runs) ? runs : []).filter((x) => x.headSha === credited);
  if (!mine.length) return { ok: false, detail: `no CI run for ${short} - push the branch and wait for ci.yml` };
  const pending = mine.find((x) => x.status !== "completed");
  if (pending) return { ok: false, detail: `CI still ${pending.status} for ${short}: ${pending.url ?? ""}`.slice(0, 160) };
  const skipped = mine.find((x) => x.conclusion === "skipped");
  if (skipped) return { ok: false, detail: `CI was SKIPPED for ${short} - it verified nothing: ${skipped.url ?? ""}`.slice(0, 160) };
  const bad = mine.find((x) => x.conclusion !== "success");
  if (bad) return { ok: false, detail: `CI ${bad.conclusion ?? "had no conclusion"} for ${short}: ${bad.url ?? ""}`.slice(0, 160) };
  return { ok: true, detail: `${mine.length} run(s) success for ${credited.slice(0, 7)}${note}` };
});

// ── report ──────────────────────────────────────────────────────────────────
const w = Math.max(...results.map((r) => r.name.length));
console.log(`\nDefinition of done — ${item ?? "no ticket"} on ${branch} @ ${head.slice(0, 8)}\n`);
for (const r of results) console.log(`  ${r.ok ? "PASS" : "FAIL"}  ${r.name.padEnd(w)}  ${r.detail}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n  ${results.length - failed.length}/${results.length} gates passed\n`);
if (failed.length) {
  console.log("  MERGE BLOCKED. Fix these, then run this check again:\n");
  for (const f of failed) console.log(`    - ${f.name}${f.detail ? `: ${f.detail}` : ""}`);
  console.log("");
  process.exit(1);
}

// Write signed merge receipt
try {
  const receiptsDir = join(ROOT, ".agents", "outputs", ".merge-receipts");
  mkdirSync(receiptsDir, { recursive: true });
  let headTree = "";
  try {
    headTree = execSync("git rev-parse HEAD:", { cwd: ROOT, encoding: "utf8" }).trim();
  } catch {}
  const receipt = {
    branch: branch,
    commit: head,
    tree: headTree,
    status: "PASS",
    timestamp: new Date().toISOString(),
    gates: results.length
  };
  const sanitizedBranch = branch.replace(/[^a-zA-Z0-9._-]/g, "_");
  const receiptPath = join(receiptsDir, `${sanitizedBranch}.json`);
  writeFileSync(receiptPath, JSON.stringify(receipt, null, 2), "utf8");
  console.log(`  Merge receipt issued: ${relative(ROOT, receiptPath)}`);
} catch (err) {
  console.error("  Warning: Failed to write merge receipt:", err.message);
}

console.log("  Ready for the founder to merge.\n");
