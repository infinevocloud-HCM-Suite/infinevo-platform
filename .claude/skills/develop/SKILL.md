---
name: develop
description: Build an approved ticket end to end - writes the code, runs test, verify and review itself, fixes what they find, and stops after three rounds. Refuses to start without an approved spec.
---

# develop

Invoke as `/develop W-nn`.

**The only skill that writes application code, and the only one the developer runs
after approval.** It builds, then checks its own work by calling `/test`, `/verify` and
`/review`, fixes what they report, and repeats until clean or until three rounds are
spent.

The dev types one command. Everything below happens inside it.

---

## Before anything — the gate

1. Read `.claude/work/active-work.md` (hard rule 2).
2. Find the spec: `docs/target-state/features/W-nn-<slug>.md`.
3. **If it does not exist, stop.** Say so and point at `/plan-feature` or `/infra-task`.
4. **If its status is not Approved, stop.** Hard rule 1: no code before founder approval.
   Print the status and wait.

Never write code to "get started while approval comes through".

---

## The loop

```
build  ->  /test  ->  /verify  ->  /review  ->  findings?
                                                  |
                                    no -> done, print the summary
                                    yes -> fix them, round += 1, back to /test
```

**Three rounds maximum.** Round 4 never starts. If findings survive three rounds, stop
and hand it to the founder with what was tried each time — something is wrong with the
approach, not the code.

### Round 1 — build

1. Read the spec in full, then `docs/CONVENTIONS.md` and
   `docs/target-state/03-code-structure.md` §3.
2. Branch, if not already on one: `W-nn-<slug>`.
3. **One module at a time.** Spawn **implementer** confined to `code/backend/<module>`
   or `code/frontend/src/<area>`. If the spec needs a second, finish the first and
   spawn again — never one agent across two modules.
4. To port logic from the frozen system, read it under `legacy/` and **cite its
   `file:line` in the commit message**, so the review can confirm it was carried over
   rather than reinvented.
5. Tests as you go, not afterwards.
6. Commit in meaningful steps and push the branch.

### Then — check the work

Run all three, in this order, and read every report:

| | Asks | Writes |
|---|---|---|
| `/test W-nn` | Is what changed covered? | tests under the module |
| `/verify W-nn` | Does it work, when run? | `.claude/outputs/<date>-verify-W-nn.md` |
| `/review W-nn` | Is it right, when read? | `.claude/outputs/<date>-review-W-nn.md` |

**The checkers never fix.** `/verify` and `/review` run through the **verifier** and
**reviewer** agents, which have no edit tools at all — so this is enforced by their
toolset, not by good intentions. They report; this skill fixes. That separation is why
`W-02` was caught reporting 23 of 23 passing while its documented Keycloak admin login
returned 401: reporting it produced three extra checks and a recorded trap, and a quiet
fix would have produced neither.

### Then — fix what they found

Collect every finding whose status is `OPEN`. For each, decide:

| Disposition | When |
|---|---|
| **FIXED** | A real defect inside this ticket's scope |
| **DEFERRED** | Real, but belongs to another ticket. **Create a GitHub ticket and link it.** Never just drop it |
| **DISPUTED** | You believe the finding is wrong. **Stop and ask the founder.** You do not overrule a checker alone |

Then:

1. Fix them one at a time. **Reference the finding ID in the commit message** —
   `Fix F-1: Keycloak admin variables are 26-only, silently ignored by 25`.
2. Add a test that would have caught it, wherever a test can.
3. Mark each finding `FIXED` in its report, with the commit hash.
4. Go back to `/test`. That is round 2.

**A finding that reappears after being marked FIXED is escalated to High.** A wrong fix
is worse than the original defect, because it consumed a round of everyone's attention.
A High finding still open blocks the merge, so a loop that ran out of rounds cannot ship
anyway.

---

## The rules the build cannot break

| | |
|---|---|
| `tenant_id` and a row-level security policy on every new table outside `reference` | |
| Flyway script for every schema change, under `code/backend/migration/`. **Never `ddl-auto`** | |
| `Money` or `BigDecimal` for money. Never `double` or `float` | |
| **No module references another module.** Only `core` | The build rejects it |
| Every new endpoint authenticated, or added to the reviewed exception list | |
| Nothing under `legacy/`, `docs/` (except this ticket's spec), `*.properties` or `.env*` | `guard-edit` blocks it |

If the design seems to need a cross-module dependency, **the data belongs in `core`**.
Say so and stop — do not reach for a workaround. `maven-enforcer` will refuse it anyway.

---

## Finishing — what the dev reads

When the loop ends clean, reply with this and nothing longer. Plain English, no jargon,
no report paste.

```
W-nn — <title>: done

| File | New or changed | Why |
|---|---|---|
| code/backend/hrms/.../LeaveService.java | new | Works out how many leave days are left |
| code/backend/migration/V12__leave_balance.sql | new | The table that stores it |
| code/frontend/src/hrms/LeaveForm.tsx | changed | Sends the new field the API now expects |

Checked: tests pass, build clean, review found <n> things and all are fixed.
Rounds used: <n> of 3.
Next: /merge W-nn
```

If the loop ran out of rounds, say that instead — what is still failing, in one plain
sentence each, and what was tried in each round.

**Never `git push` to main from here.** Code reaches main only through `/merge`.
