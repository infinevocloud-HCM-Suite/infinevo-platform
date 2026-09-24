---
name: develop
description: Build an assigned ticket end to end on the developer's own branch - writes the code, runs the checks itself, fixes what it finds, and pushes. Refuses to start without a spec and a tracker row assigning it to you.
---

# develop

Invoke as `/develop W-nn`. Works the same for a feature ticket and a platform ticket
(Bicep, Docker, CI, Flyway) — only the checks in step 2 differ.

**One pass. Checks run inside the build and defects are fixed on the spot.** There are no
rounds, no finding numbers and no report files. A defect found is a defect fixed in the
commit that caused it.

---

## Before anything

1. Read `.claude/work/active-work.md`.
2. Find the spec: `docs/target-state/features/W-nn-<slug>.md`. **If it does not exist,
   stop** and say so.
3. Find the ticket's row in `docs/trackers/`. **If it is not assigned to you, or
   you already have another row In flight, stop.** One feature at a time per developer.

## Step 1 — build

1. Read the spec in full, then `docs/CONVENTIONS.md`.
2. Work on your own branch `dev-<name>`, created from `main` once and reused for every
   feature. Before starting, bring it level with `main`: `git fetch && git rebase origin/main`.
   Set the tracker row to `In flight — dev-<name>` and commit it.
3. **One module at a time.** Spawn **implementer** confined to `code/backend/<module>`,
   `code/frontend/src/<area>`, `infra/` or `.github/workflows/`. Finish one before
   spawning the next — never one agent across two.
4. Porting from the frozen system: read it under `legacy/` and **cite its `file:line` in
   the commit message**, so the merge review can confirm it was carried over rather than
   reinvented.
5. Tests as you go.

## Step 2 — check it, and fix what you find

Run the checks for what the ticket touched. **Fix each failure immediately, in the commit
that caused it.** Do not write it down first.

| Ticket touches | Run |
|---|---|
| `code/backend/` | `./mvnw -B clean verify` from `code/backend` (Spotless is bound to `validate`) |
| `code/frontend/` | `npm run lint && npm run build` from `code/frontend` |
| `infra/azure/` | `az bicep build` and `az bicep lint` on each changed template |
| `infra/docker/` | `docker compose config` |
| anything | `git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/` — expect nothing |
| anything | `git diff --name-only origin/main...HEAD \| grep -E '^legacy/'` — expect nothing |

**Triage before you reason.** Save the failing output to a file and run
`jev-harness test-gate --log <file> --json`. Exit 0 means `skip_llm` — the failure is
mechanical: a missing package, a port already in use, a flaky download. Retry or fix it
directly and move on. Exit 1 means a real defect; only those are worth reading and
reasoning about.

**Before repeating yourself, ask.** Before trying the same fix a second time, run
`jev-harness abort-check --plan "<what you are about to do>" --history "<what already
failed>"`. If it says stop, stop and tell the founder. Do not go round again.

The gate only decides **who** fixes a failure — you or the model. It decides nothing
about whether the code is correct; every rule below stays exactly as it is.

**Break each guard the ticket ships, once.** A check that has never failed has not been
shown to work.

### What this skill may not do without asking

**Never create, modify or delete a live or billable resource** — a cloud deployment, a
resource group, a registry push, a DNS record, a production database. Read-only cloud
calls are fine: `az account show`, `az group list`, `az bicep build`, `az bicep lint`,
`az deployment ... what-if`.

Deploying the Bicep is the founder's step, not a check this skill runs. `az bicep build`
plus lint plus CI is what proves the template here.

## Step 3 — push

Commit in meaningful steps and push `dev-<name>`. **Never push to `main`** — the founder
merges, after `/merge` has run the gates and the one independent read.

---

## The rules the build enforces

These are checked by `maven-enforcer`, CI and the `guard-edit` hook. They are listed
here so you know what will reject you, not as prose to comply with by hand.

| Rule | Enforced by |
|---|---|
| `tenant_id` and an RLS policy on every new table outside `reference` | CI, `check-done.mjs` |
| Flyway script for every schema change; `ddl-auto` set nowhere | CI, `check-done.mjs` |
| `Money` or `BigDecimal` for money, never `double` or `float` | CI, `check-done.mjs` |
| No module references another module — only `core` | `maven-enforcer` |
| Nothing under `legacy/`, `*.properties`, `.env*` | `guard-edit` hook |

If the design seems to need a cross-module dependency, **the data belongs in `core`**.
Say so and stop — the build rejects the workaround anyway.

---

## Finishing

Reply with this and nothing longer.

```
W-nn — <title>: built and pushed

| File | New or changed | Why |
|---|---|---|
| code/backend/hrms/.../LeaveService.java | new | Works out how many leave days are left |
| code/backend/migration/V12__leave_balance.sql | new | The table that stores it |

Checked: <what ran> — all green. Fixed along the way: <n> things, in plain English
(<n> of them handled by the triage gate, never sent to the model).
Next: /merge W-nn
```

If something is genuinely blocked, say what and why in one sentence — do not start
another pass around it.
