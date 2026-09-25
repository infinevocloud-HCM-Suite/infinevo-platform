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
| Docker Desktop | current | The local stack, **and the backend integration tests** — without a running daemon `./mvnw verify` still passes, but every test extending `AbstractIntegrationTest` is *skipped*, not run. CI has Docker; your laptop must too |
| `gh` (optional) | current | Tickets and CI runs from the terminal |

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

## 3. Getting a ticket

**Work is assigned, not claimed.** The founder writes the spec and puts your name on the
ticket's row in [`docs/trackers/`](docs/trackers/README.md). That row is the source of
truth for who owns a ticket and where it stands. GitHub carries no status of its own,
there are no labels to watch, and nothing to claim.

| Rule | What it means |
|---|---|
| **Yours** = your name in the Owner column | If the row does not name you, it is not yours. `/develop` refuses to start |
| **One in flight at a time** | You may hold several `Assigned` rows, but only one may be `In flight` |
| **One branch, reused** | `dev-<name>`, created from `main` once, rebased onto `main` before every ticket |
| **The row moves with the work** | `Assigned` → `In flight` → `Ready to merge` → `Done`. Each command sets the next value |
| **Only `/merge` writes `Done`** | Done means on `main`. Nothing else may say so |
| **Blocked rows wait** | `/plan-feature` will not spec a Blocked ticket. It says which ticket it waits on |

### Developer: the steps, assigned to merged

Each command ends by telling you the next, in plain English. Same steps for a feature
and for platform work.

| # | Step | Command | What comes back |
|---|---|---|---|
| 1 | **Find your row** | open `docs/trackers/` | A row with your name and status `Assigned`. Its spec is already in `docs/target-state/features/` |
| 2 | Branch | `git checkout dev-<name> && git rebase origin/main` | Your branch, level with `main` |
| 3 | Understand it | `/analyze W-nn` | The ticket in plain English: what it is, what it touches, the one thing that will bite |
| 4 | **Build** | `/develop W-nn` | Sets the row to `In flight — dev-<name>`, builds, runs its own checks, fixes what it finds, pushes the branch |
| 5 | **Hand over** | `/merge W-nn` | Five gates, one independent read, row set to `Ready to merge — dev-<name>`. Then the founder merges |
| 6 | Docs | `/sync-docs` | Only if the ticket made a document untrue |

There is no spec approval stop: the founder wrote the spec, so a spec on `main` is ready
to build. There is no pull request. The branch is squashed onto `main` by the founder,
and everything a pull request used to prove — what changed, that CI was green for it —
the done check proves from the branch itself.

**Checks run inside the build, and defects are fixed on the spot.** No finding numbers,
no report files, no rounds. Earlier this was three separate checking skills that could
only write reports, and a fourth that read them back and fixed — which is how W-08 spent
three rounds and still merged with 13 findings open, each round's fixes producing the
next round's findings.

**One independent read survives, at `/merge`.** It runs once, through an agent with no
edit tools. It is there because running things is not the same as reading them: on `W-02`
the smoke test reported 23 of 23 while the documented Keycloak admin login returned 401 —
the test hit the realm endpoint, which works whether or not the admin user exists.

Disagree with the spec? Say so before `/develop`, not inside it. Blocked or stuck for
good? Tell the founder; the row is theirs to reassign.

### Founder: the steps

The founder writes, assigns and merges. Developers build.

1. **Write the spec.** `/plan-feature W-nn <developer>` writes it into
   `docs/target-state/features/` and sets the tracker row to `Assigned` with that
   developer as Owner. Commit both together.
2. **Keep every developer holding an `Assigned` row.** The tracker is the queue. A
   developer with no `Assigned` row is idle.
3. **Merge.** Step 3 of `/merge` is yours: squash the `dev-<name>` branch onto `main`,
   push, set the row to `Done` with the merge commit, set every row it unblocks to
   `Ready`, and refresh `.claude/work/active-work.md`.
4. **Reprioritise in the tracker, not in chat.** Reassign a row or move a ticket ahead
   by editing the file. If an `In flight` ticket must stop, tell the developer and reset
   the row.

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

**Five gates, checked by a script rather than by memory.** `/merge` runs it for you on
your `dev-<name>` branch; run it yourself any time to see where the ticket stands.

```bash
node .claude/scripts/check-done.mjs W-nn    # the ticket is the argument, not the branch name
node .claude/scripts/check-done.mjs         # harness or tooling work with no ticket
```

| | Gate | Refuses when |
|---|---|---|
| 1 | Spec exists | No `docs/target-state/features/W-nn-*.md` |
| 2 | `legacy/` untouched | The diff changes a frozen file |
| 3 | `ddl-auto` set nowhere | A real setting, not a comment |
| 4 | No floating-point money | `double` or `float` on an amount, salary, pay, tax or deduction field |
| 5 | CI green for this commit | No `ci.yml` run for `HEAD`, still running, or not `success` |

**The build and the tests are gate 5, not gates of their own.** CI runs backend `verify`,
frontend lint and build, and the static checks, on this exact commit; gate 5 refuses
unless that run went green. Running them again locally on the same bytes answers a
question already answered and costs ten minutes each time. A commit that touches only
`docs/`, `.claude/`, `legacy/` or `*.md` does not trigger CI, and the gate knows that.

**Then one independent read.** `/merge` spawns the reviewer agent, which has no edit
tools, on the diff against `main` and the spec. A real defect is fixed on the branch and
the gates run again. Only after that does the founder squash the branch onto `main` and
set the tracker row to `Done`.

GitHub cannot enforce branch protection on a private repository on the Free plan
(`D-43`), so this runs on your machine instead. **Do not work around a failing gate.**
It is telling you the ticket is not finished. If a gate is itself wrong, fix
`check-done.mjs` in the open with a reason — a gate people have learned to step around
protects nothing.

Two things the script cannot check, still yours: indexes for the queries you introduced,
and the spec updated to match what you actually built. (`tenant_id` and an RLS policy on
every new table are checked, by CI, so they fall under gate 5.)

That second one matters more than it looks. A spec that drifts from the code is worse
than no spec, because the next person trusts it.

---

## 6. If you use Claude Code

The harness in `.claude/` comes with the clone and works immediately.

| Piece | Does |
|---|---|
| `guard-edit` hook | Blocks edits to `docs/` and config files during feature work |
| `verify-app` hook | Compiles or lints the affected app after every edit |
| `plan-feature` skill | Drafts the spec, and stops before writing code |
| `explorer` agent | Answers "where is this" with cited evidence instead of guessing |
| `implementer` agent | Confined to one app folder, required to add tests |
| `reviewer` agent | Reads the branch diff against the spec. Has no edit tools, so findings cannot become quiet fixes |
| `verifier` agent | Runs builds and tests independently. Has no edit tools, so it cannot quietly fix what it finds |

Its value is not speed. It is that the rules in §4 are enforced by tooling rather
than by memory.
