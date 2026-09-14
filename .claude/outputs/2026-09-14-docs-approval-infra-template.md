# Docs change approval — infra-template — 2026-09-14

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-14-docs-diff-infra-template.patch` |
| **Status** | **Approved 2026-09-14** by the founder |
| **Why** | `W-01`, `W-02` and `W-03` each carried three "not applicable" sections, because the only spec template describes a customer-facing feature. Infra tickets need a template shaped like an environment. And `W-03`'s own spec still described the local done-check with the count it had before `W-03` added the tenth gate. |

## Paths covered

- `docs/target-state/features/TEMPLATE-INFRA.md` @ `8ffc3336d51b74a352253447b551dea12e613214`
- `docs/target-state/features/W-03-build-test-pipeline.md` @ `84c4a94f9a49939b33e5a2c47fc91558860fb5b2`

## What changed

**`TEMPLATE-INFRA.md`** — new. For tickets labelled `skill-INFRA`, `skill-DATA` or
`skill-SEC`, whose subject is an environment rather than a screen. Drops Flow, Frontend
changes and the API contract; adds a Baseline table in §1 and a "Proving it" section
built on the idea that an infra deliverable is usually a gate, so the test is that it
fails when it should.

Its line citations were corrected on the way in: `check-done.mjs:79` and `:139` became
`:116` and `:384` after issue #100 rewrote gate 5, and its description of what gate 5
allows was rewritten to match the two-branch rule that now exists.

**`W-03-build-test-pipeline.md:193`** — the Rollback section said `check-done.mjs` and
`guard-merge` "still enforce the nine gates locally", in the present tense, in the spec
of the very ticket that added the tenth. Now reads "the other gates", which does not go
stale the next time a gate is added.

Two other occurrences of the old count were left alone deliberately. Line 28 is in
§1 Problem and describes the world before `W-03`; line 231 records that the nine-gate
check became a ten-gate check. Both are correct as written, and rewriting them would
falsify the record rather than fix drift.

## Note

This change is also the first real exercise of the route it travels. Issue #100's
done-when 3 asked for exactly this: 62 harness cases and three live-git cases said the
route worked, and not one of them was a pull request actually using it.
