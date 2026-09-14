---
name: merge
description: Merge a reviewed pull request into main. Runs the machine-checked definition of done first and refuses if any gate fails. The only route code takes to main.
---

# merge

Invoke as `/merge <pr-number>`.

**The only way code reaches `main`.** Direct pushes carrying `code/` are refused by the
`guard-merge` hook.

GitHub cannot enforce branch protection on a private repository on the Free plan
(`D-43`), so `main` would otherwise be held by convention alone. This skill plus the
hook is the enforcement actually available: it runs on the machine doing the merge,
before the command leaves it.

---

## Step 1 — the done check

```bash
node .claude/scripts/check-done.mjs <pr>
```

Ten gates. A receipt is written **only if every one passes**:

| Gate | Refuses when |
|---|---|
| PR open, links its issue | No `Closes #<issue>` in the body |
| Approved spec exists | No spec for the branch's `W-nn`, or not marked Approved |
| **No open High findings** | Any `/verify` or `/review` report has a High finding still OPEN |
| `legacy/` untouched | The diff changes a frozen file |
| `docs/` changed only for this spec | The diff touches another document |
| `ddl-auto` set nowhere | A real setting, not a comment |
| No floating-point money | `double` or `float` on an amount, salary, pay, tax or deduction field |
| Backend builds, tests pass | `./mvnw clean verify` fails |
| Frontend lints and builds | Either fails |
| CI green for this commit | `ci.yml` has no run for the PR's HEAD commit, is still running, or did not conclude `success` |

**Do not work around a failing gate.** It is telling you the ticket is not finished.
Run `/develop` and come back.

## Step 2 — merge

The hook checks the receipt is present, records PASS, is under an hour old, and was
written **for the commit currently at HEAD**. A commit after the check invalidates it —
deliberately, since otherwise the check proves nothing about what is being merged.

```bash
gh pr merge <pr> --squash --delete-branch \
  --subject "W-nn — <title> (#<pr>)" \
  --body "<what changed, what was deferred, what is outstanding>

Closes #<issue>"
```

Squash, so `main` carries one commit per ticket. The detail stays in the pull request.

**Say what was deferred or left outstanding in the merge message.** A merge message that
only lists achievements is how "we'll fix it next ticket" disappears.

## Step 3 — after

```bash
git checkout main && git pull --ff-only
git branch -d W-nn-<slug>
git fetch --prune
cd code/backend && ./mvnw -B clean verify     # main itself still builds
```

Then:

1. **Confirm the issue closed.** `gh issue view <issue>` — it should be CLOSED. If the
   body's `Closes #` was malformed it will still be open; close it with a comment
   pointing at the merge.
2. **Update `.claude/work/active-work.md`**: which tickets are newly unblocked, and what
   is now ready. Every skill reads that file first, and a stale one steers everything
   after it wrongly.
3. **Carry deferred work forward.** Anything the PR deferred must exist as a GitHub
   issue before you finish here, linked from the merge. This is the step that decides
   whether "deferred" meant scheduled or forgotten.
4. `/sync-docs` if the build diverged from what the documents describe.
5. **Clear what the ticket left behind.** `node .claude/scripts/prune-outputs.mjs` —
   it prints a verdict per file in `.claude/outputs/` and deletes nothing on its own.
   Run it **after** `/sync-docs`, which is what decides whether a report is still cited.
   `ORPHAN` means nothing under `docs/`, `.claude/work/` or the skills points at it any
   more. Read the list, then `--delete` if you agree. `REVIEW` rows name a ticket and
   are never deleted by the flag — decide those yourself once the ticket is closed.
   Left alone, this directory grows a shadow of the design: 180K of stale duplicate
   `target-state/` sat here until 2026-09-13, seven decisions behind `docs/`.

---

## What this skill will not do

| | |
|---|---|
| Merge without a passing receipt | The hook refuses the command |
| Merge with a High finding open | The done check refuses |
| Push code straight to `main` | The hook refuses |
| Merge its own work unreviewed | Run `/review` first. It takes minutes and found five defects on `W-02` |
| Force, or bypass a gate to "unblock" | A gate that is wrong gets fixed in the open, as a change to `check-done.mjs` with a reason. Never stepped around quietly |

## If a gate is wrong

It will happen — the checks are code and code has bugs. Fix the check, do not evade it:
change `check-done.mjs`, say why in the commit, and re-run. A gate that people have
learned to work around protects nothing.
