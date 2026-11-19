# Feature: Mobile Payslips & Payslip PDF

| Field | Value |
|---|---|
| **Feature ID** | `W-98.4` · `PAYROLL-MOBILE-PAYSLIPS` |
| **Promoted to** | `docs/target-state/features/W-98-4-mobile-payslips-signed-download.md` — **`W-98-4` with hyphens**, never `W-98.4` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/payroll` (payslip PDF), `code/frontend` (`src/mobile/payslips/`, `android/`, `ios/`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 4: Payslips) |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-98.1` |

**Founder decision 2026-11-19:** the backend payslip PDF is part of this ticket. Size goes from S to **M**.

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `payroll` | 1 | Pass |
| Flyway migration | none | 1 | Pass |
| Externally testable behaviour | An employee opens a payslip on the phone and saves or shares its PDF | 1 | Pass |
| Frontend area | `src/mobile/payslips/` | 1 | Pass |

---

## 1. Problem

| # | What is missing today | Evidence |
|---|---|---|
| 1 | Payroll serves payslips as JSON only. There is no PDF and no download endpoint, for web or mobile. | `code/backend/payroll/src/main/java/com/infinevo/payroll/payslip/PayslipController.java:47,57` · `PayslipOpenController.java:44-56` |
| 2 | The web payslip page is laid out for A4 and needs zooming on a phone. | `code/frontend/src/shell/portal/PayslipPage.jsx` |
| 3 | A browser download link does not work inside a native webview. | — |

---

## 2. Scope

**In scope**

| Area | Rule |
|---|---|
| Payslip PDF | Payroll renders the payslip as a real PDF from the same `PayslipResponse` the JSON endpoints return: employer, employee, period, days, earnings, deductions, reimbursements, totals. OpenPDF is added to `code/backend/payroll/pom.xml` (founder approved 2026-11-19). |
| Through the signed link | The existing public endpoint `GET /api/v1/payroll/payslips/open?t=…` gains `format=pdf`. With it the answer is `application/pdf`; without it the JSON answer is unchanged. |
| Link for the app | New `POST /api/v1/me/payslips/{payrunId}/link` gives the signed-in employee a link token for **their own** payslip, valid 5 minutes, made by the existing `PayslipLinkService`. |
| What "signed" means | The link is signed. The PDF itself carries no digital signature. |
| Screen | `MobilePayslipsScreen.jsx` on the Payslips tab (`/me/payslips`): the latest payslip on top, then earlier months **grouped by financial year** (April to March, from `period`), each with net pay and paid-on date. A refresh control reloads the list. |
| Detail | `MobilePayslipDetailSheet.jsx`: earnings, deductions, reimbursements, totals and days from `GET /api/v1/me/payslips/{payrunId}`. |
| Money | Amounts go from the API to `MoneyText` as received. They are never passed through `Number()`, never re-added on the phone. |
| Save and share | The app downloads the PDF bytes, checks the content type is `application/pdf` and the file starts with `%PDF`, writes it to **app-private storage** (`Directory.Cache`), and opens the OS share sheet with that file. Nothing is written to public Documents or Downloads. |
| Cleanup | Cached payslip files are deleted on logout and when the app starts. |
| Native plugins | `@capacitor/filesystem` and `@capacitor/share` are in `package.json` **and** synced into `android/` and `ios/` (`npx cap sync` output committed). |
| States | Loading, empty and error views differ; a failed download says so and leaves no partial file. |

**Out of scope**

- Any change to pay computation; Form 16 (`W-36.4`, `W-36.5`).
- A PDF button on the web portal (can reuse the endpoint in a later ticket).
- Password-protected or digitally signed PDFs.

---

## 3. Flow

```
Payslips tab --> GET /api/v1/me/payslips --> list grouped by financial year
Tap a month  --> GET /api/v1/me/payslips/{payrunId} --> detail sheet
Tap PDF      --> POST /api/v1/me/payslips/{payrunId}/link --> {token, expires_at}
             --> GET /api/v1/payroll/payslips/open?t={token}&format=pdf --> PDF bytes
             --> check %PDF --> write to app cache --> share sheet
Logout       --> delete cached payslips
```

---

## 4. Backend changes

| Layer | File (`code/backend/payroll/src/main/java/com/infinevo/payroll/payslip/`) | Change |
|---|---|---|
| Controller | `PayslipController.java` | New `POST /api/v1/me/payslips/{payrunId}/link`. |
| Controller | `PayslipOpenController.java` | `format=pdf` returns the PDF; same token check, same `no-store` header, same log line. |
| Service | `PayslipService.java`, `PayslipServiceImpl.java` | Resolve the caller's own payslip for the link; hand the rendered `PayslipResponse` to the PDF writer. |
| Service (new) | `PayslipPdfWriter.java` | Builds the PDF from a `PayslipResponse`. Amounts stay `BigDecimal` and are printed with two decimals. |
| Service | `PayslipLinkService.java`, `PayslipLinkServiceImpl.java` | Expose the bare token for the app (today only a full URL is returned). |
| Build | `code/backend/payroll/pom.xml` | OpenPDF. |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/payslips` (existing) | paging | 200 page of `PayslipSummaryResponse` `{payrun_id, period, paid_on, net_pay}` | `payroll.payslip.read_own` |
| GET | `/api/v1/me/payslips/{payrunId}` (existing) | — | 200 `PayslipResponse` | `payroll.payslip.read_own` |
| POST | `/api/v1/me/payslips/{payrunId}/link` (**new**) | — | 200 `{token, expires_at}` · 404 no payslip of the caller in that run · 409 as the JSON endpoint when the slip is not released | `payroll.payslip.read_own` |
| GET | `/api/v1/payroll/payslips/open?t=…&format=pdf` (**extended**) | — | 200 `application/pdf`, `Cache-Control: no-store`, `Content-Disposition: attachment; filename="payslip-<period>.pdf"` · 404 bad or expired token | signed token (public endpoint) |

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/package.json`, `android/`, `ios/` | Filesystem and Share plugins, synced. |
| `code/frontend/src/mobile/payslips/mobilePayslipService.js` | List, detail, link, download, validate, cache, share, cleanup. No text-to-"PDF" code. |
| `code/frontend/src/mobile/payslips/MobilePayslipsScreen.jsx` | List grouped by financial year. |
| `code/frontend/src/mobile/payslips/MobilePayslipDetailSheet.jsx` | Detail sheet. |
| `code/frontend/src/mobile/mobileAuth.js` | Logout calls the payslip cache cleanup. |
| `code/frontend/src/mobile/MobileShell.jsx` | Route. |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/me/payslips` | `MobilePayslipsScreen` | `MobileShell` (native only) |

## 6. Database changes

None. Reads `payroll.payrun`, `payroll.employee_payrun` and `payroll.employee_payrun_line` through the existing service.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `code/backend/payroll/src/test/java/com/infinevo/payroll/payslip/PayslipPdfWriterTest.java` (new) | Output starts with `%PDF`; text contains the employee name, period and net pay exactly as in the `PayslipResponse`. |
| Integration | `code/backend/payroll/src/test/java/com/infinevo/payroll/payslip/PayslipReadIT.java` | Link endpoint: own payslip 200, another employee's run 404, missing permission 403. `open?format=pdf`: 200 PDF, expired token 404, JSON answer unchanged without `format`. |
| Integration | `code/backend/payroll/src/test/java/com/infinevo/payroll/payslip/PayslipRlsIT.java` | A token from one tenant never yields another tenant's payslip. |
| Unit | `code/frontend/src/mobile/payslips/mobilePayslipService.test.js` | Rejects a non-PDF body; writes to `Directory.Cache`; cleanup deletes files; amounts not converted with `Number()`. |
| Component | `code/frontend/src/mobile/payslips/MobilePayslipsScreen.test.jsx` | Financial-year groups; loading, empty, error. |
| Component | `code/frontend/src/mobile/payslips/MobilePayslipDetailSheet.test.jsx` | Lines and totals as received. |

## 8. Verification

```bash
cd code/backend && ./mvnw -pl payroll -am verify
cd ../frontend && npm test -- src/mobile/payslips/ && npm run lint && npx cap sync
```

| Check | Expected | Result |
|---|---|---|
| Backend build and tests | Pass | not run |
| Frontend tests and lint | Pass, 0 warnings | not run |
| `curl` the open endpoint with `format=pdf` and a fresh token | A file that opens in a PDF reader and matches the web payslip line by line | not run |
| Same token after 5 minutes | 404 | not run |
| Tap PDF on an Android device | Share sheet opens with a readable PDF | not run |
| Tap PDF on iOS | Same | not run |
| Look in public Documents / Downloads | No payslip file | not run |
| Logout, then inspect app cache | No payslip file | not run |
| Payslips list | Grouped by financial year; net pay equals the web portal's | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| PDF and web payslip disagree | Medium | Both are built from the same `PayslipResponse`; the writer test compares values. |
| Salary data left on the phone | Medium | App-private cache only, deleted on logout and start. |
| Link token leaked | Low | 5-minute life, one payslip, `no-store`. |
| PDF library licence | Low | OpenPDF is LGPL/MPL; record the version and licence in the PR. |

## 10. Rollback

Remove the `/me/payslips` mobile route. The link endpoint and `format=pdf` are additive; the JSON answers are unchanged.

---

## Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| The "PDF" is a plain-text summary saved with a `.pdf` name; no backend PDF exists | `code/frontend/src/mobile/payslips/mobilePayslipService.js:83-117,137,181` |
| Filesystem and Share plugins are not synced into the native projects | `code/frontend/android/app/capacitor.build.gradle:11-18` · `code/frontend/android/capacitor.settings.gradle` · `code/frontend/ios/App/Podfile:11-21` |
| Salary data is written to public Documents and never deleted | `code/frontend/src/mobile/payslips/mobilePayslipService.js:144-153` |
| Amounts are passed through `Number()` | `code/frontend/src/mobile/payslips/mobilePayslipService.js:49,103-105` |
| The list is not grouped by financial year | `code/frontend/src/mobile/payslips/MobilePayslipsScreen.jsx` |
