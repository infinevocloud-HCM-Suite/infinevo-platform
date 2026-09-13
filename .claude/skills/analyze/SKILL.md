---
name: analyze
description: Answer a "how does X work / where is X / what would Y touch" question about any of the four apps with cited evidence, without changing anything. Use before planning a feature or when a doc looks stale.
---

# analyze

Read-only investigation. Produces a cited report; changes no code and no docs.

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
7. Reply to the founder with: answer (≤6 lines), evidence table, doc drift, proposed GAP
   entries, and the report path.
8. **STOP.** No follow-on work without a new instruction.
