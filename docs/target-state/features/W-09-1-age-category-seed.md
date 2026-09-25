# Feature: Tax slab seed — senior and super-senior age categories

| Field | Value |
|---|---|
| **Feature ID** | `W-09.1` · from ticket `W-09` |
| **Promoted to** | `docs/target-state/features/W-09-1-age-category-seed.md` on branch `W-09-1-age-category-seed` — **`W-09-1` with hyphens**, never `W-09.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/migration` |
| **Related gaps** | none — completes `W-09`'s own seed |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing — `W-09` is on main |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `migration` | 1 |
| Flyway migration | 1 script, rows only | 1 |
| Externally testable behaviour | `reference.tax_slab_master` holds an `OLD` header for `SENIOR` and `SUPER_SENIOR` in each seeded year, with the right nil band | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

`V005` seeds slab headers for the general age category only and says so:
"SENIOR and SUPER_SENIOR are not seeded" (`V005__reference_tax_seed.sql:17-19`). The table was
built to hold them — `age_category ... CHECK (age_category IN ('GENERAL','SENIOR','SUPER_SENIOR'))`
(`V004__reference_tax_masters.sql:24`), unique on `(regime, financial_year, age_category)` (`:31`).

What the table holds today (`V005:22-29`, `:45-56`):

| Rows | Value |
|---|---|
| Headers | 6 — `OLD` and `NEW` for FY 2023-24, 2024-25, 2025-26, all `age_category = 'GENERAL'` |
| Old-regime brackets, every year | `0–2,50,000 @ 0` · `2,50,000–5,00,000 @ 5` · `5,00,000–10,00,000 @ 20` · `10,00,000– @ 30` |
| New-regime brackets | year-specific; no age variant exists in law |

A resident aged 60–79 has a basic exemption of 3,00,000 and one aged 80+ of 5,00,000 under the
old regime, unchanged across the three seeded years. Without those rows the calculator `W-33`
builds would over-tax every senior on the old regime. Legacy has nothing to port: its
`TaxSlabMaster` had no age column at all (`W-09-reference-schema-and-seed.md:158`), and no
Java in `code/` reads the table yet (only `ReferenceSchemaIT` does).

## 2. Scope

**In scope**

- Six headers: `OLD` × {FY 2023-24, 2024-25, 2025-26} × {`SENIOR`, `SUPER_SENIOR`}
- Their brackets and a version-1 history row each
- The `ReferenceSchemaIT` counts and helper that assume one header per `(year, regime)`

**Out of scope**

- `NEW` regime age rows — section 115BAC has one slab table for all ages; a row would assert a rule that does not exist (the same reasoning `V005:122-123` applies to HRA)
- 80TTB, 80D senior ceilings — already seeded or `W-33`'s arithmetic (`V005:178`, `:205-207`)
- How the calculator picks the category from date of birth — `W-33`

## 3. Flow

```
Flyway (migration_user) --> V027: INSERT 6 headers --> INSERT 21 brackets (join on year, regime, age)
                        --> INSERT 6 history rows (version 1)
```

## 4. Backend changes

None in Java. Test changes only:

| File | Change |
|---|---|
| `migration/src/test/.../ReferenceSchemaIT.java:210-216` | header count for the three years `6` → `12` |
| `ReferenceSchemaIT.java:259` | version-1 history rows `6` → `12` |
| `ReferenceSchemaIT.java:463-467` | `brackets(conn, fy, regime)` joins on year and regime only; after this script an `OLD` query returns three headers' rows mixed. Add an `ageCategory` parameter (`AND m.age_category = ?`), pass `GENERAL` at the existing call sites (`:219-255`), and add the two assertions in §7 |

**API contract** — none; `reference` is read-only to `app_user` and has no endpoint.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V027__tax_slab_age_categories.sql` — **reserved 2026-09-25**; `W-11.3` holds `V025`, `W-13.4` holds `V026` | `reference.tax_slab_master`, `reference.tax_slab_detail_history`, `reference.tax_slab_master_history` (rows only) | **no** — `reference` holds national data (`README.md:52-56`, `D-08`); CI gate "no tenant_id in the reference schema" (`ci.yml:351`) | forward-only |

```sql
INSERT INTO reference.tax_slab_master
    (regime, financial_year, assessment_year, age_category, effective_from, effective_to, description) VALUES
    ('OLD', '2023-2024', '2024-2025', 'SENIOR',       DATE '2023-04-01', DATE '2024-03-31', 'Old regime, FY 2023-24, resident 60-79'),
    ('OLD', '2023-2024', '2024-2025', 'SUPER_SENIOR', DATE '2023-04-01', DATE '2024-03-31', 'Old regime, FY 2023-24, resident 80+'),
    -- same pair for '2024-2025'/'2025-2026' (effective_to 2025-03-31) and '2025-2026'/'2026-2027' (effective_to NULL),
    -- dates as V005:26-28
    ;

INSERT INTO reference.tax_slab_detail_history (slab_master_id, from_amount, to_amount, tax_rate_percent, slab_order)
SELECT m.id, v.from_amount, v.to_amount, v.rate, v.ord
FROM reference.tax_slab_master m
JOIN (VALUES
    -- SENIOR: nil to 3,00,000 — per year, 4 bands
    ('SENIOR',       1,       0.0000,   300000.0000,  0.00),
    ('SENIOR',       2,  300000.0000,   500000.0000,  5.00),
    ('SENIOR',       3,  500000.0000,  1000000.0000, 20.00),
    ('SENIOR',       4, 1000000.0000,  NULL,         30.00),
    -- SUPER_SENIOR: nil to 5,00,000 — per year, 3 bands
    ('SUPER_SENIOR', 1,       0.0000,   500000.0000,  0.00),
    ('SUPER_SENIOR', 2,  500000.0000,  1000000.0000, 20.00),
    ('SUPER_SENIOR', 3, 1000000.0000,  NULL,         30.00)
) AS v(age, ord, from_amount, to_amount, rate) ON m.age_category = v.age
WHERE m.regime = 'OLD' AND m.financial_year IN ('2023-2024', '2024-2025', '2025-2026');

INSERT INTO reference.tax_slab_master_history (master_id, version, change_reason, action_type, changed_by, valid_from)
SELECT m.id, 1, 'Senior and super-senior old-regime slabs (W-09.1)', 'INSERT', 'system', m.effective_from
FROM reference.tax_slab_master m
WHERE m.age_category IN ('SENIOR', 'SUPER_SENIOR');
```

Columns and the join-by-value form follow `V005:22-23,40-44,89-91`. `NUMERIC(19,4)` amounts,
`NUMERIC(5,2)` rates (`V004:55-57`). 21 bracket rows: 3 years × (4 + 3).

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | no new table; `reference` is the designed exception |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| Money as `Money` / `BigDecimal` | `NUMERIC(19,4)` (`README.md:144`) |
| Index on `tenant_id` plus lookup columns | n/a in `reference`; `idx_tax_slab_master_fy` (`V004:34`) serves the lookup |
| Expand / contract | rows added; nothing changed or removed. `V005` is never edited (`V005:11-14`) |
| No module references another | `migration` only |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `migration/.../ReferenceSchemaIT.java` (extend §4 rows) | `brackets(conn, fy, "OLD", "SENIOR")` = `0|300000|0.00, 300000|500000|5.00, 500000|1000000|20.00, 1000000|-|30.00` for each year; `SUPER_SENIOR` = `0|500000|0.00, 500000|1000000|20.00, 1000000|-|30.00`; no `NEW` header with a non-`GENERAL` category; `GENERAL` assertions unchanged |
| Integration | `ReferenceSchemaIT.annualUpdateSimulation` (`:283-313`) | still passes — it counts FY 2026-27 only |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT regime, age_category, count(*) FROM reference.tax_slab_master GROUP BY 1,2 ORDER BY 1,2;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT m.financial_year, m.age_category, d.to_amount FROM reference.tax_slab_detail_history d JOIN reference.tax_slab_master m ON m.id = d.slab_master_id WHERE m.regime='OLD' AND d.slab_order=1 ORDER BY 1,2;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Headers by regime and category | `NEW GENERAL 3` · `OLD GENERAL 3` · `OLD SENIOR 3` · `OLD SUPER_SENIOR 3` |
| First band ceiling, `OLD` | per year: `GENERAL 250000.0000` · `SENIOR 300000.0000` · `SUPER_SENIOR 500000.0000` |
| Suite | green, no skips; `ReferenceSchemaIT` runs |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A reader joins the master on `(financial_year, regime)` alone and gets three rows | medium — `ReferenceSchemaIT:463-467` did exactly that | The helper fix in §4 is the worked example; `W-33` selects by `age_category` |
| `W-11.3` / `W-13.4` take the same version number | medium | Flyway fails loudly on a duplicate (`README.md:30`); renumber at rebase |
| Future annual scripts forget the age rows | medium | `annualUpdateSimulation` fixture (`db/migration-annual/reference`) gains the pair when `W-33` first reads them |

## 10. Rollback

Revert the test commit. The rows stay; Flyway is forward-only. The unique key
(`V004:31`) means a re-run of the script cannot duplicate them.
