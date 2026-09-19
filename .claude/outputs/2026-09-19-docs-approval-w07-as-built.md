# Docs change approval — w07-as-built — 2026-09-19

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-19-docs-diff-w07-as-built.patch` |
| **Status** | **Approved 2026-09-19** by the founder |
| **Why** | `W-07` Tenant model merged as `c1cb5ee` (#8, PR #131). `core.tenant`, the row-level security pattern and the three CI gates now exist, and the design documents still described the state before them — including one rule that contradicted `D-08`. |

## Paths covered

- `docs/CONVENTIONS.md` @ `94ba73d7d29532429da2d78aaaf49bc4a7eeba4b`
- `docs/target-state/02-data-model.md` @ `9991b3111305fb52c5f7678ded522026fd345206`
- `docs/target-state/04-runtime-containers.md` @ `db9caf02b1e5dafc9050bea367471e55c6386c05`
- `docs/target-state/07-decisions.md` @ `98af026bfb355f7f514e599abf55698efad2aa49`
- `docs/target-state/08-work-plan.md` @ `3ef0362c07c7d8914c6ac20a420a9f3c37c39a5e`
- `docs/target-state/09-build-order.md` @ `76def46e2b63a67becd60302927917214c9888e8`

## What each file changes

| Doc | Change | Evidence it is right |
|---|---|---|
| `CONVENTIONS.md` | Rule 7 no longer says `tenant_id` is required on "reference tables" | It contradicted `D-08` (`07-decisions.md:20`) and `02-data-model.md:16`, and both shipped gates are scoped "non-reference" (`ci.yml:189`, `:229`) |
| `02-data-model.md` | §9 gains the policy SQL, the variable `app.current_tenant_id`, the policy name `tenant_isolation`, and why the `CASE` and absent `WITH CHECK` are deliberate | `V001__tenant.sql:24-33`; `TenantContext.java:108` |
| `04-runtime-containers.md` | Seed row keeps ⏳ but the reason is corrected — the table exists; the seed's columns do not match it | `V001__tenant.sql:5-14` vs `infra/docker/seed/01-tenants.sql:10-18` |
| `07-decisions.md` | `D-55`–`D-58` appended | `D-54` was the highest (`:66`); append-only per `:3-5` |
| `08-work-plan.md` | `W-07` marked merged; `W-08` shown unblocked | Follows the file's own convention at `:133` and `:134` |
| `09-build-order.md` | `W-07` detail entry in done form; chain row updated | Follows the `W-05` done form at `:171` and the track form at `:51` |

## The four decisions

`D-55`, `D-57` and `D-58` ratify what the `W-07` spec left open as D-1, D-2 and D-3; the
recommendation was followed in each case. **`D-56` is new** — it came out of the review,
not the plan: the isolation policy must use the three-branch `CASE`, because after a
transaction ends the session variable reverts to the empty string and `''::uuid` raises
`22P02` on a pooled connection. Reproduced against PostgreSQL 16 before it was written down.

## Verification

- `git apply --check` clean before applying.
- Markdown link check across the repository: **0 broken**.
- Facts confirmed by the `explorer` agent against `path:line` before the patch was written;
  claims it refuted were dropped rather than softened — in particular, the policy has **no**
  `WITH CHECK` clause, so the documents say `USING` supplies the check by default.

## Known drift left out of this pass, deliberately

- `W-05`, `W-06`, `W-50` and `W-51` are also unmarked in `08-work-plan.md` and
  `09-build-order.md`. Real, but not `W-07`'s to fix.
- Root `CLAUDE.md` rule 7 says "including lookup tables" — ambiguous, never names
  `reference`, judged not wrong.
- `infra/docker/README.md:118` and `infra/docker/seed/README.md:11-12` repeat the stale
  "`core.tenant` does not exist" wording. Outside `docs/`, so outside this skill.
