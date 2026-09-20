# GEMINI.md — Repository Review & Specification Harness for Gemini

This document defines the mandatory review discipline, verification standards, and gate-keeping rules that Gemini must strictly follow when operating in `infinevo-platform`.

---

## 1. Source of Truth & Core Directives

1. **Read `CLAUDE.md` First**: Always read `CLAUDE.md` and treat the `.claude/` directory (`.claude/skills/`, `.claude/scripts/`, `.claude/work/active-work.md`) as the ultimate source of truth for repository structure, rules, and process workflows.
2. **Never Approve Based on Reading Alone**: Reading a document is never proof of correctness. Empirical runtime verification and codebase cross-referencing are mandatory before approving any specification or PR.
3. **Evidence Beats Assertions**: If physical evidence (code, git history, database schema, command exit code) contradicts the specification:
   - **Trust the evidence.**
   - **Reject the specification.**
4. **Prohibited Speculation**: Never use unevidenced phrases such as:
   - *"looks correct"*
   - *"appears correct"*
   - *"seems fine"*
   - *"likely works"*
   Every assertion must be backed by an empirical check, exact file citation, or command execution log.

---

## 2. Specification Pre-Approval Verification Protocol

Before approving any specification (`docs/target-state/features/W-nn-<slug>.md` or draft plans), Gemini **MUST** perform and log the following checks:

1. **Verify Every Citation**: Confirm that every cited document, decision (`D-xx`), gap (`BUG-xxx`, `DEBT-xxx`), and legacy reference exists.
2. **Verify Every File Path Exists**: Inspect disk using file viewing or search tools to prove every mentioned file exists at the exact specified path.
3. **Verify Every Line Reference Exists**: Read target files to ensure cited line numbers contain the exact code logic described.
4. **Verify Verification Commands Can Execute**: Test commands (e.g. `(cd code/backend && ./mvnw clean verify)`) must be syntactically valid, executable in Bash/shell, and target the correct test harness (e.g., Failsafe for `*IT.java` integration tests).
5. **Verify Break Tests Genuinely Fail**: Ensure tests designed to verify security, RLS isolation, or invalid inputs actually attempt the operation and fail when broken (e.g., real HTTP `MockMvc` requests asserting 401/403, rather than mocking tokens in unit tests).
6. **Verify Done-When Items Are Measurable**: Every acceptance criterion must be mechanically verifiable via terminal commands or automated test suites.
7. **Verify Baseline Against `main` Branch**: Check `git log` and `git diff` against `main` to verify that baseline statements reflect actual committed code, not assumed or unmeasured environments.
8. **Verify Migration Chronology**: For every database table queried or referenced in §2/§4/§7, verify that the table either:
   - Shipped in an already-merged Flyway migration (e.g. `V001__tenant.sql`), **OR**
   - Is explicitly created in §6 Database changes with its own `V00n__` Flyway migration script including RLS policies (`D-56`).

---

## 3. Mandatory Review Checklist

Before issuing an approval verdict on any specification, Gemini **MUST** execute and record the following 8-point checklist:

- **[A] Baseline verification**: Verify claimed baseline against current `main` branch commit shas and files.
- **[B] Citation verification**: Verify all `file:line`, `D-xx` ADRs, and gap inventory IDs resolve.
- **[C] Verification-command validation**: Confirm commands run under shell with valid flags and expected outputs.
- **[D] Break-test validation**: Prove security/isolation break tests fail closed (exercised, not asserted in prose).
- **[E] Done-when validation**: Confirm all "done when" criteria are mechanically testable.
- **[F] Cross-reference validation**: Cross-reference schemas against `docs/target-state/02-data-model.md` and code placements against `03-code-structure.md`.
- **[G] CI-gate validation**: Confirm changes meet CI workflow requirements (`.github/workflows/ci.yml`).
- **[H] Founder-decision validation**: Ensure strict alignment with founder decisions in `docs/target-state/07-decisions.md`.

---

## 4. Standard Finding Format

When findings are identified during a spec or code review, report them in the following standard format:

```markdown
F-1 High
- **Problem**: <Clear description of the defect or contradiction>
- **Evidence**: <Exact file:line, git status, database schema, or command output proving the issue>
- **Why it matters**: <The failure mode or security trap this causes if shipped to production>
- **Exact fix**: <The precise change required in the spec or code to resolve it>
```

Valid finding severities are:
- `High` (Blocker — ships broken, causes security/isolation bypass, or creates unexecutable runtime states)
- `Medium` (Blocker — missing prerequisite, unexecutable command, or scope contradiction)
- `Low` (Ungraded note / non-blocking recommendation)

---

## 5. Strict Gate Approval Criteria

Gemini is **ONLY** permitted to issue an **APPROVED** verdict when **ALL** of the following conditions are met:

- [ ] **0 High findings**
- [ ] **0 Medium findings**
- [ ] **100% of citations empirically verified** against the repository
- [ ] **100% of verification commands verified** as executable and falsifiable
- [ ] **100% of break tests verified** to exercise real security/isolation boundaries
- [ ] **100% of database tables verified** to exist via Flyway migrations or current spec

If any condition fails, Gemini **MUST REJECT** the specification with `NOT READY`, list the exact findings using the format above, and provide exact fixes.
