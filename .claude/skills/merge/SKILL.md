---
name: merge
description: Put a finished branch onto main. Runs the machine-checked definition of done, then one independent read, and refuses if either fails. The only route code takes to main.
---

# merge

Invoke as `/merge W-nn`, on the ticket's branch.

**The only way code reaches `main`.** A hand-rolled push carrying `code/` is refused by
the `guard-merge` hook. GitHub cannot enforce branch protection on a private repository
on the Free plan (`D-43`), so this skill plus the hook is the enforcement available.

There is no pull request. The branch is squashed onto `main` locally and pushed.

---

## Step 1 — the done check

```bash
node .claude/scripts/check-done.mjs
```

Seven gates. A receipt is written **only if every one passes**:

| Gate | Refuses when |
|---|---|
| Branch names a ticket | Not `W-nn-<slug>` or `docs-<slug>` |
| Approved spec exists | No spec for this `W-nn`, or not marked Approved |
| `legacy/` untouched | The diff changes a frozen file |
| `docs/` changed only by a recognised route | A `W-nn` branch touches a document other than its own spec |
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

The standard is the spec's verification and done-when sections. Not taste — disagreements
about approach belonged at spec approval.

| Reviewer says | Do |
|---|---|
| Nothing, or cosmetic only | Go to step 3 |
| A real defect | **Fix it here**, commit, re-run step 1, go to step 3. No report file |
| It is out of this ticket's scope | Say so in the merge commit message and carry on |

Spot-check two `file:line` citations before acting on them.

## Step 3 — put it on main

The hook checks the receipt is present, PASS, and written for the commit at HEAD. A
commit after the check invalidates it — so if step 2 produced a fix, step 1 must run
again.

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

**Say what was deferred or left outstanding.** A message that only lists achievements is
how "we'll fix it next ticket" disappears.

## Step 4 — after

1. **Confirm the ticket closed.** `gh issue view <issue>`.
2. **Update `.claude/work/active-work.md`** — what is newly unblocked. Every skill reads
   it first, and a stale one steers everything after it wrongly.
3. **Run `/sync-docs`** if the ticket changed how something documented actually works.
   Not otherwise.
4. `node .claude/scripts/prune-outputs.mjs` — prints a verdict per file, deletes nothing
   on its own. Read the list, then `--delete` if you agree.

---

## What the dev reads

One line on success.

```
W-nn — <title>: pushed to main.
Commit <sha>. Ticket #<issue> closed. Branch deleted.
```

On refusal, name the gate and what closes it, in one sentence:

```
Not merged. <Gate> failed: <what is wrong, in plain English>.
Fix with: /develop W-nn
```

## What this skill will not do

| | |
|---|---|
| Merge without a passing receipt | The hook refuses the push |
| Merge work the reviewer never read | Step 2 is not optional |
| Force, or bypass a gate to "unblock" | A gate that is wrong gets fixed in the open, as a change to `check-done.mjs` with a reason. Never stepped around quietly |
