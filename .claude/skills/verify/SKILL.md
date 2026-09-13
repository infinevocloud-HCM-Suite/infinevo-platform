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

1. Read the ticket's spec, particularly **§9 Verification** — it lists the exact
   commands and their expected output. That table is the test plan.
2. Spawn **verifier** with those commands.
3. Run the standing checks below as well, whatever the spec says.
4. Write the report. Post a summary as a comment on the pull request, if one is open.

## Standing checks — always, on every ticket

```bash
cd code/backend  && ./mvnw -B clean verify          # build + tests
cd code/frontend && npm run lint && npm run build
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/     # expect nothing
git diff --name-only origin/main..HEAD | grep -E '^legacy/' # expect nothing
cd code/backend && ./mvnw -B dependency:tree | grep -E 'com.infinevo:(hrms|payroll)'
```

The last one: `hrms` must not appear under `payroll`, nor the reverse. The build should
fail first, but verify rather than assume.

If the ticket touches the local stack, also `infra/docker/smoke.sh`.

## Check the checks, not just the code

A check that passes because it matches nothing reports green forever. When a guard is
part of the ticket, **break the thing it guards once, deliberately, and watch it fail**,
then put it back. `smoke.sh` passed its `ddl-auto` check on a comment for an hour.

---

## The report

Write to `.claude/outputs/<date>-verify-W-nn.md`.

```
# Verify — W-nn — <date time>

## Commands
| Check | Command | Exit | Verdict | Evidence (last lines) |

## Spec acceptance — section 9
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
- Clean → `/review <pr>`, which asks the different question: *is it right?*

Verify running clean is not sufficient on its own. `W-02` passed 23 of 23 while its
Keycloak admin login returned 401 — verify ran what it was told to run, and review
found what nobody had thought to check.
