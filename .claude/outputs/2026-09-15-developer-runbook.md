# Developer runbook — claim a ticket, build it, hand it over

Every command, in order, for one ticket. Replace `W-05`, `6` and the slug with yours.
Run the `gh` and `git` commands in a terminal at the repo root; run the `/` commands
inside Claude Code in the same folder.

## Your lane

| You | Lane | Claim in this order |
|---|---|---|
| **SayInfi** | Core → HRMS | W-05 · W-06 · W-07 · W-08 · W-13 · W-14 · W-17 · W-15 · W-16.1 · W-16.4 · W-18 · W-41 · W-39 · W-42 · W-40 · W-25 · W-43 · W-44 |
| **karma** | Infra → identity → security | W-49 · W-50 · W-52 · W-51 · W-54 · W-10 · W-12 · W-11 · W-57 · W-58 · W-53 · W-60 · W-62 · W-56 · W-61 · W-64 · W-24 · W-65 · W-63 · W-72 |
| **BirenGit** | Payroll | #101 · #104 · W-59 · W-09 · W-22 · W-55 · W-19 · W-26 · W-32.1 · W-32.2 · W-32.3 · W-32.4 · W-31 · W-33.1 · W-33.2 · W-33.3 · W-27 · W-34 · W-28 · W-29.1 · W-29.2 · W-29.3 · W-29.4 · W-36 · W-30 · W-35 · W-37 · W-38 |
| **Gau318** | Website → migration → FE | W-66 · W-67 · W-68.1 · W-68.2 · W-68.3 · W-20 · W-21 · W-23 · W-16.2 · W-16.3 · W-45 · W-46.1 to W-46.5 · W-47.1 to W-47.5 · W-48 · W-69 · W-70 · W-71 |

Take the first ticket in your lane that is `ready` and has no assignee. If your lane
has nothing ready, take the top `next` ticket that matches your skill label.

## Once, on your machine

```bash
gh auth login                                  # GitHub CLI, choose HTTPS, browser login
gh auth status                                 # must show the infinevocloud-HCM-Suite org
git clone https://github.com/infinevocloud-HCM-Suite/infinevo-platform.git
cd infinevo-platform
```

Setup after that is `CONTRIBUTING.md` §1.

## Step 1 — check what you hold

```bash
gh issue list --assignee @me
```

Two open tickets: claim nothing until one merges. One or none: continue.

## Step 2 — find the next ticket

```bash
gh issue list --label next  --search "no:assignee"                  # the head of the queue
gh issue list --label ready --search "no:assignee" --label skill-BE  # your skill, if next is empty
gh issue view 6                                                      # read it before claiming
```

Pick the first one that is in your lane. Note its number; the examples below use `6`.

## Step 3 — claim it

```bash
gh issue edit 6 --add-assignee @me
gh issue comment 6 --body "claimed"
```

Wait a minute, then check the bot did not revert you:

```bash
gh issue view 6 --json assignees --jq '.assignees[].login'
```

If the output is empty, the bot reverted the claim and explained why in a comment
(`gh issue view 6 --comments`). Go back to step 2 and take the next one.

## Step 4 — branch

```bash
git checkout main
git pull --ff-only
git checkout -b W-05-postgres-schemas
```

The branch name must start with the ticket id. That is how the stale sweep knows you
have started.

## Step 5 — understand it (optional)

In Claude Code:

```
/analyze how do the two legacy apps model the employee record
```

## Step 6 — write the spec

In Claude Code, one of:

```
/plan-feature W-05      # skill-BE, skill-FE
/infra-task W-05        # skill-INFRA, skill-DATA, skill-SEC
```

It writes `docs/target-state/features/W-05-<slug>.md` and stops. Push it and ask
for approval:

```bash
git add docs/target-state/features/W-05-*.md
git commit -m "W-05: spec for founder approval"
git push -u origin W-05-postgres-schemas
gh issue comment 6 --body "Spec ready for approval: https://github.com/infinevocloud-HCM-Suite/infinevo-platform/blob/W-05-postgres-schemas/docs/target-state/features/W-05-postgres-schemas.md"
```

## Step 7 — wait for approval

No code before the founder says yes on the ticket. While waiting, do not claim
another ticket unless you hold only this one and it is your first.

## Step 8 to 10 — build, test, verify

In Claude Code, in this order, and again for every round of findings:

```
/develop W-05
/test W-05
/verify W-05
```

`/verify` fixes nothing. Each failure becomes a numbered finding; `/develop W-05`
picks up the open ones. Commit as you go:

```bash
git add -A
git commit -m "W-05: <what changed>"
git push
```

## Step 11 — open the pull request

```bash
gh pr create --base main \
  --title "W-05 — Postgres & schemas" \
  --body "Closes #6

Spec: docs/target-state/features/W-05-postgres-schemas.md"
gh pr view --web          # optional, opens it in the browser
```

`Closes #6` is mandatory. The merge gate reads it, and closing the ticket is what
releases the tickets behind it.

**You may now claim one more.** Run steps 1 to 4 for the next ticket in your lane
while this one is in review.

## Step 12 — review

In Claude Code, against the PR number:

```
/review 110
```

Findings go back to step 8 on the same branch. Push, and the PR updates itself.

## Step 13 — docs, only if the build diverged from the design

```
/sync-docs
```

## Step 14 — the founder merges

Nothing for you to run. After the merge:

```bash
git checkout main
git pull --ff-only
git branch -d W-05-postgres-schemas
```

Then step 1.

## If something goes wrong

| Situation | Command |
|---|---|
| Blocked for more than a day | `gh issue comment 6 --body "Blocked: <why>"` — silence is what gets a claim released |
| Need to drop the ticket | `gh issue edit 6 --remove-assignee @me` then `gh issue comment 6 --body "Released: <why>"` |
| Bot released your claim after 3 idle working days | Claim it again with step 3 when you are ready to start |
| Want to see the whole queue | `gh issue list --label ready --search "no:assignee" --limit 50` |
| Want to see what is coming | `gh issue list --label blocked --limit 100` |

---

## Quick start — your first claim, copy and paste

Claim only the first ticket in your list. The bot reverts claims on `blocked` tickets;
each later one opens when the one before it merges.

### SayInfi — W-05 Postgres & schemas (#6)

```bash
gh issue edit 6 --add-assignee @me
gh issue comment 6 --body "claimed"
git checkout main && git pull --ff-only
git checkout -b W-05-postgres-schemas
```
Then `/infra-task W-05`. Your list: `#6 #7 #8 #9 #14 #15 #21 #16 #17 #20 #22 #53 #51 #54 #52 #29 #55 #56`

### karma — W-49 Containerisation (#69)

```bash
gh issue edit 69 --add-assignee @me
gh issue comment 69 --body "claimed"
git checkout main && git pull --ff-only
git checkout -b W-49-containerisation
```
Then `/infra-task W-49`. Your list: `#69 #70 #72 #71 #74 #11 #13 #12 #77 #78 #73 #80 #82 #76 #81 #84 #28 #85 #83 #94`

### BirenGit — Merge-gate hardening (#101)

```bash
gh issue edit 101 --add-assignee @me
gh issue comment 101 --body "claimed"
git checkout main && git pull --ff-only
git checkout -b merge-gate-hardening-101
```
Then `/infra-task 101`. Your list: `#101 #104 #79 #10 #26 #75 #23 #30 #39 #40 #41 #42 #38 #43 #44 #45 #31 #46 #32 #33 #34 #35 #36 #48 #37 #47 #49 #50`

### Gau318 — W-66 Marketing website (#86)

```bash
gh issue edit 86 --add-assignee @me
gh issue comment 86 --body "claimed"
git checkout main && git pull --ff-only
git checkout -b W-66-marketing-website
```
Then `/plan-feature W-66`. Your list: `#86 #87 #88 #89 #90 #24 #25 #27 #18 #19 #57 #58 #59 #60 #61 #62 #63 #64 #65 #66 #67 #68 #91 #92 #93`

### Every ticket after the first

Once your PR is open, take the next number in your list:

```bash
N=7                                                      # next number in your list
gh issue view $N --json labels --jq '[.labels[].name]'   # must contain "ready"
gh issue edit $N --add-assignee @me
gh issue comment $N --body "claimed"
git checkout main && git pull --ff-only
git checkout -b W-06-flyway                              # W-id first, then a slug
```
