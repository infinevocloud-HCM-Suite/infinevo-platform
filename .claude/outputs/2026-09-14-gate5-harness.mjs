// Harness for gate 5 of check-done.mjs. The gate body, and the ticketOf/shq helpers it
// depends on, are string-sliced VERBATIM out of the shipped file and driven with stubbed
// prData, a virtual disk and a virtual git, so what is tested is what merges rather than
// a copy that can drift.
//
//   node .claude/outputs/2026-09-14-gate5-harness.mjs
//
// Exit 0 = every case behaved as specified. Most cases specify FAIL: this gate's job is
// to refuse, and a guard that matches nothing reports green for ever.
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

// Relative to this file, not to one machine's drive letter (F-54): the harness follows
// the repository, so anyone with a checkout can re-run the evidence.
const FILE = resolve(dirname(fileURLToPath(import.meta.url)), "..", "scripts", "check-done.mjs");
const src = readFileSync(FILE, "utf8");

function slice(startKey, endKey) {
  const start = src.indexOf(startKey);
  const end = src.indexOf(endKey, start + 1);
  if (start < 0 || end < 0 || end < start) {
    throw new Error(`could not slice ${JSON.stringify(startKey)} .. ${JSON.stringify(endKey)} out of check-done.mjs`);
  }
  return src.slice(start, end);
}

const helpers = slice("// spawnSync goes through a shell", "// Pulling the useful line");
const gate5 = slice("// The documented filename, including the date prefix", "// ── 5. ddl-auto");
const body = `${helpers}\n${gate5}`;

// Slicing between two textual markers is only evidence if the region really is the thing
// under test. Asserting the indices exist and are ordered proves nothing about WHAT was
// cut (F-37): a gate inserted between the keys retargets every case below. These checks
// name what the region must contain, and the registration count below proves exactly one
// gate was captured, so a decoy cannot be the one being exercised.
const GATE_TITLE = 'gate("docs/ changed only by a recognised route"';
for (const [what, key] of [
  ["the ticketOf helper", "function ticketOf(branch)"],
  ["the shq helper", "const shq ="],
  ["the approval-file pattern", "const DOCS_APPROVAL_FILE ="],
  ["the fence stripper", "function stripFences("],
  ["the section scoper", "function pathsCoveredSection("],
  ["the path collector", "function approvedDocsPaths("],
  ["gate 5 itself", GATE_TITLE],
]) {
  if (!body.includes(key)) throw new Error(`sliced region does not contain ${what} (${key}) - the slice keys have drifted`);
}
console.log(`sliced ${body.length} chars verbatim from ${FILE}`);
console.log(`slice contains: ticketOf, shq, ${GATE_TITLE.slice(5)}\n`);

const ROOT = "/repo";
const NL = String.fromCharCode(10);

// disk: absolute path -> contents, or the string THROWS to make readFileSync fail on a
// file that exists. head: repo-relative path -> blob sha, standing in for `git rev-parse
// HEAD:<path>`.
const THROWS = Symbol("unreadable");

function run({ branch, files, disk = {}, head = {}, liveGit = false }) {
  let captured = null;
  let registered = 0;
  const gate = (_name, fn) => { captured = fn; registered++; };
  const join = (...p) => p.join("/");
  const existsSync = (p) => Object.prototype.hasOwnProperty.call(disk, p);
  const read = (p) => {
    if (!existsSync(p)) throw new Error("ENOENT: no such file");
    if (disk[p] === THROWS) throw new Error("EACCES: permission denied");
    return disk[p];
  };
  // Stands in for spawnSync+git. Only `git rev-parse HEAD:<path>` is asked for; anything
  // else is a bug in the gate, not something to answer politely.
  const sh = (cmd, args) => {
    if (liveGit) {
      // The real thing, including the shq quoting the gate applies on Windows. Two cases
      // below use this against this repository's own HEAD, so the digest half is not
      // proved against a stub alone.
      const r = spawnSync(cmd, args, { encoding: "utf8", shell: process.platform === "win32" });
      return { code: r.status, out: `${r.stdout ?? ""}${r.stderr ?? ""}` };
    }
    const arg = String(args[1] ?? "").replace(/^"|"$/g, "");
    if (cmd !== "git" || args[0] !== "rev-parse" || !arg.startsWith("HEAD:")) {
      throw new Error(`harness: unexpected command ${cmd} ${args.join(" ")}`);
    }
    const p = arg.slice("HEAD:".length);
    return Object.prototype.hasOwnProperty.call(head, p)
      ? { code: 0, out: `${head[p]}\n` }
      : { code: 128, out: `fatal: path '${p}' does not exist in 'HEAD'\n` };
  };
  const prData = {
    headRefName: branch,
    files: files.map((f) => (typeof f === "string" ? { path: f, changeType: "ADDED" } : f)),
  };
  new Function("join", "existsSync", "readFileSync", "NL", "ROOT", "prData", "gate", "sh", "process", body)
    (join, existsSync, read, NL, ROOT, prData, gate, sh, { platform: process.platform });
  if (registered !== 1) throw new Error(`expected exactly 1 gate in the slice, got ${registered}`);
  return captured();
}

const APPROVAL = ".claude/outputs/2026-09-14-docs-approval-template-infra.md";
const T = "docs/target-state/features/TEMPLATE-INFRA.md";
const C = "docs/CONVENTIONS.md";
const SPACED = "docs/target-state/03 code structure.md";
const sha = (c) => c.repeat(40).slice(0, 40);
const SHA_T = sha("a"), SHA_C = sha("b"), SHA_S = sha("c"), SHA_OTHER = sha("d");

// The shape sync-docs writes. `entries` are [path, sha] pairs.
const approvalFile = (entries, { approved = true, heading = "## Paths covered", extra = "" } = {}) =>
  `# Docs change approval - TEMPLATE-INFRA - 2026-09-14\n\n` +
  `| Field | Value |\n|---|---|\n| **Patch** | \`.claude/outputs/2026-09-14-docs-diff-template-infra.patch\` |\n` +
  `| **Status** | ${approved ? "**Approved 2026-09-14** by the founder" : "Draft - awaiting approval"} |\n\n` +
  `${heading}\n\n` +
  entries.map(([p, s]) => "- `" + p + "` @ `" + s + "`").join("\n") + "\n" + extra;

// E5: everything that matters is inside a fence, and the prose says the opposite.
const fencedApproval = (entries) =>
  `# Docs change approval - DRAFT, NOT YET APPROVED\n\n` +
  "```markdown\n" + approvalFile(entries) + "```\n" +
  `\nThis is only an example of what an approval would look like.\n`;

const at = (p) => `${ROOT}/${p}`;

const cases = [
  // ── the route works at all ────────────────────────────────────────────────
  ["1  non-ticket + docs + approval listing exact path and matching digest", "PASS", {
    branch: "gap-100-docs-route", files: [T, APPROVAL, ".claude/scripts/check-done.mjs"],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T } },
    "digests match"],
  ["2  non-ticket + docs + NO approval file", "FAIL", {
    branch: "gap-100-docs-route", files: [T] },
    "adds no .claude/outputs/"],
  ["3  non-ticket + docs + approval lists a DIFFERENT path", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[C, SHA_C]]) }, head: { [T]: SHA_T, [C]: SHA_C } },
    "not listed in"],
  ["4  non-ticket + docs + approval lists path but is NOT approved", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]], { approved: false }) }, head: { [T]: SHA_T } },
    "still a draft"],
  ["5  non-ticket + docs + approval on disk but NOT in the PR diff", "FAIL", {
    branch: "gap-100-docs-route", files: [T],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T } },
    "adds no .claude/outputs/"],
  ["6  W-nn PR + unrelated docs file + approval listing it", "FAIL", {
    branch: "W-04-tenant-context", files: ["docs/target-state/features/W-04-tenant.md", T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T } },
    "is ticket W-04"],
  ["7  W-nn PR + only its own features/W-nn- spec", "PASS", {
    branch: "W-04-tenant-context", files: ["docs/target-state/features/W-04-tenant.md", "code/backend/core/pom.xml"] },
    "all W-04's own spec"],
  ["8  non-ticket + no docs/ change at all", "PASS", {
    branch: "gap-100-docs-route", files: [".claude/scripts/check-done.mjs"] },
    "no docs/ change"],
  ["9  non-ticket + approval bullet is a DIRECTORY prefix", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([["docs/target-state/", SHA_T]]) }, head: { [T]: SHA_T } },
    "not a plain docs/ file path"],
  ["10 non-ticket + approved marker but NO paths listed", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([]) }, head: { [T]: SHA_T } },
    "lists no usable path"],
  ["11 non-ticket + approval named in the diff, absent from this checkout", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL] },
    "absent from this checkout"],

  // ── F-53: the try/catch, exercised by a file that exists and throws ───────
  ["12 non-ticket + approval file exists but readFileSync THROWS", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: THROWS }, head: { [T]: SHA_T } },
    "unreadable fails closed"],

  // ── F-50 / E5: fenced content must not authorise ──────────────────────────
  ["13 E5 marker and bullets ONLY inside a fenced code block, header says DRAFT", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: fencedApproval([[T, SHA_T], [C, SHA_C]]) }, head: { [T]: SHA_T } },
    "no \"**Approved\" marker outside a code fence"],
  ["14 approved for real, plus a fenced example listing an extra path", "FAIL", {
    branch: "gap-100-docs-route", files: [T, C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]], { extra: "\n```markdown\n- `" + C + "` @ `" + SHA_C + "`\n```\n" }) },
    head: { [T]: SHA_T, [C]: SHA_C } },
    "docs/CONVENTIONS.md not listed"],

  // ── F-52 / E10: section scoping ───────────────────────────────────────────
  ["15 E10 path listed under \"## Explicitly NOT approved\"", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]], { heading: "## Explicitly NOT approved" }) },
    head: { [T]: SHA_T } },
    "no \"## Paths covered\" section"],
  ["16 approved path in Paths covered, extra path under a later NOT-approved heading", "FAIL", {
    branch: "gap-100-docs-route", files: [T, C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]], { extra: "\n## Explicitly NOT approved\n\n- `" + C + "` @ `" + SHA_C + "`\n" }) },
    head: { [T]: SHA_T, [C]: SHA_C } },
    "docs/CONVENTIONS.md not listed"],

  // ── F-51 / E9: branch classification ──────────────────────────────────────
  ["17 E9 lowercase branch w-04-tenant smuggling docs/CONVENTIONS.md", "FAIL", {
    branch: "w-04-tenant", files: [C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[C, SHA_C]]) }, head: { [C]: SHA_C } },
    "is ticket W-04"],
  ["18 branch feature/W-04-x smuggling docs/CONVENTIONS.md", "FAIL", {
    branch: "feature/W-04-x", files: [C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[C, SHA_C]]) }, head: { [C]: SHA_C } },
    "is ticket W-04"],
  ["19 branch fix/W-04-x smuggling docs/CONVENTIONS.md", "FAIL", {
    branch: "fix/W-04-x", files: [C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[C, SHA_C]]) }, head: { [C]: SHA_C } },
    "is ticket W-04"],
  ["20 EMPTY branch name + docs change + approval", "FAIL", {
    branch: "", files: [C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[C, SHA_C]]) }, head: { [C]: SHA_C } },
    "cannot be classified"],
  ["21 EMPTY branch name, no docs change - still fails closed", "FAIL", {
    branch: "", files: [".claude/scripts/check-done.mjs"] },
    "cannot be classified"],
  ["22 lowercase ticket branch carrying ONLY its own spec", "PASS", {
    branch: "w-04-tenant-context", files: ["docs/target-state/features/W-04-tenant.md"] },
    "all W-04's own spec"],

  // ── F-30: content binding and re-authorisation ────────────────────────────
  ["23 approved path, but HEAD content differs from the approved digest", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_OTHER } },
    "content changed since approval"],
  ["24 approved path missing from HEAD entirely", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: {} },
    "not readable at HEAD"],
  ["25 OLD approval merely MODIFIED by this PR, re-authorising new content", "FAIL", {
    branch: "gap-100-docs-route",
    files: [{ path: T, changeType: "MODIFIED" }, { path: APPROVAL, changeType: "MODIFIED" }],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T } },
    "not added"],
  ["26 approval RENAMED into the diff", "FAIL", {
    branch: "gap-100-docs-route",
    files: [T, { path: APPROVAL, changeType: "RENAMED" }],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T } },
    "not added"],
  ["27 bullet carries a path but a truncated 7-char sha", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T.slice(0, 7)]]) }, head: { [T]: SHA_T } },
    "cannot parse"],
  ["28 bullet carries a path and NO sha at all (the pre-fix format)", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([]).replace("\n\n\n", "\n\n") + "- `" + T + "`\n" },
    head: { [T]: SHA_T } },
    "cannot parse"],

  // ── F-39 / F-40 / F-43: paths, filenames, junk ────────────────────────────
  ["29 F-39 a docs/ path containing a SPACE can be approved", "PASS", {
    branch: "gap-100-docs-route", files: [SPACED, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[SPACED, SHA_S]]) }, head: { [SPACED]: SHA_S } },
    "digests match"],
  ["30 F-40 approval filename with no date prefix is not an approval", "FAIL", {
    branch: "gap-100-docs-route", files: [T, ".claude/outputs/docs-approval-.md"],
    disk: { [at(".claude/outputs/docs-approval-.md")]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T } },
    "adds no .claude/outputs/"],
  ["31 F-40 unrelated report whose name merely contains the token", "FAIL", {
    branch: "gap-100-docs-route", files: [T, ".claude/outputs/notes-about-docs-approval-rules.md"],
    disk: { [at(".claude/outputs/notes-about-docs-approval-rules.md")]: approvalFile([[T, SHA_T]]) },
    head: { [T]: SHA_T } },
    "adds no .claude/outputs/"],
  ["32 F-43 junk bullet only - says it cannot parse, not that nothing was approved", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([]) + "- just some notes\n" }, head: { [T]: SHA_T } },
    "cannot parse"],
  ["33 traversal bullet docs/../.claude/scripts/check-done.mjs", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([["docs/../.claude/scripts/check-done.mjs", SHA_T]]) },
    head: { [T]: SHA_T } },
    "not a plain docs/ file path"],
  ["34 backslash bullet docs\\CONVENTIONS.md", "FAIL", {
    branch: "gap-100-docs-route", files: [C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([["docs\\CONVENTIONS.md", SHA_C]]) }, head: { [C]: SHA_C } },
    "no approval holds"],
  ["35 two docs files, only one of them approved", "FAIL", {
    branch: "gap-100-docs-route", files: [T, C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T]]) }, head: { [T]: SHA_T, [C]: SHA_C } },
    "docs/CONVENTIONS.md not listed"],
  ["36 two docs files, both approved with matching digests", "PASS", {
    branch: "gap-100-docs-route", files: [T, C, APPROVAL],
    disk: { [at(APPROVAL)]: approvalFile([[T, SHA_T], [C, SHA_C]]) }, head: { [T]: SHA_T, [C]: SHA_C } },
    "2 doc(s)"],
];

// ── live: the same gate, the real git, this repository's own HEAD ───────────
// Everything above stubs `git rev-parse`. These two do not. The approval body is still
// synthetic, but the digest it claims is compared against a real blob in this checkout,
// which is what exercises spawnSync, shq and the gate's actual command line.
const liveDoc = "docs/CONVENTIONS.md";
const liveSha = (() => {
  const r = spawnSync("git", ["rev-parse", `HEAD:${liveDoc}`], { encoding: "utf8" });
  return r.status === 0 ? String(r.stdout).trim() : null;
})();
if (liveSha) {
  cases.push(
    ["L1 LIVE real git: approval digest matches HEAD for docs/CONVENTIONS.md", "PASS", {
      branch: "docs-live-smoke", files: [liveDoc, APPROVAL], liveGit: true,
      disk: { [at(APPROVAL)]: approvalFile([[liveDoc, liveSha]]) } },
      "digests match"],
    ["L2 LIVE real git: approval digest one character off", "FAIL", {
      branch: "docs-live-smoke", files: [liveDoc, APPROVAL], liveGit: true,
      disk: { [at(APPROVAL)]: approvalFile([[liveDoc, (liveSha[0] === "0" ? "1" : "0") + liveSha.slice(1)]]) } },
      "content changed since approval"],
  );
} else {
  console.log(`(skipped the two live cases: ${liveDoc} is not in HEAD here)`);
}

let bad = 0;
for (const [name, want, input, mustSay] of cases) {
  let r;
  try {
    r = run(input);
  } catch (e) {
    r = { ok: false, detail: `harness threw: ${e.message}` };
  }
  const got = r.ok ? "PASS" : "FAIL";
  const said = !mustSay || String(r.detail ?? "").includes(mustSay);
  const good = got === want && said;
  if (!good) bad++;
  console.log(`${good ? "ok  " : "BAD "} ${name}`);
  console.log(`       expected ${want}${mustSay ? ` saying "${mustSay}"` : ""}, got ${got} :: ${r.detail ?? "(no detail)"}`);
}
console.log(`\n${cases.length - bad}/${cases.length} harness cases behaved as specified`);
process.exit(bad ? 1 : 0);
