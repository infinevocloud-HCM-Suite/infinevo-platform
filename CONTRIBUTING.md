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
| Node.js | **20 LTS** | Frontends and the harness hooks |
| Docker Desktop | current | The local stack |
| `gh` (optional) | current | Issues and pull requests from the terminal |

Verify: `java -version` · `mvn -v` · `node -v` · `docker ps`

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

| # | Step | Note |
|---|---|---|
| 1 | **Write the spec first** | `docs/target-state/features/W-nn-<slug>.md`, from `docs/target-state/features/TEMPLATE.md`. Flows, API surface, schema changes with Flyway script names, tests, acceptance criteria, rollback |
| 2 | **Get it approved** | The only gate. No code before it. Half a page is often enough for a small item |
| 3 | **Branch** | One branch per ticket, named for the work item |
| 4 | **Build, with tests as you go** | Not afterwards |
| 5 | **Self-verify** | Compile, lint, tests — all green locally before you ask anyone to look |
| 6 | **Pull request** | Link the ticket and the spec. It is reviewed against the spec's acceptance criteria |
| 7 | **Merge** | Pipeline runs, deploys to dev. Close the ticket with evidence attached |

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

No ticket closes without all six.

1. Merged, pipeline green — compile, lint, tests
2. Tests written for what changed
3. New tables carry `tenant_id` and a policy, unless in `reference`
4. New endpoints authenticated, or on the exception list
5. Indexes added for the queries introduced
6. The spec updated to match what was actually built

Item 6 matters more than it looks. A spec that drifts from the code is worse than
no spec, because the next person trusts it.

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
| `verifier` agent | Runs builds and tests independently. Has no edit tools, so it cannot quietly fix what it finds |

Its value is not speed. It is that the rules in §4 are enforced by tooling rather
than by memory.
