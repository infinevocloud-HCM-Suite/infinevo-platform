---
name: verify
description: Run the build, tests and checks independently and report evidence. Fixes nothing — every failure becomes a numbered finding for /develop.
---

# verify

Invoke as `/verify W-nn`.

Answers one question: **does it actually work?** By running things, not by reading them.

**This skill fixes nothing.** Not with an edit, not with `sed`, not by rerunning until
it passes. Every failure becomes a numbered finding. `/develop` fixes them.

> Why the separation is absolute: a checker that can fix has a reason to make things
> pass rather than tell you the truth. If "26 of 26" might mean "I quietly repaired
> four", the number stops being evidence. And a silent fix is unreviewed code reaching
> `main` through the one path with no gate.

The work runs through the **verifier** agent, which has no edit tools — so this is
enforced, not merely promised.

---

## Steps

1. Read the ticket's spec, particularly **its verification section** — it lists the exact
   commands and their expected output. That table is the test plan. **Check which
   template the ticket used before reading a section number**: verification is **§9** in
   `TEMPLATE.md` and **§5** in `TEMPLATE-INFRA.md`, where §9 is *Done when* instead. Read
   the wrong one on an infra ticket and the test plan becomes a checklist of outcomes
   with no commands in it.
2. **Get the branch's files in front of you.** `git worktree add --detach <dir> <branch>`
   is the clean way. On Windows it can fail with `Filename too long` inside `legacy/` —
   when it does, extract only what the branch changes:
   `git archive <branch> <paths> | tar -x -C <short dir>` (`/c/<name>`, not a deep temp
   path), and say in the report where the checks ran.
3. Spawn **verifier** with those commands, and with the authorisation limit below stated
   explicitly in its brief.
4. Run the standing checks below as well, whatever the spec says.
5. **Break each guard the ticket ships, once.** See "Check the checks".
6. Write the report. Hand it back to `/develop`, which is what fixes the findings.

## What verify may not do without asking

**Verify never creates, modifies or deletes a live or billable resource on the founder's
say-so alone** — a cloud deployment, a resource group, a registry push, a DNS record, a
production database. The founder authorises it **in the session where it runs**, and an
approval given for one run does not carry to the next.

Without that go-ahead those rows are `NOT CHECKED — requires creating billable
resources, not authorised for this run`. That is a complete, honest result, not a gap to
apologise for: `W-50`'s verification section deploys a Postgres Flexible Server, Redis,
Service Bus, a registry, a vault and four Container Apps, then deletes two resource
groups and does it twice more. Running that because a skill said "run the spec's
commands" spends real money on the founder's subscription without being asked.

Read-only cloud calls need no approval — `az account show`, `az group list`,
`az bicep build`, `az bicep lint`, `az deployment ... what-if`.

## Standing checks

Two are unconditional, because they cost a second and catch the things nobody re-reads:

```bash
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/        # expect nothing
git diff --name-only origin/main...HEAD | grep -E '^legacy/'  # expect nothing
```

Three depend on the ticket touching code at all:

```bash
git diff --name-only origin/main...HEAD -- code/   # the gate for the three below
cd code/backend  && ./mvnw -B clean verify         # build + tests
cd code/frontend && npm run lint && npm run build
cd code/backend  && ./mvnw -B dependency:tree | grep -E 'com.infinevo:(hrms|payroll)'
```

**If that first command prints nothing, skip the three and record the command and its
empty output in the report as the justification.** An infra-only branch re-running a full
Maven verify proves that `main` still equals `main`, at seven minutes a go — and `/merge`
builds `main` itself before it lands anything, so the coverage is not lost. A skip with
evidence is a result; a silent omission is not. Note the diff is **three-dot**: two-dot
against a branch behind `main` reports `main`'s own files as the branch's.

The dependency check: `hrms` must not appear under `payroll`, nor the reverse. The build
should fail first, but verify rather than assume.

If the ticket touches the local stack, also `infra/docker/smoke.sh`.

## Check the checks, not just the code

A check that passes because it matches nothing reports green forever. `smoke.sh` passed
its `ddl-auto` check on a comment for an hour.

So when the ticket ships a guard — a lint rule, a CI gate, a refusal, a privilege denial:

1. Copy the thing the guard protects; never edit the original.
2. Break the copy deliberately, in the way the spec's "Proving it" table names.
3. Run the guard and record that it failed, with the error output quoted.
4. Delete the copy.

A guard only ever observed passing has not been tested.

---

## The report

Write to `.claude/outputs/<date>-verify-W-nn.md`.

```
# Verify — W-nn — <date time>

## Commands
| Check | Command | Exit | Verdict | Evidence (last lines) |

## Spec acceptance — <the spec's verification section: §9 TEMPLATE.md, §5 TEMPLATE-INFRA.md>
| # | Check | Expected | Actual | PASS / FAIL / NOT CHECKED |

## Findings
| ID | Severity | Finding | Evidence | Status |
| F-1 | High | <one sentence> | <command output> | OPEN |

## Summary
<n> of <m> passed. <k> findings: <h> High, <m> Medium, <l> Low.
```

**Finding IDs and the OPEN status are load-bearing.** `/develop` reads them to know
what to fix, and `check-done.mjs` refuses a merge while any **High** finding is OPEN.
Get the format wrong and the gate silently stops working.

| Severity | Means | Effect |
|---|---|---|
| **High** | Wrong behaviour, a security hole, or a documented feature that does not work | **Blocks the merge** |
| **Medium** | A real defect with a workaround, or a missing test | Merge with a linked issue, founder's call |
| **Low** | Cosmetic, or dead configuration | Note it |

**"NOT CHECKED" is an honest verdict and belongs in the report.** A check you could not
run is information. Recording it as a pass is not.

---

## Finishing

- Findings → `/develop W-nn` fixes them, then `/verify W-nn` again
- Clean → `/review W-nn`, which asks the different question: *is it right?*

Verify running clean is not sufficient on its own. `W-02` passed 23 of 23 while its
Keycloak admin login returned 401 — verify ran what it was told to run, and review
found what nobody had thought to check.
