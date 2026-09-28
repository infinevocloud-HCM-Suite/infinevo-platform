# Feature: Holiday calendar screens

| Field | Value |
|---|---|
| **Feature ID** | `W-46.3b` · from ticket #60 · `CORE-08` |
| **Spec file** | `docs/target-state/features/W-46-3b-holiday-screens.md` |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/core/holiday` · `code/backend/core` — `NavigationCatalogue.java` menu item only |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 — **split from `W-46.3`**; masters are `W-46.3a` |
| **Blocked by** | `W-45`; `W-17` (krushna, after `W-24.1`) — every endpoint is spec-only today |
| **Size** | **S** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — one menu item, nothing else | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator keeps a holiday list per work location from the browser, and an employee at that location sees it | 1 |
| Frontend area | `src/core/holiday` | 1 |

Within cap.

---

## 1. Problem

`W-17` builds `core.holiday_calendar` (a named list assigned to work locations) and `core.holiday` (a dated entry, range allowed, restricted flag) with CRUD and a range query (`W-17-holiday-calendar.md` §2, §4). Nothing renders it.

| Frozen screen | Ported? |
|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/holidays.js` | **yes** in shape: table by year |
| `.../leaveAttendence/addHoliday.js:170-202` | **yes**: name, from date, to date; the target adds the restricted flag |
| `legacy/HRMS_Frontend/src/components/adminDashboard/HolidayAdmin.jsx`, `Leaves/HolidayUser.jsx` | shape only; MUI retired. The user view becomes a portal panel later |

Neither frozen system has calendars per location; both hold one flat list. The screen must make the calendar the first thing chosen.

## 2. Scope

**In scope**

- Calendars: list, create, edit name and assigned work locations
- Holidays in a calendar: table for a chosen year, add (name, from, to, restricted), delete with confirm
- A read-only "holidays between" view by work location and date range, the same query `W-16` and `W-18` use, so an administrator can see what the engine sees
- Menu item `core.holiday`

**Out of scope**

- Bulk or regional import — `W-17` §2 excludes it
- A national list — holidays are per tenant (`W-17` §2)
- The employee's own holiday view — a portal panel, after `W-25`
- Editing a holiday in place — `W-17` has add and delete only; edit is delete-and-add here, and a `PUT` is a `W-17` follow-up if the founder wants it

## 3. Flow

```
[admin] --> /holidays                       --> holidayCalendarService.list()
        --> /holidays/new, /:id/edit        --> create({name, workLocationIds}) / update
        --> /holidays/:id?year=             --> holidayService.between({workLocationId of the calendar, from, to})
              add                           --> holidayCalendarService.addHoliday(id, {name, from, to, isRestricted})
              delete                        --> holidayCalendarService.removeHoliday(id, holidayId)
        --> /holidays/lookup                --> holidayService.between({workLocationId, from, to})
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `code/backend/core/.../navigation/NavigationCatalogue.java` | add `core.holiday` (`nav.holidays`, `/holidays`, target `/api/v1/holiday-calendars`, module `null`, action `core.holiday.read`) |

No `W-17` spec adds the item; the validator forbids adding it before the endpoint exists, so it rides here.

**API contract** — consumed: `W-17-holiday-calendar.md` §4 (`/api/v1/holiday-calendars`, `/{id}/holidays`, `/api/v1/holidays?workLocationId&from&to`).

## 5. Frontend changes

`W-45` contract throughout.

| File | Change |
|---|---|
| `src/core/holiday/holidayCalendarService.js` | **new.** `createService('/v1/holiday-calendars')` spread, plus `addHoliday(id, body)`, `removeHoliday(id, holidayId)` |
| `src/core/holiday/holidayService.js` | **new.** `between({workLocationId, from, to})` → `GET /v1/holidays` |
| `src/core/holiday/Calendars.jsx` | **new.** Table: name, locations (tags, names from `@core/employee` masters cache), holiday count this year. Create / edit `Drawer` with name and a work-location multi-select |
| `src/core/holiday/CalendarHolidays.jsx` | **new.** Year picker; table: date(s), name, restricted `Tag`, weekday, actions. "Add holiday" `Modal`: name, from, to (defaults to from), restricted `Switch`. Delete with confirm. From `holidays.js` and `addHoliday.js:170-202` |
| `src/core/holiday/HolidayLookup.jsx` | **new.** Location `Select`, date range; read-only table of what the range query returns |
| `src/core/index.js` | `routes` gains the five below |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/holidays` | `Calendars` | inside `AppShell`; present when the feed carries `core.holiday` |
| `/holidays/new`, `/holidays/:id/edit` | calendar `Drawer` routes | same; hidden without `core.holiday.manage` |
| `/holidays/:id` | `CalendarHolidays` | same |
| `/holidays/lookup` | `HolidayLookup` | same |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/holiday/*Service.test.js` | paths and methods; `addHoliday` posts under `/{id}/holidays` |
| Component | `Calendars.test.jsx` | create sends `workLocationIds`; no write actions without `core.holiday.manage` |
| Component | `CalendarHolidays.test.jsx` | a range holiday renders two dates; add posts the four fields; delete confirms |
| Backend unit | `NavigationCatalogueTest` | the `core.holiday` leaf points at an existing `GET` |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
cd ../backend && ./mvnw -pl core test -Dtest=NavigationCatalogue*
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| menu | admin@acme.local sees Holidays; a user without `core.holiday.read` does not |
| calendar | create "Bengaluru 2026" assigned to one location |
| holiday | add 2026-10-02 "Gandhi Jayanti"; the lookup for that location and October returns it; a second location returns nothing |
| isolation | Globex sees no Acme calendar |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Administrators expect one flat list and miss the calendar step | medium | `/holidays` lands on the calendar list; a tenant with exactly one calendar is taken straight into it |
| No edit endpoint for a holiday | low | delete-and-add; a `PUT` is a small `W-17` follow-up if asked for |

## 10. Rollback

Revert the branch; the catalogue row goes with it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | no money |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/holiday` imports `@shared/*`, `@shell/screens`, `@core/employee` (masters cache); catalogue change is in `core` |

## 12. Gap inventory

None overlap.

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (backend) | catalogue row and test |
| 2 | `src/core/holiday` | services, `Calendars`, `CalendarHolidays`, `HolidayLookup`, tests; `src/core/index.js` registration |

## 14. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Calendar-first or a flat list with a location column? | **Calendar-first.** It is the data model (`W-17` §2) and a flat list hides which locations a date applies to |
