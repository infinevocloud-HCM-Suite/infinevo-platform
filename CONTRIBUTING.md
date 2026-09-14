# Contributing

How to set up, what to read, and how a ticket gets from assigned to merged.

This is the front door. The full version of the process is
[`docs/target-state/11-ways-of-working.md`](docs/target-state/11-ways-of-working.md).

---

## 1. Set up

### Clone the platform

```bash
git clone https://github.com/<org>/infinevo-platform.git
cd infinevo-platform
```

### The legacy applications are already here

You do **not** clone anything else. The four production applications this
platform replaces are frozen snapshots in [`legacy/`](legacy/), committed to this
repository:

```
legacy/HRMS_Backend/         legacy/Payroll-Bend-SBoot/
legacy/HRMS_Frontend/        legacy/Payroll-Fend-react/
```

> **Read them, never edit them.** They are a copy. A change under `legacy/` is not
> deployed anywhere and will be deleted. You also never push to the original repos
> — they are live production, and that is hard rule 5.

Read logic out of them, port it into the new modules, and cite the `file:line` in
your spec. [`legacy/README.md`](legacy/README.md) records which branch and commit
each snapshot came from — note the two Payroll apps are **not** on `main`.

### Install

| Tool | Version | For |
|---|---|---|
| JDK | **21** | Both backends |
| Maven | **3.9+** | Backend builds |
| Node.js | **24 LTS** | Frontends and the harness hooks |
| Docker Desktop | current | The local stack |
| `gh` (optional) | current | Issues and pull requests from the terminal |

Verify: `java -version` · `mvn -v` · `node -v` · `docker ps`

### On Windows, after pulling `W-03`: run this once

```bash
git add --renormalize .
```

`W-03` added `*.java text eol=lf` to `.gitattributes`, because Spotless resolves line
endings through `GIT_ATTRIBUTES` and the dev image copies `code/backend/` without a
`.git` directory to resolve them against.

**If you had a checkout before that landed, `./mvnw verify` will fail on files you never
touched** — Spotless reports format violations across the tree while `git status` shows
it clean, because the CRLF→LF clean filter still matches the index even though the files
on disk are still CRLF. There is no message pointing at the cause; it looks like the
build broke on its own.

`git add --renormalize .` fixes it. A fresh clone is never affected, and neither is
Linux, macOS, or CI.

---

## 2. Read before you write

About an hour, in this order. Nobody writes code before this.

| # | Read | Gives you |
|---|---|---|
| 1 | [`docs/target-state/README.md`](docs/target-state/README.md) | Where we are going, and the map of everything else |
| 2 | [`docs/target-state/01-platform-shape.md`](docs/target-state/01-platform-shape.md) | Core vs HRMS vs Payroll, and what a customer gets |
| 3 | [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) | Money handling, naming hazards, the load-bearing typos |
| 4 | [`CLAUDE.md`](CLAUDE.md) | The seven hard rules |
| 5 | Your ticket in [`docs/target-state/09-build-order.md`](docs/target-state/09-build-order.md) §3 | What to build, how you know it is done, the trap |

Look the rest up when you hit the question:

| Question | Document |
|---|---|
| Which table does this go in? | `02-data-model.md` |
| Where does this class live? | `03-code-structure.md` |
| Why is it built this way? | `07-decisions.md` |
| What runs where? | `04-runtime-containers.md`, `05-azure-architecture.md` |
| How does the old system do this? | `legacy/docs/FEATURE_MAP.md`, then the legacy clone |

---

## 3. The loop

**One ticket at a time.** Two open tickets finish neither.

| # | Step | Command | Note |
|---|---|---|---|
| 0 | Understand it | `/analyze <question>` | Optional. Cited evidence, changes nothing |
| 1 | **Write the spec** | `/plan-feature W-nn` (product)<br>`/infra-task W-nn` (platform) | Stops before any code |
| 2 | **Get it approved** | — | **The gate.** No code before it |
| 3 | Build | `/develop W-nn` | Refuses without an approved spec. One module at a time |
| 4 | Test | `/test W-nn` | Tests for what changed. Never changes code to make a test pass |
| 5 | Verify | `/verify W-nn` | Runs everything. **Fixes nothing** - failures become findings |
| 6 | Review | `/review <pr>` | Reads against the spec. **Fixes nothing** |
| 7 | **Merge** | `/merge <pr>` | Nine gates. **Refuses if any fails** |
| 8 | Docs | `/sync-docs` | Only if the build diverged from the documents |

Which skill at step 1 is decided by the issue's label: `skill-INFRA`, `skill-DATA` and
`skill-SEC` use `/infra-task`; `skill-BE` and `skill-FE` use `/plan-feature`.

**Findings loop back.** `/verify` and `/review` write numbered findings; `/develop`
picks up the OPEN ones, fixes each referencing its ID, and you verify again. A **High**
finding blocks the merge until it is closed. Three rounds maximum, then it escalates.

**Why the checkers cannot fix.** A checker that can fix has a reason to make things pass
rather than tell you the truth, and a silent fix is unreviewed code reaching `main`
through the one path with no gate. On `W-02` the smoke test reported 23 of 23 while the
documented Keycloak admin login returned 401 - reporting that produced three extra
checks and a recorded trap; fixing it quietly would have produced neither.

Disagreements about approach belong at step 2, not step 6.

---

## 4. The rules that are never negotiable

| # | Rule |
|---|---|
| 1 | Tests for everything you change |
| 2 | `tenant_id` and a row-level security policy on every new table, unless it is in the `reference` schema |
| 3 | Every new endpoint authenticated, or added to the reviewed exception list |
| 4 | Flyway script for every schema change. **Never `ddl-auto`** |
| 5 | `BigDecimal` for money. Never a floating-point type |
| 6 | No module references another module. Only `core` |
| 7 | Never edit `docs/` or `*.properties` during feature work |

Rules 2, 3 and 6 become build failures once `W-07`, `W-57` and `W-01` land. Until
then they are checked by review — and they are the three most likely to slip.

Rule 7 is enforced by the `guard-edit` hook if you use Claude Code.

---

## 5. Done means

**Nine gates, checked by a script rather than by memory:**

```bash
node .claude/scripts/check-done.mjs <pr>
```

| | |
|---|---|
| 1 | The pull request is open and says `Closes #<issue>` |
| 2 | An **approved** spec exists for this `W-nn` |
| 3 | **No High finding is still OPEN** |
| 4 | Nothing under `legacy/` was touched |
| 5 | `docs/` changed only for this ticket's own spec |
| 6 | `ddl-auto` is set nowhere |
| 7 | No `double` or `float` on a money field |
| 8 | Backend builds, tests pass |
| 9 | Frontend lints and builds |

A receipt is written **only if all nine pass**, and the merge command is refused without
a fresh receipt for the current commit. Commit again and it is void - deliberately,
since otherwise the check proves nothing about what is being merged.

GitHub cannot enforce branch protection on a private repository on the Free plan
(`D-43`), so this runs on your machine instead. **Do not work around a failing gate.**
It is telling you the ticket is not finished. If a gate is itself wrong, fix
`check-done.mjs` in the open with a reason - a gate people have learned to step around
protects nothing.

Two things the script cannot check, still yours: indexes for the queries you introduced,
and the spec updated to match what you actually built.

That second one matters more than it looks. A spec that drifts from the code is worse
than no spec, because the next person trusts it.

---

## 6. If you use Claude Code

The harness in `.claude/` comes with the clone and works immediately.

| Piece | Does |
|---|---|
| `guard-edit` hook | Blocks edits to `docs/` and config files during feature work |
| `verify-app` hook | Compiles or lints the affected app after every edit |
| `plan-feature` skill | Drafts the step-1 spec, and stops before writing code |
| `explorer` agent | Answers "where is this" with cited evidence instead of guessing |
| `implementer` agent | Confined to one app folder, required to add tests |
| `reviewer` agent | Reads the diff against the spec. Has no edit tools, so findings cannot become quiet fixes |
| `verifier` agent | Runs builds and tests independently. Has no edit tools, so it cannot quietly fix what it finds |

Its value is not speed. It is that the rules in §4 are enforced by tooling rather
than by memory.
