# W-72 — Cutover and rollback runbook

| Field | Value |
|---|---|
| **Work item** | `W-72` · issue [#94](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/94) |
| **Kind** | Infra — a runbook, no code |
| **Stream / track** | I — Migration |
| **Size / skill** | S · INFRA |
| **Owner** | founder |
| **Blocked by** | `W-71` three clean rehearsals on `uat` · `W-62` a restore actually performed (`docs/trackers/INFRA-TRACKER.md:93`) · streams C–E complete · `W-67` R5 and R6 open items dated |
| **Blocks** | nothing — last item in the build order |
| **Decisions** | `W-67` R1–R7, `W-71` §3 (dump route), `D-18` (no geo backup) |
| **Gaps addressed** | none |
| **Status** | Ready — the runbook is written here; it is executed by the founder, not a developer |

---

## 1. Problem

The build order's last item: freeze window, switch, smoke test, rollback runbook; done
when cutover completes and the rollback window closes; schedule outside payroll week; old
system stays available, read-only (`docs/target-state/09-build-order.md:315`). Nothing is
written for it. A cutover that is not scripted is a rehearsal that happens once, in
production, without a log.

## 2. Scope

**In scope** — the runbook below, the go/no-go list, the rollback. It is the deliverable.

**Out of scope** — any code. If the runbook shows a missing piece, that is a new ticket.

## 3. Go / no-go, the day before

| # | Check | Evidence |
|---|---|---|
| 1 | Three consecutive clean rehearsals on `uat` | `W-71-rehearsal-log.md`, last three rows `clean` |
| 2 | A Postgres restore has been performed on `uat` | `W-62` row says restored, with a date |
| 3 | Finance has signed the last rehearsal's LOP log | `core.lop_mismatch_log` on `uat`, no row `PENDING_FINANCE_SIGN_OFF` |
| 4 | Founder's tenant list is final | `W-67` R5 duplicate names decided, `org_mapping.json` on `main` matches |
| 5 | Bank decision made | `W-67` R6 |
| 6 | Date is outside payroll week | no `payroll.payrun` on legacy with `payDate` within 7 days |
| 7 | Prod image tags pinned | `caj-data-migration-prod`, `caj-flyway-prod` point at a `git-<sha>` image, not the sentinel (`infra/azure/HANDBOOK.md:240-243`) |
| 8 | Prod Flyway at the release's version | `caj-flyway-prod` last run `No migration necessary` |

Any `no` stops the cutover. Reschedule, do not improvise.

## 4. The window

| Step | Who | Where | Action | Done when |
|---|---|---|---|---|
| T-1 | founder | DigitalOcean, both apps | Set the legacy apps read-only: nginx returns 503 on every non-GET to both backends. Post the banner in both frontends | `curl -X POST` to each backend returns 503 |
| T-2 | founder | droplet | `mysqldump --single-transaction` both databases, sha256, upload to `migration-dumps/prod-<date>/`, set `legacy-dump-sha256` in Key Vault (`W-71` §5 steps 1–3) | blob listed, secret set |
| T-3 | founder | Azure | Take a manual Postgres backup: `az postgres flexible-server backup create -n <server> --backup-name pre-cutover` | backup listed |
| T-4 | founder | Azure | `az containerapp job start -g rg-infinevo-prod -n caj-data-migration-prod --env-vars RUN_ID=prod-<date>` | execution `Succeeded` |
| T-5 | founder | laptop | Download `results/`; `reconciliation_*.json` every tenant `clean: true`; quarantine files read | the rehearsal log gets its production row |
| T-6 | finance | laptop | Read `lop_mismatch_prod.csv`; sign by SQL `update core.lop_mismatch_log set status='APPROVED_BY_FINANCE' where ...` or ask for a stop | no `PENDING` rows |
| T-7 | founder | Azure | Smoke: run the Playwright per-role suite against prod with the tenant admin login of the founder's tenant (`code/frontend/e2e/`, `npm run e2e` with `BASE_URL`) | suite green |
| T-8 | founder | Azure | Hand check against the legacy screens still readable: 10 employees (profile, bank, manager, designation), 3 pay runs, 2 payslips (totals), 2 CTC structures, 5 documents, 2 leave balances, 1 timesheet, 1 tax declaration, 1 open claim | all match |
| T-9 | founder | email | Send invitations: every `core.employee_invitation` row for cohort 2, every Keycloak user gets a reset-password mail | mails sent |
| T-10 | founder | DNS, Front Door | Point the production host at Front Door (`infra/azure/main.bicep:466`); keep the legacy hosts alive on their old names, read-only | new host serves the platform |

Rollback window: 7 days, the Postgres backup retention (`infra/azure/modules/postgres.bicep:39`).

## 5. Rollback

| Trigger | Action |
|---|---|
| T-4 fails or T-5 not clean | Nothing has changed for users. Purge every tenant (`infra/postgres/purge-tenant.sql`), lift the 503 on legacy, reschedule |
| T-7 or T-8 fails | Same as above. The legacy apps were never switched |
| A defect found after T-10, inside 7 days | Point DNS back at legacy, lift the 503. Postgres rows stay for diagnosis; the next attempt purges and re-runs from a fresh dump, since legacy has moved on |
| After 7 days | No rollback. Legacy is history only. Defects are fixed forward |

## 6. After the window closes

| Action | When |
|---|---|
| Legacy apps stay up, read-only, for 90 days | until +90 days |
| Delete `migration-dumps/prod-<date>/` | +7 days |
| Delete `legacy-dump-sha256` secret | +7 days |
| Close the droplet firewall, then the droplet | +90 days |
| Tracker: `W-67`–`W-72` Done; `MIGRATION-TRACKER.md` summary rewritten | same day |

## 7. Standing rules

Creates nothing. No table, no Flyway, no image, no Bicep.

## 8. Verification

The runbook is verified by being executed on `uat` end to end once, as the third
rehearsal of `W-71`, including the 503 freeze on a legacy non-prod copy and the DNS step
on a `uat` host. Every row in §4 gets a time and an initial.

| Check | Expected |
|---|---|
| `uat` dry run of §3–§6 | every step has an initial and a time, no step skipped |
| Production execution | the same table, filled, pasted into `W-71-rehearsal-log.md` as the last row |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Legacy users keep writing during the window | Medium | The 503 freeze at T-1 is the first step, not a courtesy |
| Finance unavailable at T-6 | Medium | Finance's slot is booked with the window; no sign-off, no T-7 |
| Invitation mails flagged as spam | Medium | Test send to five addresses in the `uat` dry run |
| Legacy MySQL on DigitalOcean removed before 90 days | Low | The droplet is not touched by any step here |
