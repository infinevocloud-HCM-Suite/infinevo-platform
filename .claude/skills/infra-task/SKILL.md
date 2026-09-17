---
name: infra-task
description: Plan (and after approval, execute) a platform ticket — pipeline, containers, Azure, Postgres, Flyway, tenancy scaffolding, security tooling. Anything whose "flow" is an environment rather than a screen.
---

# infra-task

Same discipline as `plan-feature`, for tickets labelled `skill-INFRA`, `skill-DATA` or
`skill-SEC`. **Two phases with a hard stop between them.**

Invoke as `/infra-task W-nn`.

## The target state, so the plan aims at the right thing

| | |
|---|---|
| Runtime | **Azure Container Apps** (`D-10`). Not AKS |
| Database | **One Postgres**, four schemas: `core` `hrms` `payroll` `reference` (`D-09`). Not MySQL |
| Isolation | `tenant_id` everywhere outside `reference`, enforced by **row-level security** |
| Migrations | **Flyway only.** `ddl-auto` must appear in no configuration file, ever |
| Region | India (`D-18`) |
| Scale to design for | 10 tenants × up to 100 employees (`D-19`). Do not over-engineer |
| Code from the frozen apps | **Ported deliberately, once.** No subtree, no sync (`D-17`) |
| Secrets | Key Vault. Never a value in a file — reference the name only |

Full detail: `docs/target-state/05-azure-architecture.md` and `04-runtime-containers.md`.
**For anything that creates a database object, `02-data-model.md` §1 outranks both** —
it is where the tenant rule is stated, and step 5 below is how it reaches the spec.

## Phase 1 — plan

1. Read `.claude/work/active-work.md` for where the project actually stands, then the
   ticket's own GitHub issue for its features and build detail.
2. Read `docs/target-state/09-build-order.md` §3 for this item: **what to build, how you
   know it is done, and the trap to avoid.** That entry is deliberately the level that
   survives — use it as the spine of the plan.
3. Read the relevant gap IDs in `legacy/docs/GAP_INVENTORY.md` — `BUG-004` and
   `DEBT-002` ddl-auto and no migration framework, `DEBT-003` no tests, `DEBT-018` no
   indexes, `DEBT-021` unlocked schedulers. The plan must fix, explicitly defer, or
   discount each. **The inventory is authoritative, not this list** — read the IDs there
   and use its wording, because a mislabelled ID gets dispositioned against the wrong
   defect.
4. Spawn **explorer** if current state is unclear. Evidence to
   `.claude/outputs/<date>-infra-<slug>-evidence.md`.
5. **State the impact on the standing rules, every time, even to say "creates no
   table".** These are what `/review-spec` check 3 measures the draft against:
   - `tenant_id` on **every** table the ticket creates in `core`, `hrms` or `payroll`,
     plus a row-level security policy. `02-data-model.md:15` says "No exceptions" and
     `:16` makes an unscoped table outside `reference` a bug by definition. This binds
     infrastructure tables too — a history table, a lock table, an audit table. If the
     ticket genuinely needs one that cannot carry `tenant_id`, **say where it lives and
     name it as an input to `W-07`'s build check**; do not leave it to be discovered
     when that check goes red
   - **Which schema a table belongs in is already decided. Look it up, do not choose.**
     `02-data-model.md` §2-§5 lists all 130 target tables by schema — `core` 46,
     `hrms` 11, `payroll` 58, `reference` 15 — with a note on where each came from.
     Before naming any table, find it there: it tells you the schema, the target name,
     and whether what you are about to "add" already exists under a different name.
     If the table genuinely is not listed, say so in the spec and treat it as a design
     change the founder must approve, not a detail to settle in a migration script.
     **`legacy/docs/DB_SCHEMA.md` is the frozen MySQL schema** — consult it only for a
     table being *ported*, to get the source columns right, never to decide where a new
     table lives
   - Flyway script for every schema change, under `code/backend/migration/`.
     **Never `ddl-auto`** (`D-46` — the property is never set, not even to `validate`)
   - **Writing migration scripts? Read `code/backend/migration/README.md` first** — it
     holds the conventions (from `W-06`). The one that bites: every statement names its
     schema (`CREATE TABLE core.employee`). Unqualified DDL does not fail, it lands in
     the wrong schema and `W-07`'s tenant check never sees it
   - `Money` or `BigDecimal` with explicit precision and scale. Never floating point
   - Index on `tenant_id` plus lookup columns (`DEBT-018`, `02-data-model.md:363-372`)
   - Expand / contract sequencing — no destructive step, and the previous release must
     still run against the new schema (`05-azure-architecture.md:133`)
   - Nothing under `legacy/` or `docs/` is edited
6. Write the spec from `docs/target-state/features/TEMPLATE-INFRA.md` into
   `.claude/outputs/<date>-plan-<slug>.md` — **not** into `docs/`, which `guard-edit`
   blocks. It moves to `docs/target-state/features/W-nn-<slug>.md` once approved.
   That template already drops Flow, Frontend changes and the API contract, so there is
   nothing to mark "not applicable". Two things it does not drop:
   - **Add a Database changes section** — from `TEMPLATE.md` §6 — whenever the ticket
     creates *any* database object, with the `V__` script names filled in. A `skill-DATA`
     ticket almost always does
   - **Cite `file:line` for every claim about the current state**, and for every rule
     you are binding the work to. `/review-spec` check 1 verifies each one resolves
   What matters most:
   - **Verification**: exact commands the **verifier** can run, with expected output
   - **Rollback**: what to do if it goes wrong, given nothing is in production yet
   - **Done when**: a numbered list, each item checkable
7. **Split the work into implementer tasks now, one area per task** — `code/backend/<module>`,
   `code/frontend`, `infra/`, or `.github/workflows/`. Phase 2 spawns one **implementer**
   per task and it cannot cross areas, so a spec whose file list spans four of them
   without saying which task owns what is not buildable as written. Note where a task
   needs a module to gain a dependency before it can host its own tests.
8. **Open every file you are changing, before you describe the change.** For each row in
   the spec's file table, read the file and quote the line you are extending or
   replacing. Writing "same image", "same pattern" or "as the existing service does"
   without opening the thing you are matching is how a spec acquires a change that
   cannot work: `W-06` revision 2 specified a `migrate` compose service as "same dev
   image" when that image's `CMD` is hardwired to `-pl app`, so the service would have
   started a second copy of `app` and the stack would have hung.
9. **Write down every dependency edge the work adds, both directions, and read them
   together.** One line each — `migration -> shared (test-jar)`, `shared -> migration
   (scripts)`. A cycle is invisible while the two edges sit in different sections of the
   spec and obvious the moment they are on adjacent lines. Maven will not build one.
   Check whether an existing `<testResource>` mapping already solves it without an edge
   at all — `shared/pom.xml:55-61` is the precedent.
10. **Re-read what you wrote, twice, before handing it over:**
    - Of every acceptance criterion, ask **what could pass while the feature is broken.**
      A query returning no rows satisfies "every row is correct". `W-02` passed 23 of 23
      while Keycloak admin login returned 401. A criterion containing a `<placeholder>`
      cannot be run by the **verifier**, which has only Bash
    - **Spawn explorer to check the citations — do not check them yourself.** Lift every
      `file:line` out of the draft, hand it the full list, and ask for each: does the
      file exist, does the line exist, what does it hold. Evidence to
      `.claude/outputs/<date>-plan-<slug>-citations.md`. Self-checking does not work
      here: you verify the ones you doubt and skip the ones you are sure of, and the
      ones you are sure of are where the drift is. A row moves down a table, the number
      does not, and the citation still resolves to a real line about something else
11. **Run `/review-spec <draft path>` and fix what it finds, before the founder sees the
    draft.** It spawns its own reader and applies a different checklist, so it finds what
    step 10 cannot: on `W-06` it returned 6 High on the first draft and 3 on the second,
    none of them cosmetic. Skip it only for a genuinely small ticket — one area, one or
    two files, no new database object — and **say in step 12 whether you ran it and what
    it found.** `/review-spec` never edits the spec; it hands back findings for this
    skill to fix. Running it is not approval and does not substitute for it.
12. Reply with the spec path, a summary of 10 lines or fewer, the task list, whether
    `/review-spec` ran, and any decisions the founder must make, as numbered questions.
    **STOP and wait for approval.**

> The guard hook blocks writes to `docs/`. Write the spec to `.claude/outputs/` first and
> copy it across once approved, or use `sync-docs`.

## Phase 2 — execute, only after approval

13. Spawn one **implementer** per task from step 7, confined to that task's single area.
14. Spawn **verifier** with the verification commands from the spec. Attach its report
    verbatim; do not summarise away a failure.
15. Update the spec to match what was actually built, then open a pull request saying
    `Closes #<issue>` with the real command output in the body.
16. Reply with the summary. **STOP.**

## Standing constraints

- Nothing under `legacy/` is edited, ever. It is frozen and `guard-edit` blocks it
- No secret value in any file. Key Vault reference or environment variable name only
- Every schema change is a Flyway script under `code/backend/migration/`
- `main` cannot be branch-protected on the Free plan (`D-43`) — the pipeline reports, it
  does not block. Do not write a plan that assumes a required check
