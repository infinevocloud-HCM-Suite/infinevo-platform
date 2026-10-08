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
| Flyway migration | none (`EMPLOYEE_DOCUMENT` kind exists, `DocumentKind.java:18`; a sub-kind column is **not** added — the label is metadata) | 1 |
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
- Upload with a **label** chosen from: ID proof · Address proof · Offer letter · Contract · Certificate · Other (stored in the document's existing metadata map; no schema change)
- Tab **Documents** on the employee page: table, Upload drawer, Download (signed link), Delete (`core.employee.update`)
- `/me` portal: a Documents panel listing own documents, download only
- PDF, PNG, JPG ≤ 10 MB (the store's limit, whichever is lower)

**Out of scope**

- Expiry dates, reminders, e-sign
- Replacing the text proof fields on Identification (they stay; a later cleanup)
- Payslips and Form 16 (already their own kinds and screens)

## 3. Flow

```
hr --> /employees/{id} --> Documents --> Upload {file, label} --> POST /api/v1/documents (kind EMPLOYEE_DOCUMENT, employeeId, metadata.label)
                                     --> list --> GET /api/v1/employees/{id}/documents
                                     --> Download --> GET /api/v1/documents/{docId}/link --> signed URL
employee --> /me --> Documents --> GET /api/v1/me/documents (exists)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../employee/EmployeeDocumentController.java` (new) | `GET /api/v1/employees/{id}/documents` (`core.employee.read`); upload and delete stay on `DocumentController` |
| Service | `DocumentService` | `listForEmployee(employeeId, kind)` — likely already there for `/me/documents`; reuse |
| DTO | `EmployeeDocumentView(id, fileName, label, size, uploadedBy, uploadedAt)` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees/{id}/documents` | — | `[EmployeeDocumentView]` | `core.employee.read` |
| POST | `/api/v1/documents` | multipart + `kind=EMPLOYEE_DOCUMENT`, `employeeId`, `metadata.label` | `201` | as today |
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

None.

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
