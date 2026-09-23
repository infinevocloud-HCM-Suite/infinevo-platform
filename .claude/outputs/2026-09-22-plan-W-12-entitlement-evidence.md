# W-12 Subscription and Entitlement — Evidence

| Item | Exists | Where |
|---|---|---|
| Subscription/module concept | **NOT FOUND** | — |
| Organization entity status fields | Yes | Organization.java:81-85 |
| Setup steps tracking | Yes | OrgSetupSteps.java:15-24 |
| Navigation menu | Hard-coded (Payroll), role-driven (HRMS) | sidebar.js / Sidebar.jsx |

---

## Part B-1 — Payroll `organization` entity

### Table: `organization`
Columns: `id` (PK), `organizationId` (unique), `organizationName`, `businessLocation`, `industry`, `addressLine1`, `addressLine2`, `state`, `city`, `timezone`, `pinCode`, `createdBy`, `updatedBy`, `createdDate`, `updatedDate`, `isOrgActive` (Boolean), `isDeleted` (Boolean), `hasRunPayroll` (Boolean), `email`, `fileName`, `fileUrl`, `filePublicId`.

**Status/plan-related fields:**
- `isOrgActive` — Boolean flag, indicates org is active (line 82)
- `isDeleted` — Boolean flag, soft-delete (line 85)
- `hasRunPayroll` — Boolean flag, org has run at least one payroll (line 59)

**Payment/subscription fields:** NONE. No columns for plan, tier, subscription status, module entitlements, licence, or billing.

Where: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Organization.java:27-156`.

---

## Part B-2 — Subscription and Module Entitlement Concept

**NOT FOUND — this does not exist today.**

Grep for `subscription|module|licence|license|plan|entitlement` across Payroll codebase returned only 5 matches, all unrelated: `BenefitUtility` (benefits, not subscriptions), tax declaration, reimbursement service, and pay run DTOs. None reference customer subscriptions, module licenses, or feature entitlements.

**Conclusion:** There is no concept of customers buying or licensing modules (e.g., "Payroll-only" vs "HRMS-only" vs "integrated"). All tenants today are assumed to be full-feature. The target W-12 spec will need to build this from scratch.

Where: `legacy/Payroll-Bend-SBoot/src` (grep result: 5 files, none subscription-related).

---

## Part B-3 — Navigation Menu Building

### Payroll Frontend
Menu is **hard-coded.** `Sidebar` component at line 25 declares a static `menuLinks` array with Approvals, Taxes & Forms, Leave Management. No role-based filtering, no server-driven menu fetch. All admin users see the same menu structure.

Where: `legacy/Payroll-Fend-react/src/pages/pageLayouts/sidebarLayout/sidebar.js:25-69`.

### HRMS Frontend
Menu is **role-driven.** `Sidebar` component checks `activeRole` from context (line 51: `const role = activeRole?.toLowerCase()`), and renders different navigation links conditionally per role. For example, admin sees employee management, HR sees leave approval, employee sees only self-service timesheets and attendance.

Where: `legacy/HRMS_Frontend/src/components/Sidebar/Sidebar.jsx:50-51` (role check), `:68-120` (role-based navigation redirect logic).

---

## Part B-4 — `orgSetupSteps` Entity

### Table: `orgSetupSteps`
| Column | Type | Purpose |
|---|---|---|
| `id` | Long PK | |
| `organizationId` | String | Links to organization |
| `isWorkLocationSetup` | boolean | Work locations configured |
| `isEmployeeSetup` | boolean | At least one employee added |
| `isPayScheduleSetup` | boolean | Pay schedule defined |
| `isPriorPayrollSetup` | boolean | Prior payroll data imported |
| `isOrgTaxSetup` | boolean | Org tax details configured |
| `isSalaryComponentsSetup` | boolean | Salary components configured |
| `isEPFSetup` | boolean | EPF configuration complete |
| `isESISetup` | boolean | ESI configuration complete |
| `isPTAXSetup` | boolean | Professional tax configured |

Nine boolean flags track setup completion per organization. Used by onboarding UI to show checklist and guide new customers through configuration steps. No dependencies between steps; each is independent.

Where: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/OrgSetupSteps.java:6-58`.

---

## Summary

| Aspect | Status | Evidence |
|---|---|---|
| Subscription/module entitlement | **Not implemented** | No such fields or concepts in code; grep found 0 subscription-related entities |
| Organization status fields | Yes: `isOrgActive`, `isDeleted`, `hasRunPayroll` | Organization.java:81-85, :59 |
| Billing/plan fields | **Not present** | No plan, tier, SKU, or payment fields in organization entity |
| Setup progress tracking | Yes: 9 steps | OrgSetupSteps.java:15-24 |
| Navigation hard-coded or dynamic | Both exist: Payroll hard-coded, HRMS role-driven | sidebar.js:25-69, Sidebar.jsx:50-51 |

---

## Impact for W-12

W-12 must build subscription / module entitlement from scratch. Target design should add to `core.subscription` and `core.subscription_module` tables (per 02-data-model.md:122, :310). No existing auth check at endpoint level prevents HRMS access when Payroll-only — this is the "both halves" check mentioned in build order. Will require:
1. New `subscription` / `subscription_module` schema
2. Endpoint auth check: if user's org has no HRMS entitlement, reject HRMS API calls with 403
3. Frontend: conditionally render menu based on `subscription.modules` 
4. No existing cache or permission system blocks this work — clean slate
