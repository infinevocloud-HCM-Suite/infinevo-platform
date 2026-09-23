# Feature: Holiday calendar

| Field | Value |
|---|---|
| **Feature ID** | `W-17` · ticket #21 · `CORE-08` |
| **Promoted to** | `docs/target-state/features/W-17-holiday-calendar.md` on branch `W-17-holiday-calendar` |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured), DEBT-013 (discounted) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-14.1` — `core.work_location` must exist first |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | a location's holiday list for a date range is correct and tenant-isolated | 1 |
| Frontend area | none | 1 |

Within cap. `holiday` is a satellite of `holiday_calendar` and ships with it.

---

## 1. Problem

Both products have a holiday table, neither is usable, and **nothing reads either of them**.

- HRMS `Holiday.java:1-58` has four columns — `id`, `name`, `date`, `day` — and **no tenant, no organisation and no location**. Every tenant would share one list.
- Payroll `.../leaveAndAttedance/holiday/Holiday.java:1-130` is organisation-scoped — `:35-37` — and richer: date range, restricted-holiday flag, status.
- Payroll stores locations as a `Set<String>` in a join table, **not** a foreign key to `WorkLocation` — `.../holiday/Holiday.java:39-45`. A renamed location silently orphans its holidays.
- No leave service, attendance service or pay-run service references a holiday repository anywhere. Holidays are CRUD and nothing else.

That last point is the real finding. `09-build-order.md:196` says *"leave and pay both read one calendar"* — today **neither reads any calendar**, so this is not a port of a working mechanism. It is a new one with two tables of source data.

**A correction to the build order — now applied to it (2026-09-23).** It said *"Payroll models locations properly; use that."* Payroll models **work locations** properly — `WorkLocation.java:1-130`, with a real FK to the organisation at `:40-42`. It does **not** model the holiday-to-location link properly. The instruction is right about the entity and wrong about the link; this spec takes the entity and fixes the link.

## 2. Scope

**In scope**

- `core.holiday_calendar` — a named list, owned by a tenant, assigned to work locations
- `core.holiday` — one dated entry, supporting a range and the restricted-holiday flag
- A read API a consumer can ask "is this date a holiday for this location"
- CRUD for both, tenant-scoped

**Out of scope**

- Leave consuming the calendar — `W-16.2` and `W-16.4a`
- Pay working-days consuming it — `W-18.1` and `W-28`
- Bulk or regional import — no import path exists today for holidays (`EmployeeLeaveImportController.java:1-112` imports leave, not holidays), and inventing one is its own ticket
- A national holiday list in `reference` — holidays are per tenant, not national, because every employer picks its own

## 3. Flow

```
[admin] --> [HolidayCalendarController] --> [HolidayCalendarService]
   --> TenantContext bound by W-08 --> [core.holiday_calendar, core.holiday under RLS]

[leave or pay, later] --> [HolidayQueryService.isHoliday(locationId, date)] --> [core.holiday]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../holiday/HolidayCalendarController.java` | new |
| Service | `core/.../holiday/HolidayCalendarService.java` | new |
| Service | `core/.../holiday/HolidayQueryService.java` | new — the read side consumers will use |
| Entity | `core/.../holiday/HolidayCalendar.java`, `Holiday.java` | new, each `@Table(schema="core")` |
| Repository | `core/.../holiday/HolidayCalendarRepository.java`, `HolidayRepository.java` | new |
| DTO | `core/.../holiday/*Request.java`, `*Response.java` | new |

`HolidayQueryService` exists in this ticket even though nothing calls it yet. That is
deliberate: it is the seam `W-16` and `W-18` plug into, and defining it here stops each of
them growing its own.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/holiday-calendars` | name, work location ids | `201` | Bearer, tenant bound |
| GET | `/api/v1/holiday-calendars` | — | list | Bearer, tenant bound |
| PUT | `/api/v1/holiday-calendars/{id}` | name, work location ids | `200` | Bearer, tenant bound |
| POST | `/api/v1/holiday-calendars/{id}/holidays` | name, from, to, restricted | `201` | Bearer, tenant bound |
| DELETE | `/api/v1/holiday-calendars/{id}/holidays/{holidayId}` | — | `204` | Bearer, tenant bound |
| GET | `/api/v1/holidays?workLocationId=&from=&to=` | — | list for that location | Bearer, tenant bound |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__holiday_calendar.sql` | `core.holiday_calendar` | yes | additive |
| `core/V0NN__holiday.sql` | `core.holiday` | yes | additive |

Version numbers are assigned when the branch is cut; the sequence is global —
`migration/README.md:17-31`.

`holiday_calendar`: `id uuid` · `tenant_id uuid NOT NULL` · `name varchar(128) NOT NULL` ·
`is_default boolean NOT NULL DEFAULT false` · four audit columns.

`holiday`: `id uuid` · `tenant_id uuid NOT NULL` ·
`calendar_id uuid NOT NULL REFERENCES core.holiday_calendar(id)` · `name varchar(128)` ·
`from_date date NOT NULL` · `to_date date NOT NULL` · `is_restricted boolean NOT NULL DEFAULT false` ·
`description` · four audit columns.

The calendar-to-location link is a third structure, `core.holiday_calendar_location`
(`tenant_id`, `calendar_id`, `work_location_id`) — **and that makes three tables, not two.**
See decision 1: either it ships here under the aggregate exception, or `W-17` splits.

- [x] `tenant_id` on every table, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, calendar_id, from_date)` on `holiday`, `(tenant_id, work_location_id)` on the link
- [x] Money columns — none
- [x] Expand / contract — new tables only

`to_date >= from_date` is a check constraint, not application validation, because a one-day
holiday is the common case and an inverted range is silently wrong everywhere else.

Each script carries its own RLS and `tenant_isolation` policy in the exact `CASE` form —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../holiday/HolidayQueryServiceTest.java` | a date inside a range is a holiday; boundaries are inclusive; restricted holidays are returned but flagged |
| Integration | `core/.../holiday/HolidayRlsIT.java` | tenant A cannot read tenant B's calendars or holidays as `app_user` |
| Integration | `core/.../holiday/HolidayCalendarLocationIT.java` | a calendar cannot be assigned a work location belonging to another tenant |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in holiday_calendar holiday holiday_calendar_location; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "INSERT INTO core.holiday (tenant_id, calendar_id, name, from_date, to_date)
   VALUES (gen_random_uuid(), gen_random_uuid(), 'bad', '2026-05-02', '2026-05-01');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all three | `t` three times |
| Inverted range insert | rejected by the check constraint, not accepted |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Locations are copied as strings because the legacy model did (`Holiday.java:39-45`) | **medium — the legacy shape is the trap here** | The link table has a real FK; `HolidayCalendarLocationIT` asserts the cross-tenant case |
| `HolidayQueryService` is written to no consumer and turns out wrong when `W-16` arrives | medium | Keep it to one question — is this date a holiday for this location — and no calculation |
| A tenant with no calendar makes every day a working day, silently | medium | `is_default` on one calendar per tenant; `W-12.1` tenant creation seeds an empty default |
| Restricted holidays are treated as ordinary ones by a later consumer | low | The flag is on the row and in the response; consumers decide |

## 10. Rollback

Nothing is deployed. Scripts are additive and forward-only — `migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all three tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two or three scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | `tenant_id` leads every index |
| Expand / contract | new tables only |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 missing tenant indexes | **Honoured** |
| DEBT-013 package typo `leaveAndAttedance/` | **Discounted.** New code is `core/.../holiday/`; the typo is not carried, and the legacy package is not renamed |
| HRMS holidays global to all tenants (`Holiday.java:1-58`) | **Fixed.** Every row is tenant-scoped and RLS-protected |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Three tables or two tickets?** The calendar-to-location link is a third table. It is a pure join with no behaviour of its own, so I would ship it under the same aggregate exception granted for `W-13.2`. Confirm, or `W-17` splits into calendar-and-holidays plus location-assignment.
2. **One calendar per location, or many?** Payroll allows a holiday to name several locations. **Recommend** one calendar assigned to many locations, and a location resolving to exactly one calendar — otherwise "is this a holiday" has more than one answer and every consumer must break the tie.
