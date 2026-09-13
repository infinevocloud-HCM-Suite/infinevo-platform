# Security finding — unauthenticated employee leave endpoint (HRMS)

**Found:** 2026-09-11, while resolving the duplicate-entity question.
**App:** `HRMS_Backend` @ `main` (d984c64) — the live branch.
**Status:** Not fixed. Reported only. Proposed as a new GAP entry (BUG) via `sync-docs`.

## What was found

`HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/config/SecurityConfig.java:38`

```java
.requestMatchers("/auth/**", "/public/**", "/register").permitAll()
```

`controller/IntegrateWithPayroll.java:29` is mapped at `/public`:

```java
@RequestMapping("/public")
...
@GetMapping("/test-hrms")          // line 47
@PostMapping("/get-employee-leaves") // line 64
```

So `POST /public/get-employee-leaves` is reachable **with no authentication and no
authorization**, and returns employee leave data for the identifiers in the request body.

CSRF is disabled globally (`SecurityConfig.java:35`), so the endpoint is also callable
cross-origin from a browser.

## Why it matters

| Aspect | Assessment |
|---|---|
| Data exposed | Employee leave records (dates, leave type, LOP days) |
| Authentication | None |
| Exploitability | **Depends on whether employee identifiers are guessable.** Not yet verified. If IDs are sequential or derivable, this is bulk employee data disclosure. If they are random, it is a targeted-only leak. |
| Also unauthenticated | `/register` and `/public/test-hrms` on the same rule |

**Not verified, do not assume:** whether this endpoint is reachable from the public internet
in the current DigitalOcean deployment, or only on an internal network. That changes the
severity from critical to low. **Check the deployment before acting on this.**

## Context that makes it worse

`agents/active-work.md` lists the HRMS→Payroll LOP integration as **frozen / superseded** by
Payroll's own leave tables. But this controller is live, reachable, and wired to the live
`LeaveRequests` entity. So either:

- the integration is still in use and active-work is wrong, or
- it is dead weight that is nevertheless still serving data to anyone who asks.

Both readings argue for resolving it. This is **Open Question 4** in `active-work.md`.

## Recommended action (not taken)

1. Confirm whether the endpoint is internet-reachable in production. This is the first step
   and it changes everything after it.
2. If reachable: restrict `/public/**` immediately, or move this controller behind a
   shared-secret header at minimum.
3. Resolve Open Question 4 — if the integration is superseded, delete the controller rather
   than securing it.
4. Audit the other `permitAll()` entries: `/register` allows unauthenticated account creation.

## Related

- Payroll has a comparable issue: `/api/test/**` unguarded (existing GAP entry).
- Proposed new GAP entry. Needs `sync-docs` with founder approval to land in
  `docs/GAP_INVENTORY.md`, since the guard hook blocks direct edits.

---

# Addendum — Payroll tenancy review, 2026-09-11

Found while resolving `OQ-08` (Keycloak realm model). Same status: **reported only, nothing changed.**

## Good news first — the design is sound

`Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/OrganizationRoleInterceptor.java`
verifies, on every `/api/**` and `/admin/**` request, that the caller (JWT `sub`) has an
`OrganizationUserRoleMapping` row for the target organization — else 403. It is registered in
`config/WebConfig.java:28-30` and uses a deny-by-default allowlist for employee-accessible paths.

**So the `organizationId` header is untrusted input that is actually checked.** This is better
than the earlier assessment assumed, and the pattern is worth carrying into the target state.

## Gap 1 — 27 endpoints bypass the interceptor (needs verification)

`OrganizationRoleInterceptor.resolveOrganizationId()` (line ~150) reads only:
- `request.getHeader("organizationId")`
- `request.getParameter("organizationId")` — query string / form params

**It does not read path variables.** But 27 controller methods declare
`@PathVariable("organizationId")`, concentrated in
`controller/employeeitdeclaration/AdminProofOfInvestmentController.java` (lines 240, 285, 333,
381, 426, 472, …).

For those, `resolveOrganizationId()` returns null → `if (isBlank(organizationId)) return true;`
→ **the membership check is skipped entirely.** Spring Security still requires a valid token,
so the caller must be logged in — but any logged-in user from any organization reaches them.

> ⚠️ **NOT YET VERIFIED:** whether the service layer behind those endpoints re-checks
> membership. If it only *filters by* the supplied organization id, this is cross-tenant
> data access on tax and investment-proof data. **Verify before assuming either way.**

## Gap 2 — Keycloak admin credentials committed, weak password

`src/main/resources/application.properties:16-21`

```
keycloak.admin.server-url=https://authentication.infinevocloud.com
keycloak.admin.realm=HRMS
keycloak.admin.client-id=hrms-payroll-backend
keycloak.admin.client-secret=<redacted>
keycloak.admin.username=admin
keycloak.admin.password=<redacted — an eight-character trivial password>
```

Administrative credentials for a **live, internet-reachable identity provider**, in a
committed file, with a trivial password. Anyone with repository access can administer the
realm — which means creating users, resetting passwords, and impersonating anyone.

**This is more serious than the unauthenticated endpoint above.** Recommended: rotate the
password now, independent of any other work; move to a service account with least privilege;
move the secret to Key Vault. Note that rotating does not undo exposure — assume the current
value is compromised and check the realm for unexpected users or clients.

## Gap 3 — minor: signed payslip token written to logs

`controller/payruns/PublicPayslipController.java:43-44` logs the HMAC-SHA256 payslip token at
`info`. The token design itself is correct (signed link, not an open endpoint), but logging it
makes it replayable by anyone with log access. Drop the token from the log line.

## Target-state consequence (`D-21`)

Keep one realm. Keep server-side membership validation. **Change how the tenant is carried:**
resolve it once in a filter and set it on the database session so row-level security enforces
it, instead of passing `organizationId` by hand into 270 controller methods. That removes the
whole class of bug in Gap 1 — an endpoint that forgets to pass the tenant gets no rows, rather
than every row.
