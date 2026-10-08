# Feature: Documents tab on the employee page

| Field | Value |
|---|---|
| **Feature ID** | `W-73.5` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-5-employee-documents-tab.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (document), `code/frontend/src/core/employee` |
| **Related gaps** | `W-21` built the store; no employee screen uses it |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V166` — nullable `label` column on `core.document` (spec amended 2026-10-08: the table has no metadata map, `V037__document.sql`) | 1 |
| Externally testable behaviour | HR uploads an ID proof to an employee; the employee downloads it from `/me` | 1 |
| Frontend area | `core/employee/tabs` | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Employee page tabs: overview, personal, contact, identification, employment (+ salary, FBP from payroll) — no documents | `employeeTabs.jsx:11-40` |
| Identification holds "id proof" and "address proof" as **text** fields; the file itself has nowhere to go | `sectionFields.js` identification group |
| Store, upload, signed download and `/me/documents` exist | `DocumentController.java:68,87`, `DocumentReadController.java:43-50`, `MyDocumentController.java:37` |
| Client exists | `core/document/documentService.js` |

## 2. Scope

**In scope**

- `GET /api/v1/employees/{id}/documents`: the employee's `EMPLOYEE_DOCUMENT` rows (name, label, size, uploaded by, at)
- Upload with a **label** chosen from: ID proof · Address proof · Offer letter · Contract · Certificate · Other (stored in `core.document.label varchar(32) null`, added by `V166`; validated against the six values on upload)
- Tab **Documents** on the employee page: table, Upload drawer, Download (signed link), Delete (`core.employee.update`)
- `/me` portal: a Documents panel listing own documents, download only
- PDF, PNG, JPG ≤ 10 MB (the store's limit, whichever is lower)

**Out of scope**

- Expiry dates, reminders, e-sign
- Replacing the text proof fields on Identification (they stay; a later cleanup)
- Payslips and Form 16 (already their own kinds and screens)

## 3. Flow

```
hr --> /employees/{id} --> Documents --> Upload {file, label} --> POST /api/v1/documents (kind EMPLOYEE_DOCUMENT, employeeId, label)
                                     --> list --> GET /api/v1/employees/{id}/documents
                                     --> Download --> GET /api/v1/documents/{docId}/link --> signed URL
employee --> /me --> Documents --> GET /api/v1/me/documents (exists)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../employee/EmployeeDocumentController.java` (new) | `GET /api/v1/employees/{id}/documents` (`core.employee.read` **and** `core.document.read` — amended at merge review 2026-10-08: `payroll-officer` holds the first only); upload and delete stay on `DocumentController` |
| Service | `DocumentService` | `listForEmployee(employeeId, kind)` — likely already there for `/me/documents`; reuse |
| DTO | `EmployeeDocumentView(id, fileName, label, size, uploadedBy, uploadedAt)` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees/{id}/documents` | — | `[EmployeeDocumentView]` | `core.employee.read` and `core.document.read` |
| POST | `/api/v1/documents` | multipart + `kind=EMPLOYEE_DOCUMENT`, `employeeId`, `label` | `201` | as today |
| DELETE | `/api/v1/documents/{id}` | — | `204` | as today |

## 5. Frontend changes

| File | Change |
|---|---|
| `core/employee/employeeTabs.jsx` | `+ { key: 'documents', label: 'Documents' }` |
| `core/employee/tabs/DocumentsTab.jsx` (new) | table, upload drawer, download, delete with confirm |
| `core/document/documentService.js` | `listForEmployee(id)`, `link(id)` if missing |
| `core/portal/` | `MyDocumentsPanel.jsx` (new) over `/me/documents` |

**Routes added** — none (tab inside the employee page).

## 6. Database changes

| Script | Change | Table | `tenant_id` + RLS |
|---|---|---|---|
| `V166__document_label.sql` | `core.document + label varchar(32) null` — one of `ID_PROOF`, `ADDRESS_PROOF`, `OFFER_LETTER`, `CONTRACT`, `CERTIFICATE`, `OTHER`, enforced by a check constraint; null for every other kind | existing table | already present |

Amended 2026-10-08: the spec first said the label lives in an existing metadata map; `core.document` has no such column (`V037__document.sql:4-31`, `Document.java:55-103`), so one nullable column is added.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `EmployeeDocumentIT` | list is tenant-scoped; employee sees own only via `/me/documents`; delete needs `core.employee.update` |
| Unit | `DocumentsTab.test.jsx` | label required; size limit message; empty state |

## 8. Verification

| Check | Expected |
|---|---|
| Upload an ID proof as HR | Row appears with label, size, your name |
| Sign in as that employee | `/me` Documents shows it; download opens |
| Another employee | Not visible |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Label stored as free metadata drifts | low | the six labels are a frontend constant and validated on upload |

## 10. Rollback

Remove the tab; documents stay in the store.
