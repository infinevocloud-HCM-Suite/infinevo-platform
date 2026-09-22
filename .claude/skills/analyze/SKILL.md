---
name: analyze
description: Answer a "how does X work / where is X / what would Y touch" question with cited evidence, without changing anything. Covers the frozen system in legacy/ and the new platform in code/. Use before writing a spec, or when a doc looks stale.
---

# analyze

Read-only investigation. Produces a cited report; changes no code and no docs.

Most questions are about **how the frozen system works today**, asked by someone about to
port that logic. Always say whether a fact comes from `legacy/` (what is being replaced)
or from `docs/target-state/` and `code/` (what is being built) — confusing the two is the
failure mode that costs a rewrite.

## Steps
1. Read `.claude/work/active-work.md` (hard rule 2) and the matching entries in
   `legacy/docs/FEATURE_MAP.md` and `legacy/docs/GAP_INVENTORY.md` so known facts are not re-derived.
2. Restate the question as 1–3 concrete sub-questions (e.g. "which controller", "which
   table", "which screen calls it").
3. Spawn **explorer** with those sub-questions, the live branch names, and the output path
   `.claude/outputs/<YYYY-MM-DD>-analyze-<slug>.md`.
4. Read the explorer report. Spot-check **at least two** `path:line` citations yourself with
   Read; if one is wrong, send the explorer back with the correction.
5. If the code contradicts a doc, record it in the report under "Doc drift" with both the doc
   line and the code line. Do **not** edit the doc — that is `sync-docs`.
6. If a new defect surfaces that is not in `GAP_INVENTORY.md`, add a "Proposed GAP entry"
   block (next free ID, category BUG/DEBT, evidence) to the report.
7. Reply with the summary below. If the answer makes the next step obvious, say what it
   is — do not start it without being asked.

---

## What the dev reads

Most people run this to understand a ticket before writing its spec. So lead with the
plain-English answer, not the evidence. No jargon, short sentences, and say what it
means for the work rather than only what the code does.

```
W-nn in plain English

What it is:      <one or two sentences. What a user gets, or what stops being broken>
How it works today: <one or two sentences, or "it does not exist yet">
What it touches: <the modules and screens, named plainly>
The catch:       <the one thing that will bite - a load-bearing typo, a shared table,
                  a legacy behaviour that must be kept. Or "nothing obvious">

| Claim | Evidence |
|---|---|
| Leave requests are approved by the reporting manager | legacy/HRMS_Backend/.../LeaveController.java:88 |

Doc drift: <doc + line, or "none">
Proposed GAP entries: <or "none">
Full report: .claude/outputs/<date>-analyze-<slug>.md
Next: /plan-feature W-nn
```

**Six lines of plain English, then the evidence.** Somebody who reads only the top must
still understand the ticket well enough to talk about it.
