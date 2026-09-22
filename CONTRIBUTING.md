# Contributing

How to set up, what to read, and how a ticket gets from claimed to merged.

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

## 3. Claiming a ticket

**Work is pulled, not handed out.** Nobody waits to be assigned; nobody is assigned
ahead of time. GitHub is the queue and the lock, and `.github/workflows/tickets.yml`
enforces the rules below (#107).

| Rule | What it means |
|---|---|
| **Claimable** = `ready` label + no assignee | `gh issue list --label ready --search "no:assignee"` |
| **Claim** = assign yourself | `gh issue edit <n> --add-assignee @me`, then comment `claimed` |
| **One owner, ever** | A second assignee is reverted automatically. The earlier one wins |
| **WIP limit 2** | One in build, one waiting on approval. A third claim is reverted |
| **`next` first, your `skill-*` only** | The founder marks what should go first. Don't reach past it |
| **Blocked tickets free themselves** | When every `Blocked by` ticket closes, the label flips to `ready` |
| **3 working days idle = released** | No branch, commit or comment: the claim returns to the queue |

Ownership is the assignee field and nothing else. There are no `owner-*` labels.

### Developer: the steps, claim to merge

**One ticket in build at a time.** Six commands, in order. Each one ends by telling you
the next, in plain English.

| # | Step | Command | What comes back |
|---|---|---|---|
| 1 | **Claim it** | `gh issue edit <n> --add-assignee @me` | Comment `claimed`. If the bot reverts, read why and take the next |
| 2 | Branch | `git checkout -b W-nn-<slug> main` | The `W-nn` in the name is what tells the stale sweep you started |
| 3 | Understand it | `/analyze W-nn` | The ticket in plain English: what it is, what it touches, the one thing that will bite |
| 4 | **Write the spec** | `/plan-feature W-nn` (product)<br>`/infra-task W-nn` (platform) | What will be built and what could go wrong. It stops here |
| 5 | **Get it approved** | — | **The gate.** No code before it. `/review-spec` runs inside step 4 and names any gap as the trouble it would cause |
| 6 | **Build** | `/develop W-nn` | Builds, then runs `/test`, `/verify` and `/review` itself, fixes what they find, repeats. **Three rounds maximum.** Ends with the files it changed and why |
| 7 | **Merge** | `/merge W-nn` | Eight gates, then squashed onto main and pushed. One line: pushed successfully |
| 8 | Docs | `/sync-docs` | Always, after the merge. Names any document the change made untrue, or says nothing drifted |

There is no pull request. The branch is squashed onto `main` by `/merge`, and everything
a pull request used to prove — what changed, that CI was green for it — the done check
proves from the branch itself.

**The checkers never fix.** `/verify` and `/review` run through agents with no edit tools.
They report; `/develop` fixes and runs them again. A checker that can fix has a reason to
make things pass rather than tell you the truth, and a silent fix is unreviewed code
reaching `main` through the one path with no gate. On `W-02` the smoke test reported 23 of
23 while the documented Keycloak admin login returned 401 — reporting that produced three
extra checks and a recorded trap; fixing it quietly would have produced neither.

**Three rounds, then it stops.** If findings survive three attempts, `/develop` hands it
back rather than looping. A finding that reappears after being marked FIXED is escalated
to High, and a High finding blocks the merge — so a loop that ran out of rounds cannot
ship anyway.

Disagreements about approach belong at step 5, not step 6.

Blocked for more than a day? Say so in a ticket comment. Silence is what gets a claim
released. Stuck for good or reprioritised? Unassign yourself, comment why, and claim
the next.

### Approver: the steps

The founder never assigns a ticket. Developers claim; the founder steers the order.

1. **Keep `next` populated.** Three to five tickets, in the order they should go.
   `gh issue edit <n> --add-label next`. That is the only steering needed day to day.
2. **Approve specs within a day.** Step 5 of the developer list is the only place a
   developer waits on you, so it is the only place idle time can come from.
3. **Merge.** `/merge W-nn` is yours. `Closes #n` in the commit closes the ticket, and
   closing it is what releases the tickets behind it.
4. **Check the queue weekly.** `gh issue list --label ready --search "no:assignee"` should never
   be empty while a developer is free; `gh issue list --label blocked` shows what is
   coming. If `ready` runs dry, split or unblock something.
5. **Reprioritise with labels, not people.** Move `next` around. If a claimed ticket
   must stop, comment on it and the developer unassigns themself and claims the next.
6. **Never pre-assign.** Ownership starts when a developer claims. A `blocked` ticket
   cannot be claimed, and every ticket sits unassigned until then.

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

**Eight gates, checked by a script rather than by memory.** `/merge` runs it for you; run
it yourself any time to see where the ticket stands.

```bash
node .claude/scripts/check-done.mjs        # reads the branch you are on
```

| | |
|---|---|
| 1 | The branch says what it is: `W-nn-<slug>`, `docs-<slug>`, or plainly neither |
| 2 | An **approved** spec exists for this `W-nn` |
| 3 | **No High finding is still OPEN** |
| 4 | Nothing under `legacy/` was touched |
| 5 | `docs/` changed only by a recognised route — see below |
| 6 | `ddl-auto` is set nowhere |
| 7 | No `double` or `float` on a money field |
| 8 | CI is green for the exact commit being merged |

**Gate 5 is one rule, read off the branch name.** A `W-nn` branch may change its own
`features/W-nn-*` spec and no other document. A `docs-<slug>` branch may change `docs/`
but must not ship anything under `code/` or `infra/`. That is all of it. What it refuses
is a `docs/` edit riding along with a feature — a docs change travels on its own, read
for what it says rather than waved through with code.

**The build and the tests are gate 8, not gates of their own.** CI runs backend `verify`,
frontend lint and build, and the static checks, on this exact commit; gate 8 refuses
unless that run went green. Running them again locally on the same bytes answers a
question already answered and costs ten minutes each time.

A receipt is written **only if all eight pass**, and the push to `main` is refused
without one matching the exact content being pushed. Change anything afterwards and it is
void — deliberately, since otherwise the check proves nothing about what is being merged.
The receipt is matched on the content, not the commit, so the squash `/merge` performs
does not invalidate it.

GitHub cannot enforce branch protection on a private repository on the Free plan
(`D-43`), so this runs on your machine instead. **Do not work around a failing gate.**
It is telling you the ticket is not finished. If a gate is itself wrong, fix
`check-done.mjs` in the open with a reason — a gate people have learned to step around
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
| `plan-feature` skill | Drafts the spec, and stops before writing code |
| `explorer` agent | Answers "where is this" with cited evidence instead of guessing |
| `implementer` agent | Confined to one app folder, required to add tests |
| `reviewer` agent | Reads the branch diff against the spec. Has no edit tools, so findings cannot become quiet fixes |
| `verifier` agent | Runs builds and tests independently. Has no edit tools, so it cannot quietly fix what it finds |

Its value is not speed. It is that the rules in §4 are enforced by tooling rather
than by memory.
