# Feature: Employee search and listing

| Field | Value |
|---|---|
| **Feature ID** | `W-13.3` · from ticket #14 · `CORE-04` |
| **Promoted to** | `docs/target-state/features/W-13-3-employee-search.md` on branch `W-13-3-employee-search` — **`W-13-3` with hyphens**, never `W-13.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | **none** — creates no table | 1 |
| Externally testable behaviour | a tenant's employees are listed and searched, and no one else's are | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Each product has half of a usable list and the other half is the dangerous one.

- HRMS returns every employee with no pagination — `legacy/HRMS_Backend/.../controller/EmployeeController.java:187`, `GET /employees/all`
- HRMS has free-text search — `EmployeeController.java:172`
- Payroll paginates and sorts but cannot search — `legacy/Payroll-Bend-SBoot/.../controller/employee/BasicDetailsController.java:77-100`
- Payroll scopes by a client-supplied `organizationId` **header** — same lines

The unpaginated list is a production incident waiting for the first thousand-employee tenant.
The header scope is worse: it is the pattern `code/frontend/src/shared/api/client.js:16-18`
exists to prevent — a tenant the client can set is a tenant the client can change.

The split decision of 2026-09-22 takes both halves: paginated **and** free-text.

## 2. Scope

**In scope**

- `GET /api/v1/employees` — paginated, sorted, filtered, free-text
- Pagination always on, with a maximum page size
- Tenant scope from the bound context, never from a parameter

**Out of scope**

- Export to CSV or Excel — `W-23` reporting and export (#27)
- Filtering by department, designation or location — those columns arrive with `W-14` (#15)
- Searching inside the detail sections — the index supports name, number and work email only
- Any UI

## 3. Flow

```
[API client] --> [EmployeeController.list] --> [EmployeeQueryService]
   --> TenantContext bound by W-08 --> [core.employee under RLS] --> [Page<EmployeeSummary>]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../employee/EmployeeController.java` | change — add `list` |
| Service | `core/.../employee/EmployeeQueryService.java` | new |
| Repository | `core/.../employee/EmployeeRepository.java` | change — add the search query |
| DTO | `core/.../employee/EmployeeSummaryResponse.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees` | `?q=&status=&page=0&size=25&sort=lastName,asc` | `Page<EmployeeSummaryResponse>` | Bearer, tenant bound |

- `size` defaults to 25 and is capped at 100; a larger value is clamped, not rejected
- `q` matches `first_name`, `last_name`, `employee_number` and `work_email`, case-insensitive, prefix match
- No `organizationId` or `tenantId` parameter exists, deliberately
- Soft-deleted rows are excluded unless `includeDeleted=true`, which requires the same authority as delete

## 5. Frontend changes

None.

## 6. Database changes

**None. This ticket creates no table and ships no migration.**

The indexes it relies on were created by `W-13.1`: `(tenant_id, employee_number)`,
`(tenant_id, work_email)`, `(tenant_id, status)`. If free-text on names proves slow, a
trigram index is a **separate** ticket with its own migration — it is not smuggled in here.

- [x] `tenant_id` on every new table — creates none
- [x] Index on `tenant_id` plus lookup columns — inherited from `W-13.1`
- [x] Money columns — none
- [x] Expand / contract — no schema change

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../employee/EmployeeQueryServiceTest.java` | page size clamped at 100; `q` matches each of the four fields; soft-deleted excluded by default |
| Integration | `core/.../employee/EmployeeSearchRlsIT.java` | with 50 employees in tenant A and 50 in tenant B, every page and every `q` returns only the bound tenant's |
| Integration | `core/.../employee/EmployeeSearchPagingIT.java` | total count is the tenant's count, not the table's; last page is not short by an off-by-one |

`EmployeeSearchRlsIT` is the one that matters. A count query that escapes RLS leaks how many
employees another tenant has even when it returns none of their rows — so the test asserts on
`totalElements`, not only on page contents.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d
bash infra/docker/seed/seed.sh
TOKEN=$(curl -s -d client_id=infinevo-web -d username=admin.globex -d password=local_dev_pw \
  -d grant_type=password \
  http://localhost:8081/realms/infinevo/protocol/openid-connect/token | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/employees?size=500' | jq '.size,.totalElements'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/employees?q=glo' | jq '.content|length'
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/employees?tenantId=11111111-1111-1111-1111-111111111111'

cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| `size=500` | clamped to `100` |
| `totalElements` | the bound tenant's count only |
| `q=glo` | matches by prefix, case-insensitive |
| `?tenantId=...` | parameter ignored; the bound tenant's rows returned, never the other's |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A count query escapes RLS and leaks another tenant's total | medium | `EmployeeSearchRlsIT` asserts on `totalElements` |
| `GET /employees/all` is ported "for parity" | medium | Named in the problem statement as the defect; there is no unpaginated route |
| A tenant parameter is added later for an admin screen | medium | `PLAT-02` impersonation is a separate capability and must not enter through this endpoint |
| Free-text on names is slow at scale | low for now | Prefix match uses the existing indexes; a trigram index is its own ticket |
| `includeDeleted` becomes an unguarded way to read deleted rows | low | Same authority as delete, asserted in the unit test |

## 10. Rollback

Nothing is deployed, and no schema changes. Withdrawing the endpoint is a code revert.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | **creates no table** |
| Flyway only, `ddl-auto` nowhere | **ships no migration**; none added |
| `Money`/`BigDecimal` for money | no money column |
| Index on `tenant_id` plus lookup columns | uses `W-13.1`'s; adds none |
| Expand / contract | no schema change |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 missing tenant indexes | **Honoured.** Every query leads on `tenant_id` through `W-13.1`'s indexes |
| Unpaginated `GET /employees/all` (`EmployeeController.java:187`) | **Fixed.** No unpaginated route exists |
| Client-supplied `organizationId` header (`BasicDetailsController.java:77-100`) | **Fixed.** Scope comes from the bound context only |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

None. Both open questions — paginate always, and search free-text as well — were settled when
the split was approved on 2026-09-22.
