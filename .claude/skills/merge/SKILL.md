---
name: merge
description: Put a finished branch onto main. Runs the machine-checked definition of done first and refuses if any gate fails. The only route code takes to main.
---

# merge

Invoke as `/merge W-nn`, on the ticket's branch.

**The only way code reaches `main`.** A hand-rolled push carrying `code/` is refused by
the `guard-merge` hook; this skill is what makes the push legal, and only after the
checks pass.

GitHub cannot enforce branch protection on a private repository on the Free plan
(`D-43`), so `main` would otherwise be held by convention alone. This skill plus the
hook is the enforcement actually available: it runs on the machine doing the merge,
before anything leaves it.

There is no pull request. The branch is squashed onto `main` locally and pushed.

---

## Step 1 — the done check

```bash
node .claude/scripts/check-done.mjs
```

It reads the branch you are on. Eight gates. A receipt is written **only if every one
passes**:

| Gate | Refuses when |
|---|---|
| Branch names a ticket | The branch is not `W-nn-<slug>` or `docs-<slug>` |
| Approved spec exists | No spec for this `W-nn`, or it is not marked Approved |
| **No open High findings** | Any `/verify` or `/review` report has a High finding still OPEN |
| `legacy/` untouched | The diff changes a frozen file |
| **`docs/` changed only by a recognised route** | A `W-nn` branch touches any document other than its own `features/W-nn-*` spec, or a `docs-<slug>` branch touches anything outside `docs/` |
| `ddl-auto` set nowhere | A real setting, not a comment |
| No floating-point money | `double` or `float` on an amount, salary, pay, tax or deduction field |
| CI green for this commit | `ci.yml` has no run for this branch's HEAD commit, is still running, or did not conclude `success` |

**Build and tests are not re-run here.** CI already ran them on this exact commit, and
the last gate refuses unless that run went green. Running `mvnw clean verify` a second
time on the same bytes answers a question already answered, and costs ten minutes each
time.

**Do not work around a failing gate.** It is telling you the ticket is not finished.
Run `/develop` and come back.

## Step 2 — put it on main

The hook checks the receipt is present, records PASS, and was written **for the commit
currently at HEAD**. A commit after the check invalidates it — deliberately, since
otherwise the check proves nothing about what is being merged.

```bash
git checkout main && git pull --ff-only
git merge --squash W-nn-<slug>
git commit -m "W-nn — <title>

<what changed, what was deferred, what is outstanding>

Closes #<issue>"
git push origin main
git branch -D W-nn-<slug> && git push origin --delete W-nn-<slug> 2>/dev/null
```

Squash, so `main` carries one commit per ticket.

**Say what was deferred or left outstanding in the commit message.** A message that only
lists achievements is how "we'll fix it next ticket" disappears.

## Step 3 — after

**Do not rebuild.** This step used to run `./mvnw -B clean verify` on main, which is the
third full build of bytes CI already proved: once on the branch, once on the push to
main, once here. The receipt makes the duplication provable rather than likely — it
matches on the *tree*, so the content that lands on main is by construction the content
CI went green on. If main had moved, the receipt would be void and the push refused.

Step 1 above already makes this argument. It was worth following.

1. **Confirm the ticket closed.** `gh issue view <issue>` — it should be CLOSED. If the
   `Closes #` was malformed it will still be open; close it with a comment pointing at
   the commit.
2. **Update `.claude/work/active-work.md`**: which tickets are newly unblocked, and what
   is now ready. Every skill reads that file first, and a stale one steers everything
   after it wrongly.
3. **Carry deferred work forward.** Anything deferred must exist as a GitHub ticket
   before you finish here, linked from the commit. This is the step that decides whether
   "deferred" meant scheduled or forgotten.
4. **Run `/sync-docs`.** Always, not only when you think something drifted — it is what
   tells you whether a core document now says something untrue.
5. **Clear what the ticket left behind.** `node .claude/scripts/prune-outputs.mjs` —
   it prints a verdict per file in `.claude/outputs/` and deletes nothing on its own.
   Run it **after** `/sync-docs`, which is what decides whether a report is still cited.
   `ORPHAN` means nothing under `docs/`, `.claude/work/` or the skills points at it any
   more. Read the list, then `--delete` if you agree. `REVIEW` rows name a ticket and
   are never deleted by the flag — decide those yourself once the ticket is closed.

---

## What the dev reads

One line on success. Nothing longer — the detail was in `/develop`'s summary.

```
W-nn — <title>: pushed to GitHub main successfully.
Commit <sha>. Ticket #<issue> closed. Branch deleted.
Next: /sync-docs
```

On refusal, name the gate that failed and what closes it, in one sentence:

```
Not merged. <Gate name> failed: <what is wrong, in plain English>.
Fix with: /develop W-nn
```

---

## What this skill will not do

| | |
|---|---|
| Merge without a passing receipt | The hook refuses the push |
| Merge with a High finding open | The done check refuses |
| Merge work `/review` never read | `/develop` runs it. If it was skipped, run it now |
| Force, or bypass a gate to "unblock" | A gate that is wrong gets fixed in the open, as a change to `check-done.mjs` with a reason. Never stepped around quietly |

## If a gate is wrong

It will happen — the checks are code and code has bugs. Fix the check, do not evade it:
change `check-done.mjs`, say why in the commit, and re-run. A gate that people have
learned to work around protects nothing.
