// Harness for gate 5 of check-done.mjs. The gate body is string-sliced VERBATIM out of
// the shipped file and driven with stubbed prData + a virtual disk, so what is tested is
// what merges, not a copy that can drift.
import { readFileSync } from "node:fs";

const FILE = "D:/Infinevoclouds/.claude/scripts/check-done.mjs";
const src = readFileSync(FILE, "utf8");
const start = src.indexOf("function approvedDocsPaths");
const end = src.indexOf("// \u2500\u2500 5. ddl-auto");
if (start < 0 || end < 0 || end < start) throw new Error("could not slice gate 5 out of check-done.mjs");
const body = src.slice(start, end);
console.log(`sliced ${body.length} chars of gate 5 verbatim from check-done.mjs\n`);

const ROOT = "/repo";
const NL = String.fromCharCode(10);

function run({ branch, files, disk }) {
  let captured = null;
  const gate = (_name, fn) => { captured = fn; };
  const join = (...p) => p.join("/");
  const existsSync = (p) => Object.prototype.hasOwnProperty.call(disk, p);
  const read = (p) => { if (!existsSync(p)) throw new Error("ENOENT"); return disk[p]; };
  const prData = { headRefName: branch, files: files.map((path) => ({ path })) };
  new Function("join", "existsSync", "readFileSync", "NL", "ROOT", "prData", "gate", body)
    (join, existsSync, read, NL, ROOT, prData, gate);
  return captured();
}

const APPROVAL = `.claude/outputs/2026-09-14-docs-approval-template-infra.md`;
const approvalFile = (paths, approved = true) =>
  `# Docs change approval - TEMPLATE-INFRA - 2026-09-14\n\n` +
  `| Field | Value |\n|---|---|\n| **Patch** | \`.claude/outputs/2026-09-14-docs-diff-template-infra.patch\` |\n` +
  `| **Status** | ${approved ? "**Approved 2026-09-14** by the founder" : "Draft - awaiting approval"} |\n\n` +
  `## Paths covered\n\n` + paths.map((p) => "- `" + p + "`").join("\n") + "\n";

const T = "docs/target-state/features/TEMPLATE-INFRA.md";
const cases = [
  ["1 non-ticket + docs + approval listing exact path, approved", "PASS", {
    branch: "gap-100-docs-route", files: [T, APPROVAL, ".claude/scripts/check-done.mjs"],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile([T]) } }],
  ["2 non-ticket + docs + NO approval file", "FAIL", {
    branch: "gap-100-docs-route", files: [T], disk: {} }],
  ["3 non-ticket + docs + approval lists a DIFFERENT path", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile(["docs/CONVENTIONS.md"]) } }],
  ["4 non-ticket + docs + approval lists path but NOT approved", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile([T], false) } }],
  ["5 non-ticket + docs + approval on disk but NOT in PR diff", "FAIL", {
    branch: "gap-100-docs-route", files: [T],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile([T]) } }],
  ["6 W-nn PR + unrelated docs file + approval listing it", "FAIL", {
    branch: "W-04-tenant-context", files: ["docs/target-state/features/W-04-tenant.md", T, APPROVAL],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile([T]) } }],
  ["7 W-nn PR + only its own features/W-nn- spec", "PASS", {
    branch: "W-04-tenant-context", files: ["docs/target-state/features/W-04-tenant.md", "code/backend/core/pom.xml"],
    disk: {} }],
  ["8 non-ticket + no docs/ change at all", "PASS", {
    branch: "gap-100-docs-route", files: [".claude/scripts/check-done.mjs"], disk: {} }],
  ["9 (extra) non-ticket + approval bullet is a DIRECTORY prefix", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile(["docs/target-state/"]) } }],
  ["10 (extra) non-ticket + approved marker but NO paths listed", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL],
    disk: { [`${ROOT}/${APPROVAL}`]: approvalFile([]) } }],
  ["11 (extra) non-ticket + approval file unreadable", "FAIL", {
    branch: "gap-100-docs-route", files: [T, APPROVAL], disk: {} }],
];

let bad = 0;
for (const [name, want, input] of cases) {
  const r = run(input);
  const got = r.ok ? "PASS" : "FAIL";
  const okMark = got === want ? "ok  " : "BAD ";
  if (got !== want) bad++;
  console.log(`${okMark} ${name}\n       expected ${want}, got ${got} :: ${r.detail ?? "(no detail)"}`);
}
console.log(`\n${cases.length - bad}/${cases.length} harness cases behaved as specified`);
process.exit(bad ? 1 : 0);
