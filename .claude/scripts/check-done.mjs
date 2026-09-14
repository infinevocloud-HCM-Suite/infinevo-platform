#!/usr/bin/env node
// check-done.mjs — the definition of done, made machine-checkable.
//
//   node .claude/scripts/check-done.mjs <pr-number>
//
// Runs every gate, prints a table, and on success writes a receipt:
//   .claude/outputs/.merge-receipts/pr-<n>.json
//
// The guard-merge hook refuses `gh pr merge` without a fresh passing receipt for that
// PR. Branch protection is unavailable on the GitHub Free plan (D-43), so this is the
// enforcement that is actually available to us.
//
// Exit 0 = every gate passed, receipt written. Exit 1 = at least one failed, no receipt.

import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync } from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const RECEIPTS = join(ROOT, ".claude", "outputs", ".merge-receipts");
const pr = process.argv[2];

if (!pr || !/^\d+$/.test(pr)) {
  console.error("usage: node .claude/scripts/check-done.mjs <pr-number>");
  process.exit(1);
}

const results = [];
function gate(name, fn, { blocking = true } = {}) {
  let ok = false, detail = "";
  try {
    const r = fn();
    ok = r.ok;
    detail = r.detail ?? "";
  } catch (e) {
    ok = false;
    detail = String(e.message ?? e).slice(0, 160);
  }
  results.push({ name, ok, detail, blocking });
}

function sh(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, {
    cwd: opts.cwd ? join(ROOT, opts.cwd) : ROOT,
    encoding: "utf8",
    shell: process.platform === "win32",
    timeout: opts.timeout ?? 600_000,
  });
  return { code: r.status, out: `${r.stdout ?? ""}${r.stderr ?? ""}` };
}

// Pulling the useful line out of build output, without escape sequences that a
// generator can mangle. A gate that fails must say WHY, or it is indistinguishable
// from a gate that is itself broken - which cost an hour on PR #95.
const NL = String.fromCharCode(10);
function lastLine(out) {
  const l = String(out || "").trim().split(NL);
  return (l[l.length - 1] || "").slice(0, 140);
}
function firstError(out) {
  return (String(out || "").split(NL).find((l) => /ERROR/.test(l)) || "").slice(0, 140);
}

// ── 1. the PR exists, is open, and closes an issue ──────────────────────────
let prData = null;
gate("PR is open and links its issue", () => {
  // headRefOid is fetched here so gate 8 can pin the CI result to this exact commit
  // without shelling out to gh a second time.
  const r = sh("gh", ["pr", "view", pr, "--json", "state,title,body,headRefName,headRefOid,files"]);
  if (r.code !== 0) return { ok: false, detail: "gh pr view failed" };
  prData = JSON.parse(r.out);
  if (prData.state !== "OPEN") return { ok: false, detail: `state is ${prData.state}` };
  const closes = /\bcloses #(\d+)/i.exec(prData.body ?? "");
  if (!closes) return { ok: false, detail: 'body has no "Closes #<issue>"' };
  return { ok: true, detail: `#${pr} -> closes #${closes[1]}` };
});

// ── 2. the spec exists and is approved ──────────────────────────────────────
gate("Approved spec exists", () => {
  const dir = join(ROOT, "docs", "target-state", "features");
  if (!existsSync(dir)) return { ok: false, detail: "features folder missing" };
  const branch = prData?.headRefName ?? "";
  const item = /^(W-\d+)/.exec(branch)?.[1];
  // Not every change is a W-nn ticket from the plan. Harness, tooling and process work
  // is real work and still needs an issue and every other gate - but there is no spec
  // for it, because there is no work item. Requiring one would mean inventing a fake
  // ticket, and a gate people fake is worse than no gate.
  if (!item) {
    const issue = /closes\s+#(\d+)/i.exec(prData?.body ?? "");
    return issue
      ? { ok: true, detail: `not a W-nn ticket; governed by issue #${issue[1]} instead` }
      : { ok: false, detail: `branch "${branch}" is not a W-nn ticket AND links no issue - one or the other is required` };
  }
  const spec = readdirSync(dir).find((f) => f.startsWith(item + "-"));
  if (!spec) return { ok: false, detail: `no spec for ${item}` };
  const body = readFileSync(join(dir, spec), "utf8");
  if (!/\*\*Approved/i.test(body) && !/Status\*\*.*Approved/i.test(body))
    return { ok: false, detail: `${spec} is not marked approved` };
  return { ok: true, detail: spec };
});

// ── 3. no High finding left open ────────────────────────────────────────────
gate("No open High findings", () => {
  const dir = join(ROOT, ".claude", "outputs");
  if (!existsSync(dir)) return { ok: true, detail: "no reports" };
  const item = /^(W-\d+)/.exec(prData?.headRefName ?? "")?.[1] ?? "";
  const reports = readdirSync(dir).filter(
    (f) => f.endsWith(".md") && (f.includes(`verify-${item}`) || f.includes(`review-pr-${pr}`)),
  );
  const open = [];
  for (const f of reports) {
    for (const line of readFileSync(join(dir, f), "utf8").split("\n")) {
      // | F-1 | High | ... | OPEN |
      if (/^\|\s*`?F-\d+/.test(line) && /\bHigh\b/i.test(line) && /\bOPEN\b/.test(line)) {
        open.push(`${f}: ${(/`?(F-\d+)/.exec(line) ?? [])[1]}`);
      }
    }
  }
  return open.length
    ? { ok: false, detail: open.join(", ") }
    : { ok: true, detail: reports.length ? `${reports.length} report(s), none High/OPEN` : "no reports" };
});

// ── 4. nothing frozen was edited ────────────────────────────────────────────
gate("legacy/ untouched", () => {
  const files = (prData?.files ?? []).map((f) => f.path);
  const bad = files.filter((f) => f.startsWith("legacy/"));
  return bad.length ? { ok: false, detail: bad.slice(0, 3).join(", ") } : { ok: true, detail: `${files.length} files changed` };
});

gate("docs/ changed only for this ticket's spec", () => {
  const item = /^(W-\d+)/.exec(prData?.headRefName ?? "")?.[1] ?? "";
  const docs = (prData?.files ?? []).map((f) => f.path).filter((f) => f.startsWith("docs/"));
  if (!item) {
    // Process work may legitimately change docs/, but it must go through sync-docs
    // with an approved diff - so flag it for the reviewer rather than silently allowing.
    return docs.length
      ? { ok: false, detail: `non-ticket PR changes docs/: ${docs.slice(0, 3).join(", ")} - use sync-docs` }
      : { ok: true, detail: "no docs/ change" };
  }
  const bad = docs.filter((f) => !f.includes(`features/${item}-`));
  return bad.length ? { ok: false, detail: bad.slice(0, 3).join(", ") } : { ok: true };
});

// ── 5. ddl-auto is set nowhere ──────────────────────────────────────────────
gate("ddl-auto set nowhere", () => {
  const r = sh("git", ["grep", "-nE", "^[^#]*ddl-auto[[:space:]]*[:=]", "--", "code/"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split("\n")[0] }
    : { ok: true };
});

// ── 6. no floating point money ──────────────────────────────────────────────
gate("No float or double money field", () => {
  const r = sh("git", ["grep", "-nE",
    "(private|public|protected)[[:space:]]+(Double|Float|double|float)[[:space:]]+[a-zA-Z]*(amount|salary|pay|Pay|Amount|Salary|deduction|Deduction|tax|Tax)",
    "--", "code/"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split("\n")[0] }
    : { ok: true };
});

// ── 7. the build, including the module boundary ─────────────────────────────
gate("Backend builds and tests pass", () => {
  // Absolute path on purpose. With shell:true the command resolves against PATH, not
  // cwd, so a bare "mvnw.cmd" is not found and the failure reads as a broken build.
  const mvnw = join(ROOT, "code", "backend",
                    process.platform === "win32" ? "mvnw.cmd" : "mvnw");
  if (!existsSync(mvnw)) return { ok: false, detail: "maven wrapper missing" };
  const r = sh(`"${mvnw}"`, ["-B", "-q", "clean", "verify"], { cwd: "code/backend" });
  if (r.code !== 0) {
    const line = firstError(r.out) || lastLine(r.out) || `exit ${r.code}, no output`;
    return { ok: false, detail: `exit ${r.code}: ${line}`.slice(0, 160) };
  }
  return { ok: true, detail: "BUILD SUCCESS" };
});

gate("Frontend lints and builds", () => {
  if (!existsSync(join(ROOT, "code", "frontend", "node_modules")))
    return { ok: false, detail: "node_modules missing - run npm ci" };
  const lint = sh("npm", ["run", "lint", "--silent"], { cwd: "code/frontend" });
  if (lint.code !== 0) return { ok: false, detail: `lint exit ${lint.code}: ${lastLine(lint.out)}` };
  const build = sh("npm", ["run", "build", "--silent"], { cwd: "code/frontend" });
  return build.code === 0
    ? { ok: true, detail: "lint clean, build ok" }
    : { ok: false, detail: `build exit ${build.code}: ${lastLine(build.out)}` };
});

// ── 8. CI is green for the exact commit being merged ────────────────────────
// GitHub refuses branch protection on a private repository on the Free plan (D-43), so
// a red CI run cannot be a required check and cannot block the merge button. The
// enforcement therefore lives here, next to every other gate: the receipt is written
// only when ci.yml has completed successfully for the PR's HEAD commit. Pinning to the
// SHA matters - a green run on an earlier commit says nothing about what is being
// merged, which is the same reasoning guard-merge already uses to expire a receipt
// after a new commit.
gate("CI green for this commit", () => {
  const sha = prData?.headRefOid;
  if (!sha) return { ok: false, detail: "no HEAD sha from gh pr view - see gate 1" };
  const short = sha.slice(0, 7);
  const FIELDS = "status,conclusion,url,headSha,workflowName";
  let r = sh("gh", ["run", "list", "--workflow=ci.yml", "--commit", sha,
                    "--json", FIELDS, "--limit", "10"]);
  let via = "ci.yml";
  if (r.code !== 0 && /not found on the default branch/i.test(r.out)) {
    // Bootstrap only: --workflow resolves against the DEFAULT branch, so on the very PR
    // that introduces ci.yml the filter 404s even though the run exists. Fall back to
    // every run for this SHA. The SHA pin is what does the work; dropping the workflow
    // filter widens the gate, it does not weaken the commit guarantee.
    r = sh("gh", ["run", "list", "--commit", sha, "--json", FIELDS, "--limit", "10"]);
    via = "any workflow (ci.yml not yet on default branch)";
  }
  if (r.code !== 0) {
    // gh missing, unauthenticated, or the API refused. All of those are a failure to
    // prove CI, not a pass by default.
    return { ok: false, detail: `gh run list failed: ${lastLine(r.out) || `exit ${r.code}`}`.slice(0, 160) };
  }
  let runs;
  try {
    runs = JSON.parse(r.out);
  } catch {
    return { ok: false, detail: `gh run list returned unparseable output: ${lastLine(r.out)}`.slice(0, 160) };
  }
  // Belt and braces: --commit is a server-side filter, but the gate is worthless if it
  // ever credits a run from another commit, so check the SHA that came back too.
  const mine = (Array.isArray(runs) ? runs : []).filter((x) => x.headSha === sha);
  if (!mine.length) return { ok: false, detail: `no CI run for ${short} - push the branch and wait for ci.yml` };
  // Every run for the commit must be finished and green. One green run alongside a red
  // one is a red commit; gh lists the latest attempt per run, so a re-run that fixed a
  // failure shows as success here rather than leaving the old failure behind.
  const pending = mine.find((x) => x.status !== "completed");
  if (pending) {
    return { ok: false, detail: `CI still ${pending.status} for ${short}: ${pending.url ?? ""}`.slice(0, 160) };
  }
  const bad = mine.find((x) => x.conclusion !== "success" && x.conclusion !== "skipped");
  if (bad) {
    return { ok: false, detail: `CI ${bad.conclusion} for ${short}: ${bad.url ?? ""}`.slice(0, 160) };
  }
  return { ok: true, detail: `${mine.length} run(s) success for ${short} via ${via}`.slice(0, 160) };
});

// ── report ──────────────────────────────────────────────────────────────────
const w = Math.max(...results.map((r) => r.name.length));
console.log(`\nDefinition of done — PR #${pr}\n`);
for (const r of results) {
  console.log(`  ${r.ok ? "PASS" : "FAIL"}  ${r.name.padEnd(w)}  ${r.detail}`);
}

const failed = results.filter((r) => !r.ok && r.blocking);
console.log(`\n  ${results.length - failed.length}/${results.length} gates passed\n`);

if (failed.length) {
  console.log("  MERGE BLOCKED. Fix these, then run this check again:\n");
  for (const f of failed) console.log(`    - ${f.name}${f.detail ? `: ${f.detail}` : ""}`);
  console.log("");
  process.exit(1);
}

mkdirSync(RECEIPTS, { recursive: true });
const receipt = {
  pr: Number(pr),
  status: "PASS",
  at: new Date().toISOString(),
  head: sh("git", ["rev-parse", "HEAD"]).out.trim(),
  gates: results.map(({ name, ok, detail }) => ({ name, ok, detail })),
};
writeFileSync(join(RECEIPTS, `pr-${pr}.json`), JSON.stringify(receipt, null, 2));
console.log(`  Receipt written. \`gh pr merge ${pr}\` is now unblocked for this commit.\n`);
