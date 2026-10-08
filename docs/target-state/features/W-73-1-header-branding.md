# Feature: Header branding — company name, logo, tagline, user and role

| Field | Value |
|---|---|
| **Feature ID** | `W-73.1` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-1-header-branding.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (tenant, navigation), `code/backend/shared` (`/me`), `code/frontend/src/shell` |
| **Related gaps** | `D-13` (tenant name only) |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (+ `shared` for two fields on `/me`) | 1 |
| Flyway migration | `V160` — two nullable columns on `core.tenant` | 1 |
| Externally testable behaviour | the header shows company, logo or initials, tagline, user name and role | 1 |
| Frontend area | `shell/` header + one Settings screen | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Header shows the tenant name and a logout button, nothing else | `Header.jsx:38,102-105,117` |
| `/me` returns id, email, names, tenant — no roles | `MeController.java:40` |
| `core.tenant` has no logo or tagline column | `V001__tenant.sql`, `V082__platform_tenant.sql:8-11` |
| Document store already holds tenant-scoped files with a signed download link | `DocumentController.java:68`, `DocumentReadController.java:50` |

## 2. Scope

**In scope**

- `core.tenant.logo_document_id` (uuid, nullable, FK `core.document`) and `tagline` (text, nullable, max 80)
- Navigation reply gains `tenantLogoUrl` (signed link or null) and `tagline`
- `/me` gains `roles` (codes) and `displayName`
- Header: logo **or** a circle with the company's initials (never an empty box); tagline only when set; right side: user name, one chip per role (`hr`, `Payroll officer` …), a user menu with Sign out
- **Company profile** screen at `/settings/company`: name (read-only here), logo upload (PNG/SVG/JPG ≤ 512 KB), tagline; action `core.tenant.update`
- Menu item `core.settings.company` under Settings (`D-34` group)

**Out of scope**

- Colour themes per tenant
- Logo in Keycloak login pages (the realm theme is shared; `D-14`)
- Email templates (`W-20.2`) — a follow-up may reuse the same columns

## 3. Flow

```
tenant-admin --> /settings/company --> PUT /api/v1/tenants/current/profile {tagline, logoDocumentId}
                                   --> POST /api/v1/documents (kind TENANT_LOGO) --> core.document
any user --> GET /api/v1/navigation --> {tenantName, tenantLogoUrl, tagline, ...}
         --> GET /api/v1/me         --> {displayName, roles[]}
Header renders: [logo|initials] name · tagline ............ user name [role chips] ▾
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `core/.../tenant/TenantEntity` (whatever `TenantController.java:29` saves) | `logoDocumentId`, `tagline` |
| Controller | `core/.../tenant/TenantController.java` | `GET`/`PUT /api/v1/tenants/current/profile` for the signed-in tenant; `@RequiresAction("core.tenant.update")` on `PUT`, `core.tenant.read` on `GET` |
| DTO | `TenantProfileRequest(tagline, logoDocumentId)`, `TenantProfileResponse` | new |
| Enumeration | `core/.../document/DocumentKind.java` | `TENANT_LOGO` (tenant-scoped, not employee-scoped) |
| Service | `core/.../navigation/NavigationService.java:89-103` | `tenantName()` grows to `tenantBranding()` reading name, tagline, logo id in one query; logo id → signed link via the document link service |
| DTO | `NavigationResponse` | `+ tenantLogoUrl`, `+ tagline` |
| Controller | `shared/.../identity/MeController.java:40` | `MeView + displayName + List<String> roles`; roles from the same source `NavigationService` uses for actions |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/tenants/current/profile` | — | `{name, tagline, logoDocumentId, logoUrl}` | `core.tenant.read` |
| PUT | `/api/v1/tenants/current/profile` | `{tagline, logoDocumentId}` | same | `core.tenant.update` |
| GET | `/api/v1/navigation` | — | `+ tenantLogoUrl, + tagline` | signed in |
| GET | `/api/v1/me` | — | `+ displayName, + roles` | signed in |

## 5. Frontend changes

| File | Change |
|---|---|
| `shell/Header.jsx` | Logo or initials avatar (`Avatar` with the first letters of up to two words), name, tagline (`Text type="secondary"`, hidden when empty); right: `displayName`, role chips from `roleLabels.js` (code → label, unknown code shown as words like `navLabels.js`), `Dropdown` with Sign out |
| `shell/navigation/useNavigation.js` | keep `tenantLogoUrl`, `tagline` in state |
| `shell/auth/useMe.js` (new) | fetch `/me` once, expose `displayName`, `roles` |
| `core/settings/CompanyProfile.jsx` (new) | form: tagline, logo upload through `documentService.js`, preview, remove |
| `shell/navigation/navLabels.js` | `nav.settings.company` → "Company profile" |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/settings/company` | `CompanyProfile` | feed item `core.settings.company` |

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V160__tenant_branding.sql` | `core.tenant` + `logo_document_id uuid null references core.document(id)`, `tagline text null` | existing table, RLS unchanged | yes, both nullable |

- [x] no new table
- [x] no money

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `Header.test.jsx` | initials when no logo; tagline hidden when null; one chip per role |
| Unit | `roleLabels.test.js` | every seeded role code has a label |
| Integration | `TenantProfileIT` | PUT needs `core.tenant.update`; a logo document from another tenant is refused (RLS) |
| Integration | `NavigationBrandingIT` | reply carries name, tagline, link; acting-as returns the target tenant's |

## 8. Verification

| Check | Expected |
|---|---|
| Fresh tenant, no logo | Header shows initials circle, name, no tagline, no blank box |
| Upload a logo, set tagline | Header updates after reload; tagline under the name |
| Sign in as `hr` | Right side shows the person's name and chip `HR` |
| Act as another tenant (`W-65.2`) | Header shows that tenant's branding, banner unchanged |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Signed logo link expires while the page is open | medium | link valid 24 h; header refetches the feed on 403 |
| Big logos slow the header | low | 512 KB cap, resized client-side to 256 px |

## 10. Rollback

Both columns nullable; the header falls back to initials when the fields are null. Revert the frontend commit; leave the migration.
