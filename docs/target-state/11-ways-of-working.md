# 11 — Ways of Working

> What the founder assigns, how a developer works a ticket, and what "done" means.
> Written for a lead plus two to three part-time developers.

---

## 1. The unit of assignment

**You assign one ticket.** Not a work item, not a stream — one ticket from the 93 in `10`.

A ticket carries:

| Field | From |
|---|---|
| Work item id and title | `10` §3 |
| Features inside it | `08` |
| Build detail — what to make, done when, watch out | `09` §3 |
| Size and skill | `10` §3 |
| Blocked by | `10` §3 |
| Wave | `09` §2 |

**Never assign two tickets to one person at once.** Part-time developers with two open
tickets finish neither. The board should show one in-progress item per person.

---

## 2. The developer loop

Six steps. The same for every ticket, from the repository skeleton to the cutover, and
the same for a feature as for platform work.

**Two of them wait for you: you approve the spec, and you merge the branch.** Nothing
else stops.

### 1 — Read before writing
Day one for anyone new, in this order:
`docs/target-state/README.md` → `01-platform-shape.md` → `docs/CONVENTIONS.md` →
the `CLAUDE.md` of whatever they are touching → their ticket's entry in `09` §3.

About an hour. Nobody writes code before this.

### 2 — Spec first, code never first
The developer drafts a spec into `docs/target-state/features/W-nn-<slug>.md` from
`docs/target-state/features/TEMPLATE.md`, using the build detail in `09` §3 as the starting point.

It states: the flows, the API surface, the schema changes with their Flyway script names, the
tests to be written, the acceptance criteria, and the rollback.

> Half a page is often enough for an `S` item. A `L` item deserves two pages. **The spec is
> not documentation, it is the thing you approve.**

### 3 — Founder approval — the gate
**No code is written until you approve the spec.** This is hard rule 1, and it is the only
gate in the process. You are approving the approach, not the code.

Approval should take minutes. If it takes longer, the spec is too vague or the ticket is too
big — send it back rather than approving something you do not follow.

### 4 — Branch and build
One branch per ticket, named for the work item. The developer builds with tests as they go,
not afterwards.

The six standing rules, which are not negotiable per ticket:

| # | Rule |
|---|---|
| 1 | Tests for everything changed |
| 2 | `tenant_id` and a row-level security policy on every new table, unless it is in `reference` |
| 3 | Every new endpoint authenticated, or added to the reviewed exception list |
| 4 | Flyway script for every schema change. Never `ddl-auto` |
| 5 | `BigDecimal` for money. Never a floating-point type |
| 6 | No module may reference another module. Only Core |

Rules 2, 4, 5 and 6 are enforced now — by CI and the done check, and by `maven-enforcer`
for rule 6. Rule 3 waits on `W-57`; until then it is the one most likely to slip under
pressure, because nothing refuses it.

### 5 — Check it and fix it in the same pass
Compile, lint and tests run as part of the build, not after it, and **a defect found is
fixed in the commit that caused it.** No finding numbers, no report files, no rounds.

This replaced three separate checking steps that could only write reports and a fourth
that read them back and fixed. Each round's fixes produced the next round's findings:
`W-08` ran three rounds and still merged with 13 findings open.

### 6 — Merge, close
**There is no pull request.** The branch is squashed onto `main` locally and pushed, and
everything a pull request used to prove — what changed, that CI was green for it — the
done check in §3 proves from the branch itself.

Merge runs seven machine gates, then **one independent read** against the spec's
acceptance criteria — the only one in the process, by an agent with no edit tools. It is
there because running things is not the same as reading them: `W-02` passed 23 of 23
tests while the Keycloak admin login it documented returned 401.

The ticket closes when evidence is attached: the commands run and their output. Not when
someone says it works. Disagreements about approach belong at step 3, not here.

---

## 3. Definition of done

No ticket closes without all six.

| # | Requirement |
|---|---|
| 1 | Merged, pipeline green — compile, lint, tests |
| 2 | Tests written for what changed |
| 3 | New tables carry `tenant_id` and a policy, unless in `reference` |
| 4 | New endpoints authenticated, or on the exception list |
| 5 | Indexes added for the queries introduced |
| 6 | The spec updated to match what was actually built |

> Item 6 matters more than it looks. A spec that drifts from the code is worse than no spec,
> because the next person trusts it.

Items 1, 3 and 4 are machine-checked at merge. `.claude/scripts/check-done.mjs` is the
executable form of this table — seven gates, a receipt written only when every one passes
— and the `guard-merge` hook refuses the push without that receipt. **Do not work around
a failing gate.** A gate that is wrong gets fixed in the open, with a reason, never
stepped around.

---

## 4. What the founder does

Four things, and deliberately not more.

| What | When | Why it is yours |
|---|---|---|
| **Approve specs** | As they arrive | The gate before code. Catches wrong approaches before they cost a week |
| **Merge to `main`** | When a branch passes its gates | The second and last gate. Developers stay on side branches |
| **Unblock dependencies** | Weekly board review | Only you can decide what gets sequenced ahead of what |
| **Answer product questions** | On demand | Developers will hit things the design did not settle |

**What is not yours:** estimating, assigning within a wave, or reviewing code style. The
scoping sheet handles the first two and the pipeline handles the third.

---

## 5. Cadence

| Rhythm | What happens |
|---|---|
| **Per ticket** | Spec → your approval → build → your merge |
| **Weekly** | Board review: what closed, what is blocked, what opens next |
| **Per wave** | A wave closes; check what the next one unlocks and who is free |

Progress reads as items closed per wave, not as dates. Wave one has four items, wave six has
fourteen.

---

## 6. Working with Claude Code

If your developers use Claude Code, the harness already does part of this for them.

| Harness piece | What it does for a developer |
|---|---|
| `guard-edit` hook | Blocks edits to `docs/` and configuration files. They cannot accidentally change a doc mid-feature |
| `guard-merge` hook | Refuses a push to `main` carrying `code/` outside the merge skill |
| `verify-app` hook | Lints the frontend after an edit there. The backend compiles in the build, not after every keystroke |
| `plan-feature` skill | Drafts the step-2 spec from the current state, and **stops before writing code**. Covers platform tickets too |
| `develop` skill | Builds one approved ticket, runs its own checks, fixes what it finds, pushes the branch. One pass |
| `merge` skill | Seven gates, one independent read, then the squash onto `main`. The only route code takes there |
| `explorer` agent | Answers "where is this" with cited evidence, instead of guessing |
| `implementer` agent | Confined to one app folder, and required to add tests |
| `reviewer` agent | The one independent read, at merge. Has no edit tools, so it cannot quietly fix what it finds |

**Recommendation:** have developers use it. The value is not speed, it is that the rules in
§2 step 4 are enforced by the tooling rather than by memory. A part-time developer returning
after a week does not remember six rules; the hooks do.

---

## 7. The first week, concretely

| Person | Ticket | Then |
|---|---|---|
| **You (lead)** | `W-01` repository skeleton | Sets the module graph everyone inherits. Nobody else should write code first |
| **Dev-1** | `W-49` containerisation | Then `W-50` Azure. Longest lead time, no product dependency |
| **Dev-2** | `W-66` marketing website | Fully independent, needs no platform knowledge, visible progress |

**When `W-01` merges**, three open at once: `W-02` local stack, `W-03` pipeline, `W-04` test
foundation. Give `W-03` to whoever is free first — it is small, and every later ticket depends
on the gates being on.

**The first real test of the process** is `W-07`, the tenant model. It is large, it is the
highest-value work in the project, and its build check is what makes rule 2 self-enforcing. If
the process holds through that ticket, it will hold.

---

## Related

- What to assign: `10-scoping.md` · Build detail: `09-build-order.md`
- Rules and conventions: `docs/CONVENTIONS.md` · Hard rules: root `CLAUDE.md`
- Spec template: `docs/target-state/features/TEMPLATE.md`
