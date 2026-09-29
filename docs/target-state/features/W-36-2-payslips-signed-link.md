# Feature: Payslips — approve, pay, render from the run, signed emailed link

| Field | Value |
|---|---|
| **Feature ID** | `W-36.2` · from ticket #48 (`W-36`) · `PAY-15` · closes `W-47.2` §13 decision 1 (approve and pay endpoints) |
| **Promoted to** | `docs/target-state/features/W-36-2-payslips-signed-link.md` on the developer's `dev-<name>` branch — **`W-36-2` with hyphens**, never `W-36.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`; one constant in `code/backend/shared` (`PublicEndpoints`) |
| **Related gaps** | BUG-002 (fixed), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-022 (fixed); proposed BUG-014 and DEBT-034 (`.claude/outputs/2026-09-29-analyze-w-36-tds-payslips.md`) **fixed for new code** |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-29.2` — lines and totals · `W-29.3` — paid days · `W-31.4` and `W-36.1` for a complete slip, though the render does not depend on them · `W-20.1` — `PAYSLIP_READY` is on `main` (`core/.../notification/NotificationEvent.java:26`) · `W-21` — `PublicEndpoints` is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll`, plus **one string constant** added to `shared/.../security/PublicEndpoints.java` — the file says it is the only place a public path is added | 1 |
| Flyway migration | `V103` — columns on `payroll.payrun`, expand only; no new table | 1 |
| Externally testable behaviour | a run is paid and an employee opens their payslip from the emailed link with no session (`09-build-order.md:233`, "done when") | 1 |
| Frontend area | none — the payslip page, the public page and the portal panel are a `W-47` payslip-screens ticket (not yet opened) | 1 |

Within cap with the `shared` exception. Approve and pay are in here because the emailed link
exists only once a run is paid: `W-29.x` left both transitions to "`W-29.5` or `W-36`"
(`W-47-2-pay-run-screens.md:206`), and this is `W-36`.

---

## 1. Problem

The frozen Payroll backend renders a payslip from the pay run row on request, which is right,
and publishes it through a link that is wrong in four ways. All citations are `legacy/`.

- **Rendered, not stored.** `getEmployeePayslip` reads the row and builds the DTO
  (`Payroll-Bend-SBoot/.../serviceimpl/payruns/EmployeePayRunServiceImpl.java:1450-1599`).
  No PDF, no file. Keep that — `02-data-model.md:353`, `:373`
- **The signature is logged.** `PublicPayslipController.java:43-44` writes the full token at
  `INFO` on every request. The target rule is the opposite (`08-work-plan.md:95`)
- **The token never expires and the secret has a committed default** —
  `PayslipTokenServiceImpl.java:15-16`, `:33-39`; a plain `equals`, not constant-time
- **Two endpoints mint a link for any ids, with no session**, under a `permitAll` prefix:
  `PublicPayslipController.java:69-77`, `:79-100`; `config/SecurityConfig.java:26`
- **No email is ever sent.** `BrevoEmailService` has no payslip call; the link is only ever
  produced by the test endpoints above
- Approve and pay copy `status` straight from the request (`W-29-1-…md:51`), so any field
  can be set to anything

`W-21` already fixed the link mechanism for documents: HMAC-SHA256 over tenant, id and expiry
behind a domain tag, constant-time compare, one exact public path, never logged
(`core/.../document/DocumentLinkServiceImpl.java:49-52`, `:177-184`; `shared/.../security/PublicEndpoints.java`).
This ticket applies the same shape to a pay run row.

## 2. Scope

**In scope**

- `POST …/approve`: `COMPUTED → APPROVED`. `POST …/pay`: `APPROVED → PAID` with the date paid
- On `PAID`, one `PAYSLIP_READY` notification per `INCLUDED` employee carrying a 7-day signed link
- The payslip read: officer for any computed run, employee for own paid runs, and the
  anonymous read by signed link
- `PayslipLinkService` in `payroll`: sign, verify, never log
- `PayslipPanelProvider` (`W-25-self-service-portal.md:97`) stops being a placeholder: it
  points at `GET /api/v1/me/payslips`

**Out of scope**

- PDF. The slip is JSON; the page prints it, as the frozen screen does
  (`legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/viewPayslip.js:421-580`). A
  server-rendered PDF is a later ticket if a tenant needs an attachment
- Reject after approve. A wrong approved run is `cancel` (`W-29.1`) and a new run
- Off-cycle runs' slips — `W-30.2` reuses this read once its rows exist in `employee_payrun`
- Re-sending a link by hand. The employee's portal has the slip; the officer re-pays nothing
- Screens — a `W-47` payslip-screens ticket (not yet opened)

## 3. Flow

```
[payroll officer] --> POST /api/v1/payroll/payruns/{id}/approve --> payroll.run.approve
   --> COMPUTED only, else 409 --> APPROVED, approved_at, approved_by

[payroll officer] --> POST /api/v1/payroll/payruns/{id}/pay  {paid_on}  --> payroll.payslip.publish
   --> APPROVED only, else 409 --> PAID, paid_at, paid_by, paid_on, payslips_released_at
   --> for each employee_payrun INCLUDED, after commit (W-15.2's pattern):
         link = PayslipLinkService.signedLink(employeePayrunId, Duration.ofDays(7))
         NotificationService.compose(PAYSLIP_READY, employeeId, {employee_name, period, link})

[officer]   --> GET /api/v1/payroll/payruns/{id}/employees/{employeeId}/payslip   payroll.payslip.read
[employee]  --> GET /api/v1/me/payslips                                            payroll.payslip.read_own  (PAID runs only)
[employee]  --> GET /api/v1/me/payslips/{payrunId}                                 payroll.payslip.read_own
[anyone with the link] --> GET /api/v1/payroll/payslips/open?t=<token>            no bearer; PublicEndpoints.PAYSLIP_OPEN
   --> verify: tenant, employee_payrun id, expiry, signature; the run is PAID; else 404
   --> bind the tenant from the token, as DocumentDownloadController does
   --> PayslipService.render(tenant, employeePayrunId)

PayslipService.render:
   row     = employee_payrun (W-29.1 + W-29.2 totals + W-29.3 days)
   lines   = employee_payrun_line in sort_order, grouped by line_kind
   run     = payrun (period, pay_date, paid_on, type)
   employee= EmployeeService.summary(employeeId)   (name, number, designation, department, date of joining)
   tenant  = tenant name
   --> PayslipResponse
```

The link names the `employee_payrun` row, not `(payrun, employee)`: one id, one row, and a
recompute never changes it. A run that is later cancelled makes every link `404`, because
the verify step checks `PAID`.

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/payslip/`, plus two changes
in `payrun/`.

| Layer | File | Change |
|---|---|---|
| Service (change) | `payrun/PayRunService`, `…Impl` (`W-29.1`) | `approve(id)`, `pay(id, LocalDate paidOn)`; the two transitions added to the `W-29.1` map; `pay` registers the after-commit notification step |
| Entity (change) | `payrun/PayRun.java` | `approvedAt`, `approvedBy`, `paidAt`, `paidBy`, `paidOn`, `payslipsReleasedAt` |
| Controller (change) | `payrun/PayRunController.java` | two endpoints |
| Service / ServiceImpl | `PayslipService`, `PayslipServiceImpl` | new — `render(tenantId, employeePayrunId)`, `forOfficer(payrunId, employeeId)`, `listOwn()`, `own(payrunId)` |
| Service / ServiceImpl | `PayslipLinkService`, `PayslipLinkServiceImpl` | new — `signedLink(UUID employeePayrunId, Duration ttl)` → `{url, expiresAt}`; `verify(String token)` → `Optional<PayslipClaims>`. Token `{tenantId}.{employeePayrunId}.{expiresEpochSecond}.{signature}`, message behind the domain tag `infinevo:payslip-link:v1`, HMAC-SHA256, `MessageDigest.isEqual`, ttl capped at 7 days — `DocumentLinkServiceImpl.java:49-52`, `:120-145`, `:148-185` line for line, with a different domain and a different id |
| Controller | `PayslipController.java` | the three authenticated reads |
| Controller | `PayslipOpenController.java` | the anonymous read at `PublicEndpoints.PAYSLIP_OPEN`; every refusal `404`; `Cache-Control: no-store`, `nosniff`, `Referrer-Policy: no-referrer`; the token is never logged — the only log line is "Served payslip {id} in tenant {id} by signed link" |
| Provider (change) | `portal/PayslipPanelProvider.java` (`W-25`) | descriptor points at `/api/v1/me/payslips`; if `W-25` is not on `main`, this ticket leaves it |
| DTO | `PayslipResponse`, `PayslipLineResponse`, `PayslipSummaryResponse`, `PayRunPaymentRequest` | new; `status` / `message` / `data` envelope |
| Constant | `shared/.../security/PublicEndpoints.java` | `PAYSLIP_OPEN = "/api/v1/payroll/payslips/open"` added to `PATHS`; javadoc as `DOCUMENT_DOWNLOAD` |

**Secret and base URL.** `PayslipLinkServiceImpl` reads `document.link.secret` — the same
Key Vault value as `W-21` (`containerapps.bicep`, `document-link-secret`), so no new secret,
no `deploy.sh` change. The domain tag keeps a document token from ever verifying as a payslip
token and vice versa. The link's base is `payslip.link.base-url`, default
`/public/payslips`, the page a `W-47` payslip-screens ticket (not yet opened) builds; the page calls `…/payslips/open?t=` with the
same token. No default secret, no default shorter than 24 characters — the `W-21` constructor
checks, copied.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/payruns/{id}/approve` | — | `200` the run `APPROVED`; `409` unless `COMPUTED` | `payroll.run.approve` |
| POST | `/api/v1/payroll/payruns/{id}/pay` | `paid_on` (`YYYY-MM-DD`, not in the future, not before `period` start) | `200` the run `PAID` with `notified` count; `409` unless `APPROVED` | `payroll.payslip.publish` |
| GET | `/api/v1/payroll/payruns/{id}/employees/{employeeId}/payslip` | — | `PayslipResponse`; `409` if the run is `DRAFT`, `LOCKED`, `COMPUTING`, `FAILED`; `404` if `SKIPPED` | `payroll.payslip.read` |
| GET | `/api/v1/me/payslips` | `page`, `size` (12, clamped at 100) | own rows of `PAID` runs, newest period first: `payrun_id`, `period`, `paid_on`, `net_pay` | `payroll.payslip.read_own` |
| GET | `/api/v1/me/payslips/{payrunId}` | — | `PayslipResponse`; `404` unless `PAID` and own | `payroll.payslip.read_own` |
| GET | `/api/v1/payroll/payslips/open` | `t` | `PayslipResponse`; **`404` for every refusal** — malformed, tampered, expired, not `PAID`, cancelled | none (signed link) |

All three action codes exist (`migration/.../reference/V020__action.sql:117-122`) and are
granted: officer `read` and `publish`, finance `read`, employee `read_own`
(`V052__fbp_actions.sql:92-93`, `:107`, `:122`); `payroll.run.approve` is in the catalogue at
`V020:119`. No seed script.

**`PayslipResponse`**, replacing `dto/payruns/PayslipResponseDTO.java:12-95`

| Group | Fields | From |
|---|---|---|
| `run` | `payrun_id`, `type`, `period`, `pay_date`, `paid_on`, `status` | `payroll.payrun` |
| `employer` | `name` | `core.tenant` |
| `employee` | `id`, `number`, `name`, `designation`, `department`, `date_of_joining` | `EmployeeService` (`W-13.1`, `W-13.2`) |
| `days` | `payable_days`, `paid_days`, `lop_days`, `unpaid_days` | `W-29.3` columns |
| `earnings`, `deductions`, `reimbursements`, `benefits` | each a list of `{code, name, amount, source, is_taxable}` in `sort_order` | `employee_payrun_line` |
| `totals` | `gross_earnings`, `total_deductions`, `total_reimbursements`, `total_benefits`, `net_pay` | `employee_payrun` (`W-29.2`) |
| `link` | `expires_at` | only on the anonymous read |

Every amount a string-safe `BigDecimal` at scale 2 on the wire, from the stored scale 4, the
`W-29.2` rounding rule. Legacy `Double` throughout (`PayslipResponseDTO.java:18-21`, `:92`) — BUG-011.

`/me/*` resolves the employee with `EmployeeService.currentEmployee()` as `W-27.2` does
(`payroll/.../fbp/FbpDeclarationServiceImpl.java:84`); a login linked to no employee gets `403`.

**Notification.** `NotificationService.compose(PAYSLIP_READY, employeeId, Map.of("employee_name", …, "period", "April 2026", "link", url))`
— the three keys the event declares (`NotificationEvent.java:26`). Composition runs after the
`pay` transaction commits, once per included employee, each in its own transaction, so one
employee with no template does not undo the payment. `notified` in the response is the count
composed. Legacy Brevo template `SALARY_SLIP` (`W-20-1-…md:36`) is not migrated; the seeded
default template for `PAYSLIP_READY` is used.

## 5. Frontend changes

None. a `W-47` payslip-screens ticket (not yet opened) builds the officer payslip view, the public `/public/payslips` page and the
`MyPayslips` panel against this contract; the layout is ported from `viewPayslip.js:421-580` then.

## 6. Database changes

> Flyway only. Never `ddl-auto`. Every statement names its schema
> (`code/backend/migration/README.md`).

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V103__payrun_approve_pay.sql` | `ALTER payroll.payrun` | existing table, RLS in place (`V055`) | additive |

`payroll.payrun` gains: `approved_at timestamptz` · `approved_by varchar(100)` · `paid_at timestamptz` ·
`paid_by varchar(100)` · `paid_on date` · `payslips_released_at timestamptz` ·
`CHECK ((status = 'PAID') = (paid_on IS NOT NULL))`.

No table for the payslip and no table for the link: `02-data-model.md:353` and `:373`.

- [x] `tenant_id` — no new table; the altered table has it and its policy (`W-29.1`)
- [x] Index — none needed; `idx_payrun_tenant_status` (`W-29.1`) serves the `PAID` list
- [x] Money — nothing new stored; the response is `BigDecimal`
- [x] Expand / contract — nullable columns added, nothing dropped

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payslip/PayslipLinkServiceTest.java` | a valid link verifies; tampered signature, tampered id, tampered expiry, expired, a `W-21` document token, a token signed with another domain all fail; verify is constant-time (`MessageDigest.isEqual` is the only comparison); ttl over 7 days refused; a blank or short secret refuses to start |
| Unit | `payroll/.../payrun/PayRunTransitionTest.java` (change) | `approve` from every status but `COMPUTED` is `409`; `pay` from every status but `APPROVED` is `409`; `paid_on` in the future or before the period is `400` |
| Integration | `payroll/.../payslip/PayslipReleaseIT.java` | **the acceptance test**: compute a two-employee run, approve, pay ⇒ `PAID`, `paid_on` stored, two `core.notification` rows for `PAYSLIP_READY` whose data holds a `link`; `GET …/open?t=` with that link and **no bearer** returns the slip with the `W-29.2` totals and lines; the same call after `cancel` is `404`; a link built with the same secret and the document domain is `404` |
| Integration | `payroll/.../payslip/PayslipReadIT.java` | officer read on a `COMPUTED` run works and on `LOCKED` is `409`; `GET /me/payslips` before `pay` is empty and after has one row; another employee's `payrunId` under `/me` is `404`; `SKIPPED` employee is `404` |
| Integration | `payroll/.../payslip/PayslipRlsIT.java` | as `app_user`, tenant A cannot read tenant B's slip by the officer path; a tenant B token presented under tenant A's bearer still resolves tenant B from the token and returns tenant B's slip — the anonymous path binds from the token only, as `DocumentDownloadController` |
| Integration | `payroll/.../payslip/PayslipLogIT.java` | after `PayslipReleaseIT`'s calls, the captured log output contains no token and no signature (`OutputCaptureExtension`) |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='payrun'
      AND column_name IN ('approved_at','paid_on','payslips_released_at');"
grep -n "PAYSLIP_OPEN" code/backend/shared/src/main/java/com/infinevo/shared/security/PublicEndpoints.java
grep -rn 'log\.\(info\|debug\|warn\|error\).*\(token\|signature\|"t"\)' code/backend/payroll/src/main/java/com/infinevo/payroll/payslip/ || echo "no signature logged"
grep -rn "secret-key\|default-payslip" code/backend/payroll/src/main/java || echo "no default secret"
grep -rn "Double\|double\|float" code/backend/payroll/src/main/java/com/infinevo/payroll/payslip/ || echo "no floating point"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Columns | three rows: `timestamp with time zone`, `date`, `timestamp with time zone` |
| Public path | one line, the constant, inside `PATHS` |
| Logging | `no signature logged` |
| Default secret | `no default secret` |
| Floating point | `no floating point` |
| Suite | green, no skips; `PayslipReleaseIT` and `PayslipLogIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The token is logged "for debugging", as `PublicPayslipController.java:43-44` | high — it is the line being ported | `PayslipLogIT` fails the build; the §8 grep is a merge gate |
| A test or "latest" endpoint that mints links is added under the public path, as `:69-100` | medium | `PublicEndpoints` is exact paths only; the reviewer checks `PayslipOpenController` has one mapping |
| The link is a path prefix in security config, as `SecurityConfig.java:26` | low | `PublicEndpoints` refuses `*` and `{` in its static block |
| A recompute after approve changes what the employee sees | low | `approve` is from `COMPUTED` only and `compute` is `409` from `APPROVED` (`W-29-4-…md:111`) |
| Notifications composed inside the `pay` transaction roll the payment back on one failure | medium | after-commit, one transaction per employee; the IT asserts `PAID` with two notification rows |
| The slip is written to a file "for immutability" | medium | §1: immutability is the status. No storage anywhere; `02-data-model.md:373` |

## 10. Rollback

Nothing is deployed. `V103` is additive. A run paid in error stays `PAID`: the money moved.
Its links expire in 7 days on their own; cancelling the run makes them `404` at once.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table; `payroll.payrun` already has both |
| Flyway only, `ddl-auto` nowhere | `V103` |
| `Money`/`BigDecimal` for money | response amounts `BigDecimal`; nothing new stored |
| Index on `tenant_id` plus lookup columns | no new lookup; `idx_payrun_tenant_status` reused |
| Expand / contract | columns added, nullable |
| No module references another module | `payroll` uses `core` (`EmployeeService`, `NotificationService`, tenant name) and `shared` (`PublicEndpoints`, `Money`); the one `shared` edit is a constant |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed** — the anonymous path binds the tenant from the signed token |
| BUG-011 money as `Double` | **Fixed** for the response |
| BUG-014 (proposed) open link-minting endpoints | **Fixed for new code** — no minting endpoint; links are issued only inside `pay` |
| DEBT-034 (proposed) token logged, no expiry, default secret | **Fixed for new code** — `PayslipLogIT`, 7-day expiry inside the signature, no default. The frozen system still logs it; that is an operational rotation, as `W-21` §12 |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-022 unscoped finders | **Fixed** |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Where do approve and pay live? | **Here.** The payslip is released by paying, so the transition and the release are one act. `W-47.2` gains two buttons (`W-47-2-…md:206`) |
| 2 | Store the slip? | **Never.** Rendered from the row and lines; the run's status is the immutability (`09-build-order.md:233`) |
| 3 | Reuse `DocumentLinkService`? | **No — same shape, own service.** Its claims are `(tenant, document)`; a payslip is not a document and must not become one to get a link. Same secret, different domain tag, so neither token verifies as the other |
| 4 | A new secret? | **No.** `document.link.secret` with the `infinevo:payslip-link:v1` domain. One rotation rotates both, which is what an operator would do anyway |
| 5 | What does the link point at? | **The frontend public page**, `payslip.link.base-url`, which calls the anonymous API with the same token. The API alone satisfies "opens with no session" for the IT; the page makes it readable |
| 6 | PDF? | **Not now.** JSON plus the printed page, as the frozen screen. A PDF is a later ticket with a real library decision |
| 7 | When may an employee see a slip? | **`PAID` only.** Officer from `COMPUTED`. Legacy showed whatever the row held |
| 8 | Which action guards `pay`? | **`payroll.payslip.publish`** — "release payslips to employees" is exactly what paying does. `approve` keeps `payroll.run.approve` |
