---
name: merge
description: Get a finished feature onto main. Runs the machine-checked definition of done and one independent read on the developer's branch, then the founder merges. Updates the tracker.
---

# merge

Invoke as `/merge W-nn`, on your `dev-<name>` branch, when the feature is finished.

**Steps 1 and 2 are the developer's. Step 3 is the founder's** — only the founder merges
to `main`. There is no pull request: the branch is squashed onto `main` and pushed.

---

## Step 1 — the done check

```bash
node .claude/scripts/check-done.mjs W-nn
```

Five gates, all must pass:

| Gate | Refuses when |
|---|---|
| Spec exists | No `docs/target-state/features/W-nn-*.md` |
| `legacy/` untouched | The diff changes a frozen file |
| `ddl-auto` set nowhere | A real setting, not a comment |
| No floating-point money | `double` or `float` on an amount, salary, pay, tax or deduction field |
| CI green for this commit | No `ci.yml` run for HEAD, still running, or not `success` |

**Build and tests are not re-run here.** CI already ran them on this exact commit and the
last gate refuses unless it went green.

## Step 2 — one independent read

Spawn **reviewer** with `git diff main...HEAD`, the spec path, and the ticket number.
It has no edit tools, so it reports and this skill fixes.

**This is the only independent read in the whole process, and it runs once.** It exists
because running things is not the same as reading them: `W-02` passed 23 of 23 tests
while the Keycloak admin login it documented returned 401 — the test hit the realm
endpoint, which works whether or not the admin user exists.

The standard is the spec's verification and done-when sections. Not taste — the spec
settled the approach.

| Reviewer says | Do |
|---|---|
| Nothing, or cosmetic only | Go to step 3 |
| A real defect | **Fix it here**, commit, re-run step 1, go to step 3. No report file |
| It is out of this ticket's scope | Say so in the merge commit message and carry on |

Spot-check two `file:line` citations before acting on them.

Before handing over: set the tracker row to `Ready to merge — dev-<name>`, commit, push,
and tell the founder. If step 2 produced a fix, run step 1 again first.

## Step 3 — the founder puts it on main

```bash
git checkout main && git pull --ff-only
git merge --squash origin/dev-<name>
git commit -m "W-nn — <title>

<what changed, what was deferred, what is outstanding>"
git push origin main
```

Squash, so `main` carries one commit per ticket.

**Say what was deferred or left outstanding.** A message that only lists achievements is
how "we'll fix it next ticket" disappears.

## Step 4 — after

1. **Tracker:** set the row to `Done` with the merge commit and Built by; set every row
   this one unblocks to `Ready`. **Only this step marks anything Done** — Done means on
   `main`.
2. **Update `.claude/work/active-work.md`** — what is newly unblocked.
3. If the ticket changed how something documented actually works, edit that document in
   `docs/` in the same commit. Not otherwise.
4. The developer resets their branch for the next feature:
   `git checkout dev-<name> && git reset --hard origin/main && git push --force-with-lease`.

---

## What the dev reads

One line on success.

```
W-nn — <title>: pushed to main.
Commit <sha>. Tracker row Done. dev-<name> reset to main.
```

On refusal, name the gate and what closes it, in one sentence:

```
Not merged. <Gate> failed: <what is wrong, in plain English>.
Fix with: /develop W-nn
```

## What this skill will not do

| | |
|---|---|
| Merge with a failing gate | `check-done.mjs W-nn` must pass first |
| Merge work the reviewer never read | Step 2 is not optional |
| Force, or bypass a gate to "unblock" | A gate that is wrong gets fixed in the open, as a change to `check-done.mjs` with a reason. Never stepped around quietly |
