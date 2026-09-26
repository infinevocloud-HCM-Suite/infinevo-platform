# Feature: Provident fund and state insurance settings

| Field | Value |
|---|---|
| **Feature ID** | `W-31.1` · from ticket #38 (`W-31`) · `PAY-08` part 1 of 4 |
| **Promoted to** | `docs/target-state/features/W-31-1-epf-esi-settings.md` on the developer's `dev-<name>` branch — **`W-31-1` with hyphens**, never `W-31.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | DEBT-034 (proposed below, fixed here), DEBT-008, DEBT-018, DEBT-022 |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing — `W-07` tenant model is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | 2 scripts, one table each — `V062` EPF, `V063` ESI. Aggregate exception, same as `W-26.2` | 1 — exception granted 2026-09-25 |
| Externally testable behaviour | a tenant saves its provident fund and state insurance rates once, and reads them back as numbers | 1 |
| Frontend area | none — `W-47` | 1 |

`W-31` was split on 2026-09-25 into `W-31.1` (this), `W-31.2` professional tax, `W-31.3`
employee EPF and ESI lines, `W-31.4` the pay-run contributor.

---

## 1. Problem

The frozen Payroll backend has one EPF row and one ESI row per organisation. The shape
is wrong in three ways.

- **Every rate is text.** `epfEmployeeContribution`, `epfEmployerContribution`,
  `epsEmployerContribution`, `edliEmployerContribution` and the admin charge are `String`
  holding values like `"12.00%"` and `"NA"` —
  `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/statutorycomponents/Epf.java:20,25-26,33-34,37,42,45,48-49`;
  ESI the same at `Esi.java:17-18`. Nothing downstream can multiply by them
- **The defaults live only in a running database.** `MasterDataInitializer.java:36-96`
  seeds an `EPF` and an `ESI` JSON blob into `masterConfig` at first boot, and the
  services read the blob when a tenant has no row (`EpfServiceImpl.java:52`,
  `EsiServiceImpl.java:47`)
- **The wage ceiling is a browser constant.** `15000` appears only in the settings
  screen (`legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/statutoryComponents/editEPF.js:126-127,191-201`);
  the backend never stores it, so the pay run cannot apply it
- Twelve of the 35 columns are display copies or UI state: `deductionCycleFormatted`,
  `registrationDateFormatted`, `name`, `isAssociatedWithEmployee`, `canOverrideRestrictedBasic`
  and the `canEnable*` flags (`Epf.java:27,29,32,38,41,43,46`)

## 2. Scope

**In scope**

- `payroll.epf_setting` and `payroll.esi_setting`, one row per tenant, numeric rates and
  ceilings, ported from `Epf.java` and `Esi.java` with the fixes above
- Read, upsert; statutory defaults returned when a tenant has no row, without writing one
- A read-side service the other three parts call: `StatutorySettingsService`

**Out of scope**

- Professional tax — `W-31.2`
- Deriving an employee's PF and ESI amounts — `W-31.3`
- Deducting anything — `W-31.4`
- Screens — `W-47`

## 3. Flow

```
[payroll officer] --> GET  /settings/epf  --> permission payroll.salary.read
                                          --> row for tenant, else the defaults in §6 with is_enabled=false (no write)
[payroll officer] --> PUT  /settings/epf  --> permission payroll.settings.manage
                                          --> validate (§4) --> insert or update the one row --> 200
W-31.3 / W-31.4   --> StatutorySettingsService.epf(tenantId) / esi(tenantId)  (defaults if no row)
```

Legacy's `POST /disable` (`EpfController.java:51`, `EsiController.java:53`) is `is_enabled=false`
on the PUT. Two endpoints per component, not three.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/settings/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EpfSetting.java`, `EsiSetting.java` | new, `@Table(schema = "payroll")`, `UUID` ids, `@Audited` |
| Repository | `EpfSettingRepository`, `EsiSettingRepository` | new; `findByTenantId` only |
| Service / ServiceImpl | `StatutorySettingsService`, `StatutorySettingsServiceImpl` | new — `epf(tenantId)`, `esi(tenantId)`, `saveEpf(...)`, `saveEsi(...)` |
| Controller | `StatutorySettingsController.java` | new |
| DTO | `EpfSettingRequest/Response`, `EsiSettingRequest/Response` | new; `status` / `message` / `data` envelope |
| Enumeration | `DeductionCycle` (`MONTHLY`) | new — legacy stores `"monthly"` and nothing else (`MasterDataInitializer.java:68,91`) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/settings/epf` | — | the row, or defaults with `is_enabled=false` and `source=DEFAULT` | `payroll.salary.read` |
| PUT | `/api/v1/payroll/settings/epf` | every column in §6 except audit | `200` upserted row | `payroll.settings.manage` |
| GET | `/api/v1/payroll/settings/esi` | — | as above | `payroll.salary.read` |
| PUT | `/api/v1/payroll/settings/esi` | every column in §6 except audit | `200` | `payroll.settings.manage` |

Permission codes exist: `payroll.settings.manage` (`reference/V020__action.sql`),
`payroll.salary.read` (`:113`).

Validation, all `400`: every rate `0 <= r <= 100` at scale 4; `eps_rate <= employer_rate`;
`wage_ceiling > 0`; `eps_senior_age` between 50 and 70; `registration_number` at most 32
characters, may be blank while `is_enabled=false`, required when `true`.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V062__epf_setting.sql` | `payroll.epf_setting` | yes | additive |
| `payroll/V063__esi_setting.sql` | `payroll.esi_setting` | yes | additive |

`V062`–`V069` extend the payroll lane's block for `W-31` (`DEV-TRACKER.md` § lanes).

**Common:** `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` · four audit columns ·
`uk_<table>_tenant (tenant_id)` unique — one row per tenant.

**`epf_setting`**, from `Epf.java:16-49` and the defaults at `MasterDataInitializer.java:36-71`:

| Column | Type | Default | Legacy |
|---|---|---|---|
| `is_enabled` | `boolean NOT NULL` | `false` | `isActive` (`:31`) |
| `registration_number` | `varchar(32)` | | `registrationNumber` (`:19`) |
| `registration_date` | `date` | | `registrationDate` (`:40`) |
| `deduction_cycle` | `varchar(16) NOT NULL` | `'MONTHLY'` | `deductionCycle` (`:47`) |
| `employee_rate` | `numeric(7,4) NOT NULL` | `12.0000` | `epfEmployeeContribution` `"12.00%"` (`:26`) |
| `employer_rate` | `numeric(7,4) NOT NULL` | `12.0000` | `epfEmployerContribution` 3.67 + `epsEmployerContribution` 8.33 (`:45`, `:37`) |
| `eps_rate` | `numeric(7,4) NOT NULL` | `8.3300` | `epsEmployerContribution` (`:37`); the EPF share is `employer_rate − eps_rate` |
| `edli_rate` | `numeric(7,4) NOT NULL` | `0.5000` | `edliEmployerContribution` (`:25`) |
| `admin_charge_rate` | `numeric(7,4) NOT NULL` | `0.5000` | `epfAdminChargesEmployerContribution` (`:20`) |
| `wage_ceiling` | `numeric(19,4) NOT NULL` | `15000.0000` | browser constant, `editEPF.js:126` |
| `restrict_employee_to_ceiling` | `boolean NOT NULL` | `false` | `isEmployeeRestrictedBasicEnabled` (`:22`) |
| `restrict_employer_to_ceiling` | `boolean NOT NULL` | `false` | `isEmployerRestrictedBasicEnabled` (`:44`) |
| `prorate_restricted_wage` | `boolean NOT NULL` | `false` | `canProRateRestrictedBasic` (`:28`) |
| `consider_earned_wage` | `boolean NOT NULL` | `true` | `considerEarnedSalaryForEpf` (`:18`) |
| `eps_senior_age` | `smallint NOT NULL` | `58` | `epsSeniorCategoryAge` (`:30`) |
| `include_employer_in_ctc` | `boolean NOT NULL` | `false` | `isEmployerContributionIncludedCtc` (`:21`) |
| `include_edli_admin_in_ctc` | `boolean NOT NULL` | `false` | `isEdliIncludedCtc`, `isAdminChargesIncludedCtc` (`:16-17`), always set together by the screen |
| `include_employer_in_structure` | `boolean NOT NULL` | `false` | `isEmployerContributionIncludedSalaryStructure` (`:23`) |
| `include_edli_admin_in_structure` | `boolean NOT NULL` | `false` | `isEdliIncludedSalaryStructure`, `isAdminChargesIncludedSalaryStructure` (`:35-36`) |
| `abry_scheme` | `boolean NOT NULL` | `false` | `isEligibleForAbryScheme`, `isSubsidyApplicableForBothContributions` (`:24`, `:39`) |

**Deliberately absent:** the senior-category rates (`:42`, `:48`). Past `eps_senior_age`
the rule is fixed — EPS `0`, the whole `employer_rate` to EPF — and `W-31.3` applies it.
The `*Formatted`, `name`, `isAssociatedWithEmployee`, `canOverrideRestrictedBasic` and
`canEnable*` columns are screen state and are dropped.

**`esi_setting`**, from `Esi.java:16-28` and `MasterDataInitializer.java:77-91`:
`is_enabled boolean NOT NULL DEFAULT false` · `registration_number varchar(32)` ·
`registration_date date` · `deduction_cycle varchar(16) NOT NULL DEFAULT 'MONTHLY'` ·
`employee_rate numeric(7,4) NOT NULL DEFAULT 0.7500` (`:17`) ·
`employer_rate numeric(7,4) NOT NULL DEFAULT 3.2500` (`:18`) ·
`wage_ceiling numeric(19,4) NOT NULL DEFAULT 21000.0000` — the statutory gross limit, which
legacy never stored (the browser tested `annualCTC <= 200000`, `salaryDetails.js:262`) ·
`include_employer_in_ctc boolean NOT NULL DEFAULT false` (`:26`) ·
`include_in_structure boolean NOT NULL DEFAULT false` (`:22`).

- [x] `tenant_id` on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — the unique `(tenant_id)` is the only lookup
- [x] Rates `numeric(7,4)`, money `numeric(19,4)`; nothing floating, nothing text
- [x] Expand / contract — two new tables, nothing dropped

RLS and `tenant_isolation` in the exact `CASE` form in both scripts — `migration/README.md`
§row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../statutory/settings/StatutorySettingsValidationTest.java` | rate above 100 refused; `eps_rate > employer_rate` refused; `is_enabled=true` with blank registration refused; the defaults in §6 are what `epf()` returns for a tenant with no row |
| Integration | `payroll/.../statutory/settings/StatutorySettingsIT.java` | **the acceptance test**: GET with no row returns defaults and `source=DEFAULT` and writes nothing; PUT creates; second PUT updates the same row (count stays 1); GET returns the numbers as numbers |
| Integration | `payroll/.../statutory/settings/StatutorySettingsRlsIT.java` | as `app_user`, tenant A reads tenant B's settings as absent, not as B's row |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class WHERE oid IN ('payroll.epf_setting'::regclass,'payroll.esi_setting'::regclass);"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT table_name, column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name IN ('epf_setting','esi_setting')
      AND (column_name LIKE '%rate%' OR column_name='wage_ceiling') ORDER BY 1,2;"
cd code/backend && mvn -q verify
grep -rn "String .*[Rr]ate\|double\|float" code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/settings | wc -l
```

| Check | Expected |
|---|---|
| RLS | `t`, `t` |
| Rate columns | every row `numeric`; no `character varying`, no `double precision` |
| Suite | green, no skips |
| Text or floating rates | `0` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Rates ported as text "because the DTO had `%`" | medium | The grep in §8; `StatutorySettingsValidationTest` multiplies by the rate |
| GET writes a defaults row and the tenant "has settings" it never saved | medium | `StatutorySettingsIT` asserts zero rows after GET |
| A third table sneaks in for the defaults, as legacy's `masterConfig` | low | Defaults are column `DEFAULT`s plus one constant in the service; no table |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables |
| Flyway only, `ddl-auto` nowhere | `V062`, `V063` |
| `Money`/`BigDecimal` for money | `numeric(7,4)` rates, `numeric(19,4)` ceilings; `BigDecimal` in Java |
| Index on `tenant_id` plus lookup columns | the unique `(tenant_id)` on each |
| Expand / contract | new tables only |
| No module references another module | `payroll` and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-034 rates as `String` (proposed) | **Fixed** for these two tables |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId` |
| DEBT-002 `ddl-auto` | **Discounted** — Flyway |

**Proposed GAP entry** — not yet in `legacy/docs/GAP_INVENTORY.md`:

| ID | Category | Finding |
|---|---|---|
| DEBT-034 | Payroll Backend | EPF and ESI contribution rates stored as `String` with a `%` sign (`Epf.java:20,25-26,33-34,37,42,45,48-49`, `Esi.java:17-18`); the wage ceiling exists only as `15000` in `editEPF.js:126` |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | One combined `statutory_setting` row, or two tables as the data model lists? | **Two**, `02-data-model.md:169` names `epf` and `esi`; they are enabled and registered separately |
| 2 | Store `employer_rate` and `eps_rate`, or the EPF and EPS shares separately as legacy? | **Total and EPS.** The EPF share is a subtraction; storing both invites a row where they do not add up |
| 3 | Senior-category rates as columns? | **No.** Fixed rule, applied in `W-31.3` from `eps_senior_age` |
