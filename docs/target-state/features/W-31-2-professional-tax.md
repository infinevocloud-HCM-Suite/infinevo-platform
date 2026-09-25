# Feature: Professional tax — shared state slabs and per-tenant override

| Field | Value |
|---|---|
| **Feature ID** | `W-31.2` · from ticket #38 (`W-31`) · `PAY-08` part 2 of 4 |
| **Promoted to** | `docs/target-state/features/W-31-2-professional-tax.md` on the developer's `dev-<name>` branch — **`W-31-2` with hyphens**, never `W-31.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` (`reference/` and `payroll/`) |
| **Related gaps** | DEBT-024 (discounted), DEBT-035, DEBT-036, DEBT-037 (proposed below), DEBT-018, DEBT-022 |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing — `W-09` (`reference.state`, `V003`) and `W-14.1` (`core.work_location.state_code`, `V013`) are on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | 4 scripts — `V064` `reference` (two tables, as `V003` and `V004` group reference tables), `V065`–`V067` `payroll`, one table each. Aggregate exception, same as `W-26.2` | 1 — exception granted 2026-09-25 |
| Externally testable behaviour | an employee's monthly professional tax is resolved from the shared state slabs, or from the tenant's override where one exists, and resetting the override returns to the shared slabs | 1 |
| Frontend area | none — `W-47` | 1 |

---

## 1. Problem

The frozen system already has the right idea and the wrong storage.

- **The shared slabs exist only in a production database row.** Every state's slabs are one
  JSON blob in `masterConfig` under `componentName = "slabDetails"`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/statutorycomponents/ProfessionalTaxServiceImpl.java:173-181,831-843`).
  Nothing in the repository seeds it: `MasterDataInitializer.java:96-98` seeds `EPF` and
  `ESI` only. **There is no legacy source to port the slab values from**
- **The override is another JSON blob.** `OrgPTOverride.overrideJson`
  (`entity/statutorycomponents/OrgPTOverride.java:27-28`) replaces the whole state entry
  (`ProfessionalTaxServiceImpl.java:870`); `PTHistory` stores `oldJson` and `newJson`
  (`PTHistory.java:27-33`)
- **Slab money is `Double`** — `SlabDetail.java:15-17`, `TaxSlabDetailHistory.java:18-24`
- **The per-organisation copy tables are dead.** `professionalTax`, `slabRateConfiguration`,
  `slabDetail` are written by `createDefaultTax` (`:159-232`) and never read by the pay run,
  which calls `getAllProfessionalTaxes` (`EmployeePayRunServiceImpl.java:830`) — master plus
  override. Two of the three `resetToDefaultSlabs` bodies are commented out (DEBT-024)
- **The pay run uses today, not the pay period.** Effective dates and deduction months are
  compared with `LocalDate.now()` (`EmployeePayRunServiceImpl.java:844-850,872`), so a run
  computed in March for February applies March's rule
- **A female exemption is hard-coded.** `"female"` and `< 25000` at `:796-800`, an
  organisation-wide rule beside the per-slab `isFemaleExempted` flag it duplicates

The target already names the fix: *shared base in `reference`, per-tenant overrides in
`payroll`* (`docs/target-state/02-data-model.md:32-35`).

## 2. Scope

**In scope**

- `reference.pt_state` and `reference.pt_slab` — the national base, no tenant column,
  seeded for the 21 states the frozen system supports (`util/ProfessionalTaxUtil.java:9-14`)
- `payroll.org_pt_override`, `payroll.org_pt_override_slab`, `payroll.pt_history` — a
  tenant's own slabs for a state, and every change to them
- Read the tenant's states with their effective slabs and source; set an override; reset
  to the shared slabs
- `ProfessionalTaxService.resolve(...)` — the one place a gross and a month become a PT
  amount; `W-31.4` calls it

**Out of scope**

- Deducting the amount — `W-31.4`
- EPF, ESI — `W-31.1`, `W-31.3`
- Annual statutory update of the seed — the process in `V005__reference_tax_seed.sql:1-14`, one new script per change
- Screens — `W-47`

## 3. Flow

```
[payroll officer] --> GET /settings/professional-tax --> permission payroll.salary.read
   --> states = distinct state_code of core WorkLocationService.list(activeOnly=true)   (core, W-14.1)
   --> for each state: override row for (tenant, state)? its slabs, source=OVERRIDE : reference slabs in force today, source=REFERENCE
       a state with no reference rows and no override --> source=NONE, "no professional tax"

[payroll officer] --> PUT /settings/professional-tax/{stateCode} --> permission payroll.settings.manage
   --> validate slabs (§4) --> replace the override header and its slab rows in one transaction
   --> pt_history row: operation OVERRIDE_SET, before = previous effective slabs, after = new

[payroll officer] --> DELETE /settings/professional-tax/{stateCode}/override
   --> delete header and slabs --> pt_history row OVERRIDE_RESET --> 204

W-31.4 --> ProfessionalTaxService.resolve(tenantId, stateCode, grossForPt, gender, periodEnd)
   --> slabs = override in force on periodEnd, else reference rows with effective_from <= periodEnd < effective_to
   --> keep slabs whose deduction_months is null or contains periodEnd.month
   --> first slab with from_amount <= gross and (to_amount is null or gross <= to_amount)
   --> female and slab.is_female_exempt --> ZERO, else slab.amount
```

Ported from `EmployeePayRunServiceImpl.java:868-940` with three changes: the date is the
period end, not today; the female rule is the slab flag only; the source is a table, not
JSON.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/`.

| Layer | File | Change |
|---|---|---|
| Entity | `PtState.java`, `PtSlab.java` | new, `@Table(schema = "reference")`, read-only (`@Immutable`) |
| Entity | `OrgPtOverride.java`, `OrgPtOverrideSlab.java`, `PtHistory.java` | new, `@Table(schema = "payroll")`, `UUID` ids |
| Repository | five | new; the three `payroll` finders take `tenantId`; `PtSlabRepository.inForce(stateCode, date)` |
| Service / ServiceImpl | `ProfessionalTaxService`, `ProfessionalTaxServiceImpl` | new — `statesForTenant()`, `setOverride(...)`, `resetOverride(...)`, `resolve(...)` |
| Service | `PtSlabMatcher.java` | new — the pure function in §3, `Money` in, `Money` out; no repository |
| Controller | `ProfessionalTaxController.java` | new |
| DTO | `PtStateResponse` (state, source, registration_number, effective_from, slabs[]), `PtSlabDto`, `PtOverrideRequest` | new; `status` / `message` / `data` envelope |
| Enumeration | `PtSource` (`REFERENCE`, `OVERRIDE`, `NONE`), `PtHistoryOperation` (`OVERRIDE_SET`, `OVERRIDE_RESET`) | new |

The state list comes from `core` `WorkLocationService.list(true)` (`OrgMasterService.java:38`)
and `WorkLocationResponse.stateCode` (`:21`) — `payroll` may depend on `core`
(`payroll/pom.xml:19`). Legacy's fallback to the organisation's own state
(`ProfessionalTaxServiceImpl.java:815-819`) is dropped: a tenant with no active work
location has no state to file in.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/settings/professional-tax` | — | one `PtStateResponse` per state of the tenant's active work locations | `payroll.salary.read` |
| GET | `/api/v1/payroll/settings/professional-tax/{stateCode}` | — | that state; `404` if no active work location is in it | `payroll.salary.read` |
| PUT | `/api/v1/payroll/settings/professional-tax/{stateCode}` | `registration_number`, `effective_from`, `slabs[]` of `{from_amount, to_amount, amount, is_female_exempt, deduction_months[]}` | `200` the state, `source=OVERRIDE` | `payroll.settings.manage` |
| DELETE | `/api/v1/payroll/settings/professional-tax/{stateCode}/override` | — | `204`; `404` if none | `payroll.settings.manage` |
| GET | `/api/v1/payroll/settings/professional-tax/{stateCode}/history` | — | `pt_history` rows, newest first | `payroll.salary.read` |

Validation, all `400`: `stateCode` exists in `reference.state`; at least one slab; slabs
sorted by `from_amount`, contiguous, non-overlapping, only the last may have `to_amount`
null; `amount >= 0`; `deduction_months` values `1..12`; `effective_from` not null.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V064__pt_state_and_slab.sql` | `reference.pt_state`, `reference.pt_slab`, and the seed | no — shared national data, by design | additive |
| `payroll/V065__org_pt_override.sql` | `payroll.org_pt_override` | yes | additive |
| `payroll/V066__org_pt_override_slab.sql` | `payroll.org_pt_override_slab` | yes | additive |
| `payroll/V067__pt_history.sql` | `payroll.pt_history` | yes | additive |

**`reference.pt_state`** — one row per state that levies professional tax:
`state_code varchar(10) PK REFERENCES reference.state(code)` ·
`levies_pt boolean NOT NULL` · `annual_ceiling numeric(19,4)` — the constitutional cap, 2,500 ·
`notes varchar(200)`.

**`reference.pt_slab`** — the base slabs, effective-dated:
`id bigserial PK` · `state_code varchar(10) NOT NULL REFERENCES reference.pt_state` ·
`effective_from date NOT NULL` · `effective_to date` ·
`from_amount numeric(19,4) NOT NULL` · `to_amount numeric(19,4)` — null is open-ended ·
`amount numeric(19,4) NOT NULL` · `is_female_exempt boolean NOT NULL DEFAULT false` ·
`deduction_months varchar(32)` — comma-separated month numbers, null means every month; Tamil
Nadu's half-yearly levy is `'3,9'`, a February-only balancing slab is `'2'` ·
`sort_order smallint NOT NULL` ·
`idx_pt_slab_state_effective (state_code, effective_from, sort_order)`.

**The seed.** `V064` inserts `pt_state` for all rows of `reference.state`, `levies_pt=true`
for the 21 states in `ProfessionalTaxUtil.java:9-14`, and the current slabs of each of
those 21 from the state Acts, `effective_from` the date of the latest amendment in force
on 2026-04-01. As `V005` says of the tax slabs (`V005__reference_tax_seed.sql:3-9`): there is
nothing to copy, every value is written from the law, and **every state's slabs are
asserted in `PtReferenceSeedIT`** — an unasserted row is a number nobody checked.

**`org_pt_override`**, from `OrgPTOverride.java:21-33`: `tenant_id` · `state_code varchar(10) NOT NULL REFERENCES reference.pt_state` ·
`registration_number varchar(32)` · `effective_from date NOT NULL` · four audit columns ·
`uk_org_pt_override_tenant_state (tenant_id, state_code)` unique.

**`org_pt_override_slab`**, the structured form of `overrideJson` (`:28`): `tenant_id` ·
`override_id uuid NOT NULL REFERENCES payroll.org_pt_override` · the six slab columns of
`reference.pt_slab` from `from_amount` to `sort_order` ·
`idx_org_pt_override_slab_tenant_override (tenant_id, override_id, sort_order)`.

**`pt_history`**, from `PTHistory.java:21-39`: `tenant_id` · `state_code varchar(10) NOT NULL` ·
`operation varchar(16) NOT NULL CHECK (operation IN ('OVERRIDE_SET','OVERRIDE_RESET'))` ·
`before_slabs jsonb` · `after_slabs jsonb` — snapshots of the slab rows, the one place JSON is
right: a history row is a record, never queried by field ·
`changed_at timestamptz NOT NULL` · `changed_by uuid NOT NULL` ·
`idx_pt_history_tenant_state_changed (tenant_id, state_code, changed_at DESC)`.

- [x] `tenant_id` on the three `payroll` tables, leading index column; none on `reference` (`migration/README.md` §tenant_id)
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — listed above
- [x] Money `numeric(19,4)`; nothing floating — DEBT-035 fixed
- [x] Expand / contract — five new tables

RLS and `tenant_isolation` in the exact `CASE` form in `V065`–`V067`. `reference` tables get
none, as `V003`–`V005`.

`02-data-model.md` §4 lists `professional_tax`, `slab_rate_configuration`, `slab_detail`
under `payroll` (`:169`). They are not built: the three legacy tables are the dead per-org
copy (§1). §5 gains `pt_state` and `pt_slab`; §4 gains `org_pt_override_slab`. The data
model is updated when this spec is committed — founder decision 1, 2026-09-25.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../statutory/pt/PtSlabMatcherTest.java` | the worked table in §8; open-ended last slab; `deduction_months` `'3,9'` pays in March and September only; female exempt on a flagged slab, not on an unflagged one; gross exactly on a boundary lands in the lower slab |
| Unit | `payroll/.../statutory/pt/PtOverrideValidationTest.java` | overlapping slabs refused; gap refused; `to_amount` null before the last refused; unknown state refused |
| Integration | `payroll/.../statutory/pt/PtReferenceSeedIT.java` | one assertion per seeded state: a named gross on a named date resolves to the amount in the Act (21 rows) |
| Integration | `payroll/.../statutory/pt/ProfessionalTaxOverrideIT.java` | **the acceptance test**: tenant A with a Karnataka work location: GET shows `REFERENCE`; PUT an override with a different amount: `resolve` returns the override amount; `pt_history` has `OVERRIDE_SET` with before and after; DELETE: `resolve` returns the reference amount again, `OVERRIDE_RESET` written; `reference.pt_slab` row count unchanged throughout |
| Integration | `payroll/.../statutory/pt/ProfessionalTaxRlsIT.java` | as `app_user`, tenant B's `resolve` for the same state ignores A's override; B cannot read A's history |
| Integration | `payroll/.../statutory/pt/ProfessionalTaxStatesIT.java` | states listed are exactly the active work locations' states; an inactive location's state is absent; a state with `levies_pt=false` shows `NONE` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

Worked table for `PtSlabMatcherTest`, Karnataka slabs as seeded (monthly, from 2024-04-01):

| Gross | Month | Gender | Amount |
|---|---|---|---|
| 24,999.0000 | July | any | 0 |
| 25,000.0000 | July | male | 200 |
| 60,000.0000 | February | female | 200 — no exemption on the Karnataka rows |

And a Maharashtra check: 24,000 female → 0 (`is_female_exempt`), 24,000 male → 175,
15,000 male → 0, 30,000 male in February → 300, in March → 200.

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT state_code, count(*) FROM reference.pt_slab GROUP BY 1 ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class WHERE relname IN ('org_pt_override','org_pt_override_slab','pt_history','pt_slab');"
cd code/backend && mvn -q verify
grep -rn "LocalDate.now()" code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt | wc -l
```

| Check | Expected |
|---|---|
| Seed | 21 states, each with at least one row |
| RLS | `t` for the three `payroll` tables, `f` for `pt_slab` |
| Suite | green, no skips; `PtReferenceSeedIT` runs 21 assertions |
| `LocalDate.now()` in the resolver | `0` — the date is always the caller's |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Seeded slab values are wrong, and nobody notices until a payslip | **high** — 21 Acts, no legacy source | One assertion per state in `PtReferenceSeedIT`, each citing the Act and section in a comment; the founder reads that file, not the SQL |
| The override replaces one slab instead of the state's slab set, and the two mix | medium | PUT replaces the whole set in one transaction; the matcher reads one source, never both |
| `resolve` uses today's date | medium | The grep in §8; `PtSlabMatcherTest` passes a February date in July |
| A tenant edits the reference slabs through the API | low | `reference` entities are `@Immutable`; no endpoint writes them |

## 10. Rollback

Nothing is deployed. All scripts are additive. A wrong seed value is corrected by a new
`V0NN` script that closes the row with `effective_to` and inserts the right one — the
`V005` rule, never an edit.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | the three `payroll` tables |
| Flyway only, `ddl-auto` nowhere | `V064`–`V067` |
| `Money`/`BigDecimal` for money | `numeric(19,4)`; `Money` in the matcher |
| Index on `tenant_id` plus lookup columns | four indexes, §6 |
| Expand / contract | new tables only |
| No module references another module | `payroll`, `core` (work locations), `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-024 duplicate PT service methods | **Discounted** — that class is not ported; only `:868-940` is |
| DEBT-035, DEBT-036, DEBT-037 (proposed) | **Fixed** here |
| DEBT-018, DEBT-022 | **Honoured**, **fixed** |

**Proposed GAP entries** — not yet in `legacy/docs/GAP_INVENTORY.md`, evidence in §1:

| ID | Category | Finding |
|---|---|---|
| DEBT-035 | Payroll Backend | PT slab amounts and ranges are `Double` (`SlabDetail.java:15-17`, `TaxSlabDetailHistory.java:18-24`) |
| DEBT-036 | Payroll Backend | The PT slab master for every state is a hand-inserted JSON row in `masterConfig` (`ProfessionalTaxServiceImpl.java:173-181`); no seed, script or resource in the repository holds it |
| DEBT-037 | Payroll / Pay run | PT effective dates and deduction months are compared with `LocalDate.now()` (`EmployeePayRunServiceImpl.java:844-850,872`), and a female exemption below 25,000 is hard-coded (`:796-800`) beside the per-slab flag |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Slabs per tenant in `payroll`, as `02-data-model.md:169`, or shared in `reference` with overrides? | **Shared base in `reference`, overrides in `payroll`** — founder, 2026-09-25. It is what `02-data-model.md:32-35` already prescribes and what the live legacy path does (`:831-870`); the per-org copy tables are dead |
| 2 | Override as JSON, as legacy? | **Rows.** `org_pt_override_slab` is one more table than the data model lists; a JSON blob is a `Double`-in-disguise. `pt_history` keeps JSON snapshots because a history row is never queried by field |
| 3 | Female exemption and deduction months? | **Kept**, as slab columns — founder, 2026-09-25. Legacy's org-wide 25,000 rule is dropped; Maharashtra's exemption is a flagged slab |
| 4 | States from work locations or from a tenant setting? | **Work locations**, as legacy (`:804-813`), via `core` `WorkLocationService.list(true)`. A location's `state_code` is the filing key (`V013__work_location.sql:24-26`) |
