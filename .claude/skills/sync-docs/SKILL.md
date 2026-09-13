---
name: sync-docs
description: Bring docs/ back in line with the code after a change or when analyze found drift. Produces a reviewable diff; only applies it after the founder approves, and only through this skill.
---

# sync-docs

The **only** sanctioned path to change `docs/`. The `guard-edit` hook blocks direct edits;
this skill produces a diff first and applies it only after approval.

## Steps
1. Collect the sources of truth for the change: the verifier report / merged diff, the
   `analyze` report's "Doc drift" section, or the founder's stated correction.
2. Identify affected docs: `ARCHITECTURE.md` (shape, ports, flows), `DB_SCHEMA.md` (tables —
   regenerate the affected table's column list from the entity, respecting Payroll's
   class-name-verbatim rule), `FEATURE_MAP.md` (files per feature), `GAP_INVENTORY.md`
   (status changes, new IDs), `CONVENTIONS.md`, per-app `CLAUDE.md`, and
   `docs/target-state/features/<slug>.md` for an approved plan being promoted.
3. Spawn **explorer** to confirm each fact you intend to write, with `path:line` evidence.
   Never write a path into a doc without confirming it exists on the live branch.
4. Write the proposed change as a **unified diff** to `agents/outputs/<date>-docs-diff-<slug>.patch`
   (create it by editing a copy under `agents/outputs/tmp-docs/` and running `git diff
   --no-index docs/<file> agents/outputs/tmp-docs/<file>`). Do not touch `docs/` yet.
5. Reply with the patch path, a table `| Doc | Lines | What changes | Evidence |`, and any
   entry you could not verify (marked so). **STOP; wait for "approved".**
6. On approval, apply the patch: the guard hook will block Edit/Write, so apply with
   `git apply agents/outputs/<patch>` from the repo root via Bash (this is the intended
   bypass — hooks guard the model's editors, not an approved patch). Never delete a doc:
   superseded files move to `docs/legacy/_archive/`.
7. Re-run the verification script from `agents/outputs/2026-09-11-docs-audit.md` §9 (path
   existence check) and paste the result. Update `agents/active-work.md` if direction or
   in-flight items changed. **STOP.**
