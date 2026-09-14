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

// spawnSync goes through a shell on Windows only, so an argument containing a space has
// to be quoted there and must NOT be quoted anywhere else.
const shq = (s) => (process.platform === "win32" ? `"${s}"` : s);

// Which work item, if any, a branch belongs to. Gates 2, 3 and 5 all ask this, and they
// must answer it the same way: a branch that gate 5 treats as a ticket while gate 2 does
// not is incoherent, and the disagreement is exactly where a smuggled change fits.
//
// The old test was /^(W-\d+)/ - anchored and case-sensitive - so "w-04-tenant",
// "feature/W-04-x" and "fix/W-04-x" were all classified as NOT a ticket and took the
// permissive route in gate 5, which is the smuggle done-when 4 asks it to refuse
// (F-38/F-51). A reference anywhere in the name, in any case, now counts. Erring towards
// "this is a ticket" is the safe direction: it makes gate 5 stricter and gate 2 demand a
// spec, so a false positive stops a merge rather than waving one through.
//
// An unreadable branch name is its own answer. It used to mean "not a ticket", so the
// least information bought the most permission; callers must handle unknown explicitly.
function ticketOf(branch) {
  const b = String(branch ?? "").trim();
  if (!b) return { item: null, unknown: true };
  const m = /(?:^|[^A-Za-z0-9])(W-\d+)/i.exec(b);
  return { item: m ? m[1].toUpperCase() : null, unknown: false };
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
  const { item, unknown } = ticketOf(branch);
  if (unknown) return { ok: false, detail: "no branch name from gh pr view - see gate 1" };
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
  const item = ticketOf(prData?.headRefName).item ?? "";
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

// ── 4b. docs/ changed only through a route the gate recognises ──────────────
// Two different questions, deliberately answered differently: "does this PR own a
// ticket's spec" and "may this PR change docs/ at all". Conflating them left every
// non-spec document - a template, a CONVENTIONS rule, a README - with no way in (#100).
//
// The non-ticket route is an approval file written by sync-docs into .claude/outputs/.
// It must be ADDED by this PR's diff, and for every path it covers it must name the git
// blob sha of that file's content as approved.
//
// Its trust model is NOT gate 2's, and the comment here used to claim it was. The
// difference is blast radius. Gate 2's marker authorises exactly one path, and WHICH
// path is fixed outside the approving file, by the branch name. Gate 5's marker
// authorises a list of paths chosen by the author, written in the very file that grants
// itself the authority - scope and permission in one hand. The digest is what narrows
// that: the permission is for specific bytes, not for a path for ever.
//
// What the digest proves: the docs/ content being merged is byte-identical to the
// content that existed when the approval was written, and that the approval is new in
// this PR rather than an old one touched to re-open the paths it lists. What it does
// not prove: who wrote the marker, or the digest. Anyone who can edit an approval file
// can recompute a digest, exactly as anyone who can edit check-done.mjs can delete this
// gate. This is a process gate - it stops drift and accident, not an adversary - and
// turning it into a security boundary needs signatures, not more regex.

// The documented filename, including the date prefix. Without the date this matched a
// file called only "docs-approval-.md", and any unrelated report whose name happened to
// contain the token (F-40).
const DOCS_APPROVAL_FILE =
  /^\.claude\/outputs\/\d{4}-\d{2}-\d{2}-docs-approval-[A-Za-z0-9][A-Za-z0-9._-]*\.md$/;

// One path per bullet, in the shape sync-docs writes:
//     - <backtick>docs/CONVENTIONS.md<backtick> @ <backtick>40-hex blob sha<backtick>
// The whole path sits in backticks so a path containing a space can be approved at all
// (F-39), and nothing else is allowed on the line. Exact match only: no prefix or directory matching, or one
// bullet reading docs/target-state/ would sign off everything beneath it unread.
const DOCS_APPROVAL_BULLET =
  /^\s*[-*]\s+`([^`]+)`\s*(?:@|[:\u2013\u2014])\s*`([0-9a-f]{40}|[0-9a-f]{64})`\s*$/;

// Markdown fences hide text from a reader's eye but not from a regex. Without this, the
// example approval printed inside sync-docs/SKILL.md is itself a passing approval file,
// and a document headed "DRAFT - not yet approved" passes because the marker and the
// bullets sit in an illustrative block (F-50/E5). An unterminated fence swallows the
// rest of the file, which fails closed.
function stripFences(body) {
  const out = [];
  let fence = "";
  for (const line of body.split(NL)) {
    const m = /^\s*(`{3,}|~{3,})/.exec(line);
    if (!fence) {
      if (m) { fence = m[1]; continue; }
      out.push(line);
    } else if (m && m[1][0] === fence[0] && m[1].length >= fence.length && !line.slice(m[0].length).trim()) {
      fence = "";
    }
  }
  return out.join(NL);
}

// Only bullets under the heading sync-docs documents authorise anything. Harvesting the
// whole body meant a path listed under "## Explicitly NOT approved" was approved
// regardless (F-52/E10). Returns null when the section is absent, which is different
// from present and empty and must read differently in the failure message.
function pathsCoveredSection(body) {
  const lines = body.split(NL);
  const start = lines.findIndex((l) => /^\s{0,3}#{2,6}\s+paths\s+covered\s*$/i.test(l));
  if (start < 0) return null;
  const rest = lines.slice(start + 1);
  const end = rest.findIndex((l) => /^\s{0,3}#{1,6}\s+\S/.test(l));
  return (end < 0 ? rest : rest.slice(0, end)).join(NL);
}

// A bullet that is not a plain, literal repository path authorises nothing, and says so
// rather than quietly entering the allowed set - where it would make the set non-empty
// without covering anything and flip the diagnostic (F-43). Rejecting the double quote
// also keeps shq() below safe.
function plausibleDocsPath(p) {
  if (!p.startsWith("docs/")) return false;
  if (/["\\]/.test(p) || /[\u0000-\u001f]/.test(p)) return false;
  return p.split("/").every((seg) => seg !== "" && seg !== "." && seg !== "..");
}

// The blob sha of the path as it exists in the commit that would be merged. Content
// only - the same bytes hash the same in any checkout, which is what makes the approval
// re-checkable by somebody else. null when the path is absent at HEAD.
function blobShaAtHead(p) {
  const r = sh("git", ["rev-parse", shq(`HEAD:${p}`)]);
  const out = String(r.out || "").trim();
  return r.code === 0 && /^[0-9a-f]{40,64}$/.test(out) ? out : null;
}

function approvedDocsPaths(prFiles) {
  const allowed = new Map();   // docs/ path -> blob sha recorded as approved
  const seen = [];             // approval files that authorised at least one path
  const problems = [];         // why an approval file authorised less than it looks like
  for (const f of prFiles.filter((x) => DOCS_APPROVAL_FILE.test(x.path))) {
    const rel = f.path;
    // Only files in THIS PR's diff count, and only ones it ADDS. Requiring merely that
    // the approval appear in the diff meant an old approval already on main could be
    // re-opened by touching it, re-authorising every path it lists for brand-new
    // content - the "authorises anything for ever after" failure this was meant to stop
    // (F-30). changeType comes from gh pr view and is relative to the base branch, so a
    // file added and then amended within the PR still reads ADDED.
    if (f.changeType && f.changeType !== "ADDED") {
      problems.push(`${rel} is ${String(f.changeType).toLowerCase()} by this PR, not added - an approval covers the change it shipped with, once`);
      continue;
    }
    const abs = join(ROOT, rel);
    // The diff supplies the filename; the body is read from this checkout. They are the
    // same file only when the check runs on the PR's head commit, which /merge does.
    if (!existsSync(abs)) {
      problems.push(`${rel} is in the diff but absent from this checkout - run the check on the PR's head commit`);
      continue;
    }
    let body;
    try {
      body = readFileSync(abs, "utf8");
    } catch (e) {
      problems.push(`${rel} could not be read (${String(e.message ?? e).slice(0, 40)}) - unreadable fails closed`);
      continue;
    }
    body = stripFences(body);
    // Same marker as gate 2, so there is one thing to remember rather than two.
    if (!/\*\*Approved/i.test(body) && !/Status\*\*.*Approved/i.test(body)) {
      problems.push(`${rel} has no "**Approved" marker outside a code fence - it is still a draft`);
      continue;
    }
    const section = pathsCoveredSection(body);
    if (section === null) {
      problems.push(`${rel} is approved but has no "## Paths covered" section - it authorises nothing`);
      continue;
    }
    let any = false;
    for (const line of section.split(NL)) {
      if (!/^\s*[-*]\s+\S/.test(line)) continue;          // prose inside the section
      const m = DOCS_APPROVAL_BULLET.exec(line);
      if (!m) {
        problems.push(`${rel}: cannot parse "${line.trim().slice(0, 50)}" - expected a path and a blob sha, both in backticks`);
        continue;
      }
      if (!plausibleDocsPath(m[1])) {
        problems.push(`${rel}: "${m[1].slice(0, 50)}" is not a plain docs/ file path - it authorises nothing`);
        continue;
      }
      allowed.set(m[1], m[2]);
      any = true;
    }
    if (any) seen.push(rel);
    else problems.push(`${rel} is approved but lists no usable path under "Paths covered"`);
  }
  return { allowed, seen, problems };
}

gate("docs/ changed only by a recognised route", () => {
  const branch = prData?.headRefName ?? "";
  const { item, unknown } = ticketOf(branch);
  const files = prData?.files ?? [];
  const docs = files.map((f) => f.path).filter((f) => f.startsWith("docs/"));
  // An unreadable branch name used to fall through to the permissive route, so the least
  // information bought the most permission (F-51). It fails closed instead.
  if (unknown) return { ok: false, detail: "no branch name from gh pr view, so this PR cannot be classified - see gate 1" };
  if (item) {
    // A ticket PR stays strict, and an approval file does NOT widen it. The whole value
    // of this gate is that a feature branch cannot carry an unrelated docs/ edit along
    // with it; letting an approval waive that would hand every feature PR the exception.
    // A docs-only change of its own goes to main as its own PR.
    const own = `docs/target-state/features/${item}-`;
    const bad = docs.filter((f) => !f.startsWith(own));
    return bad.length
      ? { ok: false, detail: `branch "${branch}" is ticket ${item}: only ${own}*.md may ride with it, but it changes ${bad.slice(0, 3).join(", ")} - send those as their own docs PR` }
      : { ok: true, detail: docs.length ? `${docs.length} file(s), all ${item}'s own spec` : "no docs/ change" };
  }
  if (!docs.length) return { ok: true, detail: "no docs/ change" };
  const { allowed, seen, problems } = approvedDocsPaths(files);
  // Five causes used to print one identical message, including the case where the file
  // exists, is in the diff and lists the path but is not approved - indistinguishable
  // from a gate that is itself broken, which is what :53-55 of this file forbids (F-33).
  if (!seen.length) {
    return problems.length
      ? { ok: false, detail: `docs/ changed, no approval holds: ${problems.join("; ")}`.slice(0, 200) }
      : { ok: false, detail: `docs/ changed (${docs.slice(0, 3).join(", ")}) and this PR adds no .claude/outputs/<date>-docs-approval-<slug>.md - run /sync-docs` };
  }
  const unlisted = docs.filter((f) => !allowed.has(f));
  if (unlisted.length) {
    const extra = problems.length ? ` (also: ${problems[0]})` : "";
    return { ok: false, detail: `${unlisted.slice(0, 3).join(", ")} not listed in ${seen.join(", ")}${extra}`.slice(0, 200) };
  }
  const drift = [];
  for (const f of docs) {
    const head = blobShaAtHead(f);
    if (head === null) drift.push(`${f} is not readable at HEAD`);
    else if (head !== allowed.get(f)) drift.push(`${f} is ${head.slice(0, 10)} at HEAD, approved as ${allowed.get(f).slice(0, 10)}`);
  }
  if (drift.length) {
    return { ok: false, detail: `content changed since approval: ${drift.slice(0, 2).join("; ")} - re-run /sync-docs on what is actually being merged`.slice(0, 200) };
  }
  return { ok: true, detail: `${docs.length} doc(s), digests match, approved by ${seen.join(", ")}` };
});

// ── 5. ddl-auto is set nowhere ──────────────────────────────────────────────
gate("ddl-auto set nowhere", () => {
  // Scope and pattern kept identical to ci.yml's static job (W-03, F-20) - if these two
  // ever disagree, one of them is lying about the tree. legacy/ is excluded because the
  // frozen apps really do set ddl-auto; docs/, .md and .claude/ because they discuss it
  // in prose. Everything else - infra/, .github/, the repo root - is in scope, since
  // DDL_AUTO as a container or App Service env var is exactly how it would come back.
  const r = sh("git", ["grep", "-nE",
    "^[^#]*(ddl-auto|DDL_AUTO)([[:space:]]*[:=]|[^A-Za-z0-9]*$)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).claude/", ":(exclude)*.md"]);
  return r.code === 0 && r.out.trim()
    ? { ok: false, detail: r.out.trim().split("\n")[0] }
    : { ok: true };
});

// ── 6. no floating point money ──────────────────────────────────────────────
gate("No float or double money field", () => {
  const r = sh("git", ["grep", "-nE",
    "(private|public|protected)[[:space:]]+(Double|Float|double|float)[[:space:]]+[a-zA-Z]*(amount|salary|pay|Pay|Amount|Salary|deduction|Deduction|tax|Tax)",
    "--", ".", ":(exclude)legacy/", ":(exclude)docs/", ":(exclude).claude/", ":(exclude)*.md"]);
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
  // The display name at the top of .github/workflows/ci.yml ("name: CI"). gh reports
  // workflowName as that display name, never the filename, so this is what the fallback
  // below has to compare against - and it must be kept in step with ci.yml if renamed.
  const CI_WORKFLOW_NAME = "CI";
  let r = sh("gh", ["run", "list", "--workflow=ci.yml", "--commit", sha,
                    "--json", FIELDS, "--limit", "10"]);
  let via = "ci.yml";
  let filterWorkflowLocally = false;
  if (r.code !== 0 && /not found on the default branch/i.test(r.out)) {
    // ── BOOTSTRAP FALLBACK - DELETE ONCE ci.yml IS ON THE DEFAULT BRANCH ──────
    // --workflow resolves against the DEFAULT branch, so on the very PR that introduces
    // ci.yml the filter 404s even though the run exists. This branch exists solely for
    // that one PR. It becomes dead code the moment a commit containing
    // .github/workflows/ci.yml is on the default branch (main) - at which point the
    // primary --workflow call can no longer 404, and this block must be removed rather
    // than left to be re-interpreted by a future gh whose error wording differs. The
    // trigger is a substring match on gh's message, which is not API-stable.
    r = sh("gh", ["run", "list", "--commit", sha, "--json", FIELDS, "--limit", "10"]);
    // Dropping --workflow widens the query to every workflow for the SHA, so the
    // workflow identity has to be re-imposed here; otherwise a green run of some
    // unrelated workflow would be credited as CI passing.
    filterWorkflowLocally = true;
    via = `${CI_WORKFLOW_NAME} (matched locally; ci.yml not yet on default branch)`;
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
  let mine = (Array.isArray(runs) ? runs : []).filter((x) => x.headSha === sha);
  if (!mine.length) return { ok: false, detail: `no CI run for ${short} - push the branch and wait for ci.yml` };
  if (filterWorkflowLocally) {
    // Fail closed: an entry with no workflowName cannot be shown to be CI, so it is
    // discarded rather than given the benefit of the doubt.
    mine = mine.filter((x) => x.workflowName === CI_WORKFLOW_NAME);
    if (!mine.length) {
      return { ok: false, detail: `no "${CI_WORKFLOW_NAME}" workflow run for ${short} - other workflows do not count` };
    }
  }
  // Every run for the commit must be finished and green. One green run alongside a red
  // one is a red commit; gh lists the latest attempt per run, so a re-run that fixed a
  // failure shows as success here rather than leaving the old failure behind.
  const pending = mine.find((x) => x.status !== "completed");
  if (pending) {
    return { ok: false, detail: `CI still ${pending.status} for ${short}: ${pending.url ?? ""}`.slice(0, 160) };
  }
  // "skipped" is NOT a pass. A skipped run verified nothing about this commit, which is
  // precisely what this gate exists to prevent - and it is indistinguishable from green
  // in the Checks UI. ci.yml has no paths: filter today, but W-54 and W-59 are both
  // expected to add conditions to it, and the day one lands a skipped run must block the
  // merge and say so rather than quietly counting as proof.
  const skipped = mine.find((x) => x.conclusion === "skipped");
  if (skipped) {
    return { ok: false, detail: `CI was SKIPPED for ${short} - it verified nothing: ${skipped.url ?? ""}`.slice(0, 160) };
  }
  const bad = mine.find((x) => x.conclusion !== "success");
  if (bad) {
    return { ok: false, detail: `CI ${bad.conclusion ?? "had no conclusion"} for ${short}: ${bad.url ?? ""}`.slice(0, 160) };
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
