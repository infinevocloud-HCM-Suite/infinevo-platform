# Active Work

> Tracked skeleton. The live file (`active-work.md`) is gitignored — copy this over it
> when starting fresh, then populate from **actual repo state**, never from memory
> (root `CLAUDE.md` rule 6). Cite branch, SHA and date for every claim.
> Last refreshed: <YYYY-MM-DD>

## Repo state at last refresh

| App | Branch | HEAD | Last commit | Sync |
|---|---|---|---|---|
| `HRMS_Backend` | | | | |
| `HRMS_Frontend` | | | | |
| `Payroll-Bend-SBoot` | | | | |
| `Payroll-Fend-react` | | | | |

Refresh with:

```bash
for d in HRMS_Backend HRMS_Frontend Payroll-Bend-SBoot Payroll-Fend-react; do
  b=$(git -C "$d" branch --show-current)
  echo "$d [$b] $(git -C "$d" rev-parse --short HEAD) \
$(git -C "$d" log -1 --format='%ad %an - %s' --date=short) \
[$(git -C "$d" rev-list --left-right --count HEAD...origin/$b 2>/dev/null)]"
done
```

---

## Current direction

<The one or two sentence goal everything currently serves.>

1. **<Thread>** — <what it is>. *<state / blocked on what>*

---

## In flight

| Item | Where | State |
|---|---|---|
| | | |

---

## Frozen

| Item | Evidence | Note |
|---|---|---|
| | | |

---

## Open questions

| # | Question | Blocks |
|---|---|---|
| | | |
