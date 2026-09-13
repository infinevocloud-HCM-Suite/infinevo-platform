---
name: develop
description: Build an approved ticket, or fix the findings a verify/review raised on it. Refuses to start without an approved spec. One module at a time, tests as it goes.
---

# develop

Invoke as `/develop W-nn`.

**The only skill that writes application code.** It has two modes and picks the right
one itself:

| Mode | When | What it does |
|---|---|---|
| **Build** | No open findings | Implements the approved spec |
| **Fix** | `/verify` or `/review` left findings OPEN | Works those findings, and nothing else |

---

## Before anything — the gate

1. Find the spec: `docs/target-state/features/W-nn-<slug>.md`.
2. **If it does not exist, stop.** Say so and point at `/plan-feature` or `/infra-task`.
3. **If its status is not Approved, stop.** Hard rule 1: no code before founder approval.
   Print the status and wait.

Never write code to "get started while approval comes through".

---

## Mode: fix

Check first, because a build on top of unfixed findings wastes the fixing.

Read every `.claude/outputs/*verify-W-nn*.md` and `*review-pr-*.md` for this ticket.
Collect every finding whose status is `OPEN`.

If there are any:

1. **List them back to the founder** with your intended disposition for each:

   | Disposition | When |
   |---|---|
   | **FIXED** | A real defect inside this ticket's scope |
   | **DEFERRED** | Real, but belongs to another ticket. **Create a GitHub issue and link it.** Never just drop it |
   | **DISPUTED** | You believe the finding is wrong. **Stop and ask.** You do not overrule the checker alone |

2. Fix them one at a time. **Reference the finding ID in the commit message** —
   `Fix F-1: Keycloak admin variables are 26-only, silently ignored by 25`.
3. Add a test that would have caught it, wherever a test can.
4. Mark each finding `FIXED` in its report, with the commit hash.
5. Stop and tell the founder to run `/verify W-nn`.

**A finding that reappears after being marked FIXED is escalated to High.** A wrong fix
is worse than the original defect, because it consumed a round of everyone's attention.

**Three rounds maximum.** If the same ticket enters fix mode a fourth time, stop and
escalate — something is wrong with the approach, not the code.

---

## Mode: build

1. Read the spec in full, then `docs/CONVENTIONS.md` and
   `docs/target-state/03-code-structure.md` §3.
2. Branch, if not already on one: `W-nn-<slug>`.
3. **One module at a time.** Spawn **implementer** confined to `code/backend/<module>`
   or `code/frontend/src/<area>`. If the spec needs a second, finish the first and
   spawn again — never one agent across two modules.
4. To port logic from the frozen system, read it under `legacy/` and **cite its
   `file:line` in the commit message**, so the reviewer can confirm it was carried
   over rather than reinvented.
5. Tests as you go, not afterwards. See `/test`.
6. Commit in meaningful steps and push the branch. Push daily even if unfinished — a
   branch on one laptop helps nobody.

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

## Finishing

Run `/test W-nn`, then `/verify W-nn`. Open the pull request only once verify is clean:

```bash
gh pr create --fill --title "W-nn — <title>" --body "Closes #<issue>
<paste the real verification output>"
```

**Never `git push` to main.** Code reaches main only through `/merge`, and
`guard-merge` refuses a direct push carrying `code/`.
