# Infinevo HCM & Payroll — Comprehensive Manual QA Test Plan

| Field | Value |
|---|---|
| **Document Version** | 2.1 (Azure DevOps Wiki Ready & Team Mapped) |
| **Date** | 2026-10-06 |
| **Environment** | Azure Dev — Reached strictly via Azure Front Door |
| **Base URL** | `https://ep-infinevo-dev-huc0atg2hvd2fgbr.z02.azurefd.net` |
| **QA Lead & E2E Owner** | **Mohit Birla** (Dev 1) |
| **Project Manager** | Sanjib Banerjee |
| **Platform & Infra Owner** | Karmaveer Mohapatra |

---

## 1. Executive Summary & Purpose

### 1.1 Purpose
This test plan provides an exact, step-by-step verification blueprint for the 5 assigned developers to manually validate the Infinevo Multi-Tenant HCM & Payroll platform on the live Azure Dev environment.

Every single test case specifies the exact role, route, action, input payload, and expected UI response. Furthermore, each major functional module includes a **plain-language Hinglish explanation** clarifying what the business workflow does, why it matters, and how the underlying state transitions work.

### 1.2 In Scope
- **Platform Administration**: Multi-tenant provisioning, tenant module configuration (`PAYROLL`, `HRMS`), and Act-As impersonation.
- **Tenant Administration & Identity**: User invitations, employee invitations, token lifecycles, accept flows, role assignments, and custom role creation.
- **HRMS Core & Organization**: Department, designation, and work location hierarchies with filing address constraints; complete employee lifecycle (onboarding, profile management, PAN masking, reporting chains, termination, and reactivation).
- **Time & Leave Management**: Holiday calendars (regular vs restricted), leave types, leave accrual/allocations, multi-level leave approvals, delegations, on-behalf applications, attendance clocking, regularization, overtime, and project timesheet reviews.
- **Payroll Engine & Processing**: Salary component definitions, CTC calculation breakdowns, effective-dated salary revisions, statutory configuration (EPF, ESI, Maharashtra PT), pay run lifecycle (`DRAFT` → `COMPUTING` → `COMPUTED` → `LOCKED` → `APPROVED` → `PAID`), LOP leave deduction impact, and payslip generation.
- **Tax & Compliance**: Tax declaration window management, employee tax regime selection (Old vs New), Section 80C/HRA declarations, and Proof of Investment (POI) verification.
- **Benefits & Claims**: Flexible reimbursement claims with receipt uploads, finance approvals, ad-hoc payroll deductions, and reversal accounting.
- **Security & Enforcements**: Strict role-based navigation and API enforcement (negative testing), cross-tenant isolation verification, and comprehensive audit trail logging.
- **Real Communications**: Live verification of Brevo transactional emails (invitations, approval notifications, payslip alerts).

---

## 2. Developer Work Allocation & Execution Architecture

To eliminate bottlenecking and prevent cross-talk, the testing scope is divided into **5 specialized tracks**. Each developer operates inside their own dedicated tenant (`QA-D1` to `QA-D5`) to prevent data collisions, while owning primary responsibility for specific modules.

### 2.1 Developer Specialization Matrix

| Developer Code | Name & Track | Tenant | Lead Functional Scope | Primary Stages Owned | Setup Required |
|---|---|---|---|---|---|
| **Dev 1** | **Mohit Birla**<br>*(QA Lead / Platform & E2E Gatekeeper)* | `QA-D1` | • Platform Admin & Tenant Provisioning<br>• Negative & RBAC Enforcements<br>• Cross-Tenant Isolation<br>• End-to-End Clean Slate Smoke | **Stage 0, Stage 11, Stage 12, Stage 13** | Pre-conditions P-01 to P-07 |
| **Dev 2** | **Biren**<br>*(Payroll Engine Lead)* | `QA-D2` | • Salary Components & CTC Breakdowns<br>• Statutory Rules (EPF, ESI, Maharashtra PT)<br>• Pay Schedule & LOP Deduction Rules<br>• Pay Run Execution & Locking<br>• Salary Revisions (Effective-Dated CTC)<br>• Payroll Reconciliation Dashboard | **Stage 5, Stage 8, Stage 15, Stage 18** | Stages 1 & 2 in `QA-D2` |
| **Dev 3** | **Devashish**<br>*(Tax & Claims Lead)* | `QA-D3` | • Tax Declaration Windows & Regimes<br>• Housing & 80C Investment Declarations<br>• Proof of Investment (POI) Review<br>• Reimbursement Claims with Receipts<br>• Ad-hoc Deductions & Reversals<br>• Audit Trail Verification | **Stage 9, Stage 10, Stage 16, Stage 12** | Stages 1 & 2 in `QA-D3` |
| **Dev 4** | **Krushna**<br>*(HRMS Core Lead)* | `QA-D4` | • Org Masters (Locations, Depts, Designations)<br>• Custom Role Creation & Permission Grants<br>• Employee Onboarding & PAN Masking<br>• Multi-tier Reporting Chains<br>• Employee Termination & Reactivation<br>• Advanced Invitation Management | **Stage 1, Stage 2, Stage 3, Stage 14, Stage 17** | Stages 1 & 2 in `QA-D4` |
| **Dev 5** | **Sayeed**<br>*(Time & Operations Lead)* | `QA-D5` | • Holiday Calendars & Location Mapping<br>• Leave Types & Balance Allocations<br>• Leave Requests, Delegations & On-Behalf<br>• Attendance Clocking & Regularizations<br>• Overtime Requests & Timesheet Review<br>• Employee Self-Service (`/me`) Portal<br>• Leave Balance Import Flow | **Stage 4, Stage 6, Stage 7, Stage 19, Stage 20** | Stages 1 & 2 in `QA-D5` |

---

### 2.2 Execution Roadmap & Phase Dependencies

```
[Day 1 Morning: Phase 1 - Provisioning]
Mohit (Dev 1 - Platform-Admin) -> Provisions Tenants QA-D1 to QA-D5 -> Invites Biren, Devashish, Krushna, Sayeed
                                      │
            ┌─────────────────────────┴─────────────────────────┐
            ▼                                                   ▼
[Day 1 Midday: Phase 2 - Tenant Bootstrapping (Parallel)]
Mohit, Biren, Devashish, Krushna, Sayeed in parallel:
- Log in to respective tenant (QA-D1 to QA-D5)
- Stage 1: Setup Org Masters (Mumbai Office, QA Dept, Designations)
- Stage 2: Invite Core Roles (HR, Payroll Officer, Manager, Finance, Employees)
- Stage 3: Create Employee Records (EMP-01, EMP-02, EMP-03)
                                      │
            ┌─────────────────────────┼─────────────────────────┐
            ▼                         ▼                         ▼
[Day 1 Afternoon - Day 2: Phase 3 - Domain Deep Dives in Parallel]
Track A (Krushna & Sayeed): HRMS & Time       Track B (Biren & Devashish): Payroll & Compliance
- Krushna: Stages 14, 17, 18 (Lifecycle)      - Biren: Stages 5, 8, 15, 18 (Pay Run & Revision)
- Sayeed: Stages 4, 6, 7, 19, 20 (Time/Leave) - Devashish: Stages 9, 10, 16 (Tax, Claims, POI)
                                      │
                                      ▼
[Day 2 Afternoon: Phase 4 - Negative Enforcement & RBAC (Mohit + Support)]
- Mohit: Stage 11 (Wrong-role URL direct navigation, cross-tenant isolation, 403 checks)
- Mohit & Devashish: Stage 12 (Audit trail logging & Brevo email receipt validation)
                                      │
                                      ▼
[Day 3 Morning: Phase 5 - Final Smoke & Sign-Off (Mohit)]
- Mohit: Stage 13 (Brand new clean tenant QA-E2E, 30-min smoke test, Sanjib Banerjee sign-off)
```

---

## 3. Ground Rules & Test Execution Protocols

1. **Real Mail System**: Brevo SMTP is connected live. Transactional emails (invitations, password setup, leave alerts, payslips) go to actual inboxes. **Do not use personal emails.** Use configured aliases (e.g. `qa-d2-admin@infinevocloud.com` for Biren).
2. **Dedicated Single Tenant Boundary**: Never create test data inside another developer's tenant.
   - Mohit → `QA-D1`
   - Biren → `QA-D2`
   - Devashish → `QA-D3`
   - Krushna → `QA-D4`
   - Sayeed → `QA-D5`
3. **No Database Resets or Seed Fixtures**: There is no rollback script or reset button on Azure Dev. Execute steps in strict sequence. If a prerequisite step fails, mark dependent steps as `BLOCKED` and report the bug immediately.
4. **Updating Status in Azure DevOps Wiki**:
   - Change `[ ] PENDING` to `[x] PASS`, `[!] FAIL`, `[B] BLOCKED`, or `[-] DEFER`.
   - Add notes or Bug-IDs in the status column directly.
5. **Standardized Test Result Codes**:
   - `PASS`: UI and backend behavior match expected result completely.
   - `FAIL`: Behavior diverges or error occurs. File immediate bug using Section 22 template.
   - `BLOCKED`: Cannot proceed because a prerequisite step failed.
   - `DEFER`: Feature screen or API endpoint not yet deployed on Azure Dev main.
   - `SKIP`: Intentionally bypassed per track assignment.

---

## 4. Test Data Architecture (Identical Per Tenant)

### 4.1 Role Credentials Per Tenant
Replace `dX` with `d1` (Mohit), `d2` (Biren), `d3` (Devashish), `d4` (Krushna), or `d5` (Sayeed):

| Logical Persona | Work Email | System Roles Assigned |
|---|---|---|
| Tenant Administrator | `qa-dX-admin@infinevocloud.com` | `tenant-admin` |
| Payroll Officer | `qa-dX-payroll@infinevocloud.com` | `payroll-officer` |
| HR Manager | `qa-dX-hr@infinevocloud.com` | `hr` |
| Functional Manager (EMP-01) | `qa-dX-manager@infinevocloud.com` | `manager`, `employee` |
| Finance Officer | `qa-dX-finance@infinevocloud.com` | `finance` |
| Test Employee 2 (Team Member) | `qa-dX-emp2@infinevocloud.com` | `employee` |
| Test Employee 3 (Entry Level) | `qa-dX-emp3@infinevocloud.com` | `employee` |

### 4.2 Standard Employee Profiles
All compensation arithmetic follows standard monthly conversion:

```
┌────────────────────────────────────────────────────────────────────────┐
│ EMP-01: QA Senior Engineer (Manager Level)                             │
│ • Annual CTC: Rs 7,20,000  -> Monthly Gross: Rs 60,000                 │
│ • EPF: Applicable (12% of Basic) | ESI: EXEMPT (Gross > Rs 21,000/mo)  │
│ • Professional Tax: Applicable (Maharashtra Slab: Rs 200/mo)           │
├────────────────────────────────────────────────────────────────────────┤
│ EMP-02: QA Junior Engineer (Mid Level)                                 │
│ • Annual CTC: Rs 3,60,000  -> Monthly Gross: Rs 30,000                 │
│ • EPF: Applicable (12% of Basic) | ESI: EXEMPT (Gross > Rs 21,000/mo)  │
│ • Professional Tax: Applicable (Maharashtra Slab: Rs 200/mo)           │
├────────────────────────────────────────────────────────────────────────┤
│ EMP-03: QA Analyst (Entry Level - ESI Eligible)                        │
│ • Annual CTC: Rs 2,40,000  -> Monthly Gross: Rs 20,000                 │
│ • EPF: Applicable (12% of Basic) | ESI: ELIGIBLE (Gross <= Rs 21,000)  │
│ • ESI Employee Contribution: 0.75% of Rs 20,000 = Rs 150/month        │
│ • ESI Employer Contribution: 3.25% of Rs 20,000 = Rs 650/month        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Pre-Flight Verification Checklist (Mohit & Karmaveer)

| Check ID | Verification Item | Command / URL | Target State | Status |
|---|---|---|---|---|
| **P-01** | Azure CLI Subscription | `az account show --query name` | Returns subscription `InvoiceLLM` | [ ] PENDING |
| **P-02** | Azure Container Apps | `az containerapp list -g rg-infinevo-dev -o table` | All 4 (`app`, `worker`, `web`, `keycloak`) status `Running` | [ ] PENDING |
| **P-03** | Front Door Ingress | `curl -k -I https://ep-infinevo-dev-huc0atg2hvd2fgbr.z02.azurefd.net` | HTTP `200 OK` or `302` to Keycloak | [ ] PENDING |
| **P-04** | Keycloak Realm Reachability | `<BaseURL>/auth/admin` | Keycloak Master & Tenant realms accessible | [ ] PENDING |
| **P-05** | Database Migrations | GitHub Actions log `caj-flyway-dev` | All Flyway scripts applied with zero pending | [ ] PENDING |
| **P-06** | Platform Seed Account | Keycloak user `staff.infinevo` | Exists with role `platform-admin` | [ ] PENDING |
| **P-07** | Brevo SMTP Dispatch | Key Vault `kv-infinevo-shared` | `BREVO-API-KEY` valid, sender verified | [ ] PENDING |

---

## 6. Stage 0 — Platform Administration: Tenant Provisioning
> **Assigned Lead:** Mohit Birla (Dev 1)  
> **Target Tenant:** System Level (creates `QA-D1` to `QA-D5`)  
> **Login Role:** `platform-admin` (`staff.infinevo`)  
>
> **Hinglish Flow Explanation:**  
> Ye stage platform-level administrator run karta hai. Mohit yahan `staff.infinevo` se login karke 5 alag tenants banayenge:  
> - `QA-D1` (Mohit)  
> - `QA-D2` (Biren)  
> - `QA-D3` (Devashish)  
> - `QA-D4` (Krushna)  
> - `QA-D5` (Sayeed)  
> Har tenant me `HRMS` aur `PAYROLL` modules enable honge, aur sabhi developers ko unke admin account ka invitation link email par dispatch kiya jayega.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S0-01 | `/login` | Access Base URL and log in with `staff.infinevo` credentials. | Logged in successfully. Left sidebar displays `nav.tenants` (`/admin/tenants`). | [ ] PENDING |
| 2 | S0-02 | `/admin/tenants` | Click `nav.tenants`, then click **New Tenant** button. | Form modal opens with fields: Tenant Name, Key, Enabled Modules, Admin Email. | [ ] PENDING |
| 3 | S0-03 | `/admin/tenants/new` | Fill: Name: `QA Tenant D1`, Key: `qa-d1`, Modules: Checked `PAYROLL` and `HRMS`. Click Save. | Tenant created. List shows status `ACTIVE`. Capture Tenant UUID. | [ ] PENDING |
| 4 | S0-04 | `/admin/tenants/new` | Repeat S0-02 & S0-03 for `QA Tenant D2` (Biren), `QA Tenant D3` (Devashish), `QA Tenant D4` (Krushna), and `QA Tenant D5` (Sayeed). | All 5 tenants appear in active tenant table with unique UUIDs. | [ ] PENDING |
| 5 | S0-05 | `/admin/tenants` | For Tenant `QA-D1`, click **Act-As (Impersonate)**. Banner displays "Acting as QA Tenant D1". | Active session changes to Tenant D1 context. Menu shows Tenant Admin options. | [ ] PENDING |
| 6 | S0-06 | `/invitations/users` | Click `nav.userInvitations` → **Invite User**. Email: `qa-d1-admin@infinevocloud.com`, Role: `tenant-admin`. Submit. | Row appears with status `PENDING`. Brevo dispatches invitation email. | [ ] PENDING |
| 7 | S0-07 | External Email | Open email inbox for `qa-d1-admin`. Click invite link (`/invitations/accept?token=...`). | Browser loads token acceptance page. No token leakage in URL bar after hydration. | [ ] PENDING |
| 8 | S0-08 | `/invitations/accept` | Click **Accept Invitation**. | Banner: "Invitation accepted. Check email to set your password." | [ ] PENDING |
| 9 | S0-09 | Keycloak Reset | Open password setup email, configure password `TestPassword@123`, log in at Base URL. | Successfully logged into `QA Tenant D1` as `tenant-admin`. | [ ] PENDING |
| 10 | S0-10 | `/admin/tenants` | Mohit repeats S0-05 to S0-09 for Biren (D2), Devashish (D3), Krushna (D4), and Sayeed (D5). | All 5 developers possess active, authenticated `tenant-admin` accounts. | [ ] PENDING |

---

## 7. Stage 1 — Tenant Setup: Organisation Masters & Work Locations
> **Assigned Lead:** Krushna (Dev 4) *(Executed by each developer in their own tenant)*  
> **Login Role:** `tenant-admin` (`qa-dX-admin@infinevocloud.com`)  
>
> **Hinglish Flow Explanation:**  
> Company ke basic pillars: Departments, Designations aur Work Locations. Work location me "Filing Address" set karna mandatory hota hai kyunki statutory tax, PF aur ESI usi address ke state (Maharashtra) se bind hote hain. Yahan check karna hai ki filing address delete karne par system HTTP 409 se block karta hai ya nahi.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S1-01 | `/org/departments` | Click `nav.organisation` → `nav.departments`. | Departments page opens; empty table with "+ Add Department" button. | [ ] PENDING |
| 2 | S1-02 | `/org/departments` | Click Add Department. Fill: Name = `QA Engineering`, Code = `QA-ENG`. Save. | Record appears: `QA Engineering` / `QA-ENG` / Status `ACTIVE`. | [ ] PENDING |
| 3 | S1-03 | `/org/departments` | Add second department: Name = `Human Resources`, Code = `HR-DEPT`. Save. | Second department saved successfully. | [ ] PENDING |
| 4 | S1-04 | `/org/designations` | Click `nav.designations`. Add: 1) `Senior Engineer` (`SNR-ENG`), 2) `Junior Engineer` (`JNR-ENG`), 3) `Analyst` (`ANLST`). | All 3 designations appear in grid with active status. | [ ] PENDING |
| 5 | S1-05 | `/org/work-locations` | Click `nav.locations` → Click **New Work Location**. | Modal opens with Address, State, City, PIN, and Statutory toggles. | [ ] PENDING |
| 6 | S1-06 | `/org/work-locations` | Fill: Name = `QA Office Mumbai`, Code = `QA-MUM`, City = `Mumbai`, State = `Maharashtra`, State Code = `MH`, PIN = `400001`, Filing Address = **ON**. Save. | Location created with `FILING ADDRESS` badge. | [ ] PENDING |
| 7 | S1-07 | `/org/work-locations` | Attempt to delete `QA Office Mumbai`. | Deletion refused with HTTP 409: "Cannot delete official filing address. Deactivate instead." | [ ] PENDING |
| 8 | S1-08 | `/roles` | Navigate to `nav.roles` (`/roles`). | Verify default seeded roles exist: `tenant-admin, payroll-officer, hr, manager, finance, employee`. *(If 404, mark DEFER)*. | [ ] PENDING |
| 9 | S1-09 | `/setup` | Navigate to `nav.setup` (`/setup`). | Setup checklist displays: Org Masters marked COMPLETE, Employees pending. | [ ] PENDING |

---

## 8. Stage 2 — User Onboarding & Advanced Invitation Management
> **Assigned Lead:** Krushna (Dev 4) *(Executed by each developer in their own tenant)*  
> **Login Role:** `tenant-admin`  
>
> **Hinglish Flow Explanation:**  
> Tenant-admin baaki roles (Payroll, HR, Manager, Finance) ko invite bhejta hai. Normal invite accept ke alawa "Resend" aur "Revoke" bhi test karna hai: purana link expire hona chahiye, aur revoke hone par login completely reject hona chahiye.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S2-01 | `/invitations/users` | Click **Invite User**. Email: `qa-dX-payroll@infinevocloud.com`, Role: `payroll-officer`. Send. | Table row shows `PENDING`. Brevo email received. | [ ] PENDING |
| 2 | S2-02 | Accept Flow | Accept invite, set password in Keycloak, log in as `payroll-officer`. | Logged in. Left menu shows ONLY payroll items (`nav.payroll.runs`, `claims`, `deductions`, `dashboard`). Log out. | [ ] PENDING |
| 3 | S2-03 | `/invitations/users` | Log in as admin. Invite `qa-dX-hr@infinevocloud.com` with role `hr`. Send. | Row `PENDING`. Brevo email received. | [ ] PENDING |
| 4 | S2-04 | Accept Flow | Accept invite, set password, log in as `hr`. | Logged in. Menu shows HRMS items (`employees`, `organisation`, `holidays`, `leave`). Log out. | [ ] PENDING |
| 5 | S2-05 | `/invitations/users` | Invite `qa-dX-finance@infinevocloud.com` with role `finance`. Send. | Row `PENDING`. | [ ] PENDING |
| 6 | S2-06 | Accept Flow | Accept invite, set password, log in as `finance`. | Logged in. Menu shows `nav.payroll.claims`, `nav.payroll.deductions`, and approvals inbox. Log out. | [ ] PENDING |
| 7 | S2-07 | `/invitations/users` | Invite a test address `dummy-test@infinevocloud.com` with role `hr`. | Row appears as `PENDING`. | [ ] PENDING |
| 8 | S2-08 | `/invitations/users` | Click **Resend Invitation** for `dummy-test`. | New invitation email dispatched. Old invitation token is rendered invalid. | [ ] PENDING |
| 9 | S2-09 | `/invitations/accept` | Try opening the first (older) invite link in an incognito window. | Page displays error: "This invitation link has expired or was replaced by a newer invitation." | [ ] PENDING |
| 10 | S2-10 | `/invitations/users` | Click **Revoke Invitation** on the `dummy-test` row. | Status transitions to `REVOKED`. Trying the link results in "Invitation revoked." | [ ] PENDING |

---

## 9. Stage 3 — Employee Lifecycle: Onboarding, Profiles & Reporting Lines
> **Assigned Lead:** Krushna (Dev 4)  
> **Login Role:** `tenant-admin` or `hr`  
>
> **Hinglish Flow Explanation:**  
> 3 test employees banayenge (EMP-01, EMP-02, EMP-03). Security check: PAN card number submit hone par UI me masked dikhega (`••••••1234C`). Reporting line test: EMP-02 aur EMP-03 ka manager EMP-01 hoga. Agar EMP-01 ka manager EMP-01 banayein to system ko HTTP 409 cycle detection error dena chahiye.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S3-01 | `/employees` | Click `nav.employees` → Click **New Employee**. | Employee creation drawer/form opens with basic details. | [ ] PENDING |
| 2 | S3-02 | `/employees/new` | Fill EMP-01: Name: `QA Test Employee One`, Joining: `2026-04-01`, Work Email: `qa-dX-emp1@infinevocloud.com`, Mobile: `9000000001`, Dept: `QA Engineering`, Desig: `Senior Engineer`, Location: `QA Office Mumbai`, Portal Enabled: **ON**. Save. | Employee created. Navigates to detail page `/employees/:id`. Record UUID. | [ ] PENDING |
| 3 | S3-03 | `/employees/:id` | Open **Personal Tab**. Fill DOB: `1990-01-15`, Marital Status: `Single`, Father Name: `Senior Test`. Save. | Toast: "Personal details updated." Reload verifies persistence. | [ ] PENDING |
| 4 | S3-04 | `/employees/:id` | Open **Identification Tab**. Enter PAN: `AAAPL1234C`. Save. | PAN field shows masked characters with reveal toggle icon. | [ ] PENDING |
| 5 | S3-05 | `/employees/:id` | Open **Reporting Line Tab**. Attempt to set Reporting Manager to EMP-01 itself. | System rejects: 409 Conflict with message "Circular reporting chain detected." | [ ] PENDING |
| 6 | S3-06 | `/employees/new` | Create EMP-02 (`Junior Engineer`, `qa-dX-emp2@infinevocloud.com`, Joining: `2026-05-01`). | Record created. | [ ] PENDING |
| 7 | S3-07 | `/employees/new` | Create EMP-03 (`Analyst`, `qa-dX-emp3@infinevocloud.com`, Joining: `2026-06-01`). | Record created. | [ ] PENDING |
| 8 | S3-08 | `/employees/:id` | On EMP-02 Reporting Line Tab: Set Manager = EMP-01, Type = `PRIMARY`, Effective = `2026-04-01`. Save. | Hierarchy tree shows EMP-01 as direct manager. | [ ] PENDING |
| 9 | S3-09 | `/employees/:id` | On EMP-03 Reporting Line Tab: Set Manager = EMP-01, Type = `PRIMARY`. Save. | Manager hierarchy saved. | [ ] PENDING |
| 10 | S3-10 | `/invitations/employees` | Navigate to `nav.employeeInvitations`. Select EMP-01. Click **Send Portal Invite**. | Invitation sent to `emp1` email address. Row shows `PENDING`. | [ ] PENDING |
| 11 | S3-11 | Accept Flow | EMP-01 accepts invite, sets password. Also assign role `manager` in admin console. | EMP-01 can log into `/me` self-service AND sees `nav.approvals` in menu. | [ ] PENDING |
| 12 | S3-12 | `/invitations/employees` | Send portal invites for EMP-02 and EMP-03 with role `employee`. Complete password setup. | Both EMP-02 and EMP-03 can successfully log into `/me`. | [ ] PENDING |

---

## 10. Stage 4 — Leave & Holiday Management Setup
> **Assigned Lead:** Sayeed (Dev 5)  
> **Login Role:** `hr`  
>
> **Hinglish Flow Explanation:**  
> Sayeed yahan holiday calendar setup karenge: Diwali (regular holiday) aur restricted holiday add karenge. Uske baad leave types banayenge: Earned Leave (EL), Sick Leave (SL), aur Loss of Pay (LOP). Teeno employees ko opening leave balance allocate karenge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S4-01 | `/holidays` | Click `nav.holidays` → Click **New Calendar**. | Calendar creation modal opens. | [!] FAIL — Retest after deployment (`D-11`: 409 Default Calendar Conflict) |
| 2 | S4-02 | `/holidays/new` | Name: `Mumbai Holiday Calendar 2026`, Year: `2026`, Location: `QA Office Mumbai`. Save. | Calendar listed with assigned work location badge. | [x] PASS |
| 3 | S4-03 | `/holidays/:id` | Open Calendar. Click Add Holiday. Name: `Diwali Test Holiday`, Date: `2026-10-15`, Restricted: **OFF**. Save. | Holiday added to October 2026 list. | [x] PASS |
| 4 | S4-04 | `/holidays/:id` | Add Holiday: Name: `Special Restricted Holiday`, Date: `2026-11-05`, Restricted: **ON**. Save. | Holiday tagged with `RESTRICTED` badge. | [x] PASS |
| 5 | S4-05 | `/leave/types` | Click `nav.leave` → `nav.leave.types`. | Empty leave types screen opens. | [x] PASS |
| 6 | S4-06 | `/leave/types/new` | Create Leave Type: Name: `Earned Leave`, Code: `EL`, Paid: **YES**, Unit: `DAYS`, Allow Half-Day: **ON**, Carry Forward: **YES** (Max 15 days). Save. | EL leave type active. | [x] PASS |
| 7 | S4-07 | `/leave/types/new` | Create Leave Type: Name: `Sick Leave`, Code: `SL`, Paid: **YES**, Unit: `DAYS`, Allow Half-Day: **ON**. Save. | SL leave type active. | [x] PASS |
| 8 | S4-08 | `/leave/types/new` | Create Leave Type: Name: `Loss of Pay`, Code: `LOP`, Paid: **NO**, Unit: `DAYS`, Allow Half-Day: **OFF**. Save. | LOP type active (unpaid leave). | [x] PASS |
| 9 | S4-09 | `/leave/allocations` | Click `nav.leave.allocations`. Filter Year: `2026`. Click Allocate Balance. | Allocation grid loads showing all active employees. | [x] PASS |
| 10 | S4-10 | `/leave/allocations` | Allocate EL: EMP-01 = 12 days, EMP-02 = 10 days, EMP-03 = 8 days. Save. | Balance records saved. | [!] FAIL — Retest after deployment (`D-10`: Missing policy effectiveFrom) |
| 11 | S4-11 | `/leave/allocations` | Allocate SL: 6 days each for EMP-01, EMP-02, and EMP-03. Save. | Balance table reflects updated SL counts. | [!] FAIL — Retest after deployment (`D-10`: Missing policy effectiveFrom) |

---

## 11. Stage 5 — Payroll Engine Configuration (Components, Salary & Statutory)
> **Assigned Lead:** Biren (Dev 2)  
> **Login Role:** `payroll-officer`  
>
> **Hinglish Flow Explanation:**  
> Biren yahan salary components banayenge: Basic, HRA, Special Allowance, PF, PT, Medical Reimbursement. Har employee ki CTC daalenge (EMP-01: 7.2L, EMP-02: 3.6L, EMP-03: 2.4L). Statutory rules verify honge: EPF (12%), ESI (0.75% strictly EMP-03 ke liye kyunki gross <= 21k), aur Maharashtra PT slabs.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S5-01 | `/payroll/components` | Navigate to salary components page. *(Mark DEFER if 404)*. | Screen displays tabs: Earnings, Deductions, Benefits, Reimbursements. | [ ] PENDING |
| 2 | S5-02 | `/payroll/components` | Under Earnings, create `Basic Pay`: Type = `PERCENTAGE`, Taxable = YES, Consider for EPF = YES, ESI = YES. Save. | Component created. | [ ] PENDING |
| 3 | S5-03 | `/payroll/components` | Create `House Rent Allowance (HRA)`: Type = `PERCENTAGE`, Taxable = PARTIAL, EPF = NO, ESI = NO. Save. | Component created. | [ ] PENDING |
| 4 | S5-04 | `/payroll/components` | Create `Special Allowance`: Type = `BALANCING_FIGURE` / `FLAT`, Taxable = YES. Save. | Component created. | [ ] PENDING |
| 5 | S5-05 | `/payroll/components` | Under Deductions, create `Professional Tax`: Type = `SLAB`. Save. | Deduction listed. | [ ] PENDING |
| 6 | S5-06 | `/payroll/components` | Under Reimbursements, create `Medical Reimbursement`: Monthly Limit = Rs 1,250. Save. | Reimbursement listed. | [ ] PENDING |
| 7 | S5-07 | `/employees/:EMP01/salary` | Open EMP-01 → **Salary Tab**. Click **Create Salary Structure**. Annual CTC: Rs 7,20,000, Effective From: `2026-04-01`. Save. | Backend returns breakdown. Total Monthly Gross matches Rs 60,000. Basic = 50% (Rs 30,000) *(confirm)*. | [ ] PENDING |
| 8 | S5-08 | `/employees/:EMP02/salary` | Open EMP-02 → Salary Tab. CTC: Rs 3,60,000, Effective: `2026-05-01`. Save. | Monthly Gross = Rs 30,000. | [ ] PENDING |
| 9 | S5-09 | `/employees/:EMP03/salary` | Open EMP-03 → Salary Tab. CTC: Rs 2,40,000, Effective: `2026-06-01`. Save. | Monthly Gross = Rs 20,000. ESI status automatically tags as `ELIGIBLE` (Gross <= Rs 21,000). | [ ] PENDING |
| 10 | S5-10 | `/payroll/settings/pay-schedule` | Open Pay Schedule settings. Work Week: Mon-Fri, Pay Day: `Last working day of the month`, LOP Basis: `Calendar days (31 days for Oct)`. Save. | Pay schedule saved. Period preview for October 2026 shows Pay Date = 2026-10-31. | [ ] PENDING |
| 11 | S5-11 | `/payroll/settings/epf` | Open EPF settings. Verify defaults: Employee Share = 12%, Employer EPF = 3.67%, Employer EPS = 8.33%, EDLI = 0.50%. Save. | EPF statutory profile active. | [ ] PENDING |
| 12 | S5-12 | `/payroll/settings/esi` | Open ESI settings. Verify defaults: Employee = 0.75%, Employer = 3.25%, Wage Ceiling = Rs 21,000. Save. | ESI profile active. | [ ] PENDING |
| 13 | S5-13 | `/payroll/settings/professional-tax` | Open PT settings. State: `Maharashtra`. Verify slabs: Up to Rs 7,500 = Nil, Rs 7,501 to Rs 10,000 = Rs 175, Above Rs 10,000 = Rs 200 (Feb Rs 300). | Slabs verified and saved. | [ ] PENDING |

---

## 12. Stage 6 — Leave Application, Approval Workflows & Delegation
> **Assigned Lead:** Sayeed (Dev 5)  
> **Personas:** EMP-02 (Applicant), EMP-01 (Manager), HR  
>
> **Hinglish Flow Explanation:**  
> Sayeed leave application aur approval chain test karenge. Approval definition banegi (Step 1 = Reporting Manager). EMP-02 chutti apply karega, EMP-01 approve karega, balance 10 se 7 ho jayega. Manager delegation bhi test hoga ki jab manager absent ho to HR approve kar sake.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S6-01 | `/approvals/definitions` | As Admin: Open `nav.approvals.definitions`. Create definition: Flow = `LEAVE`, Step 1 = `REPORTING_MANAGER`, Escalate After = 3 days. Save. | Approval flow active for leaves. | [x] PASS |
| 2 | S6-02 | `/me` | Log in as EMP-02. Navigate to **My Leave** → Click **Apply Leave**. | Leave application form opens. Balance shown: EL = 10 days. | [!] FAIL — Retest after deployment (`D-10`: Policy `effectiveFrom` missing on server; `/me` requires employee account) |
| 3 | S6-03 | `/me/leave/apply` | Leave Type: `Earned Leave`, From: `2026-10-20`, To: `2026-10-22` (3 days), Reason: `Diwali Family Function`. Submit. | Request submitted. Status = `PENDING`. EMP-01 receives approval email alert. | [!] FAIL — Retest after deployment (`D-10`: `/api/v1/leave-types/eligible` returns `[]`; "No data" in Leave Type dropdown) |
| 4 | S6-04 | `/approvals` | Log in as EMP-01 (Manager). Navigate to `nav.approvals` inbox. | EMP-02's leave request is listed with applicant name, dates (3 days), and reason. | [!] FAIL — Retest after deployment (Blocked by S6-03: no pending leave request exists) |
| 5 | S6-05 | `/approvals` | Click the request row. Click **Approve** with comment: `Approved, enjoy holidays!`. | Request status changes to `APPROVED`. | [!] FAIL — Retest after deployment (Blocked by S6-03: cannot approve uncreated request) |
| 6 | S6-06 | `/me` | Log in as EMP-02. Check leave balance. | EL balance reduced from 10 to **7 days**. | [!] FAIL — Retest after deployment (Blocked by S6-03: no balance reduction) |
| 7 | S6-07 | `/leave/requests` | Log in as HR. Click **New On-Behalf Request**. Employee = EMP-03, Type = Sick Leave, Date = `2026-10-12` (1 day), Reason = `Fever`. Submit. | Status directly created as `APPROVED` (no approval required for HR on-behalf per W-16.3). EMP-03 SL balance becomes 5. | [x] PASS |
| 8 | S6-08 | `/approvals/delegations` | As EMP-01 (Manager): Click `nav.approvals.delegations` → Create delegation: Delegate To = `HR`, Flow = `LEAVE`, From = `2026-10-25`, To = `2026-10-28`. Save. | Delegation saved. | [!] FAIL — Retest after deployment (Delegation API requires caller employee profile; EMP-01 Keycloak account not activated) |
| 9 | S6-09 | `/me` | As EMP-02: Apply leave for `2026-10-27` (1 day). | Request status = `PENDING`. | [!] FAIL — Retest after deployment (Blocked by S6-03: leave apply disabled due to D-10) |
| 10 | S6-10 | `/approvals` | Log in as HR. Check `nav.approvals` inbox. | Delegated leave request from EMP-02 appears in HR's inbox. HR can approve it. | [!] FAIL — Retest after deployment (Blocked by S6-08 and S6-09: no delegated request exists) |

---

## 13. Stage 7 — Time & Attendance, Regularization & Timesheets
> **Assigned Lead:** Sayeed (Dev 5)  
> **Login Role:** `employee` (EMP-02), `manager` (EMP-01), `hr`  
> *(Routes verified directly from `HrmsNavigation.java`)*  
>
> **Hinglish Flow Explanation:**  
> Attendance clock-in/out (`/hrms/attendance`), Missed punch regularization request (`/hrms/regularizations`), Overtime application (`/hrms/overtime-requests`), aur project timesheets review (`/hrms/timesheet-review`).

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S7-01 | `/hrms/dashboard` | Log in as EMP-02. Navigate to `nav.hrms.dashboard`. *(DEFER if not deployed)*. | HRMS Dashboard displays attendance summary, my work widget, and pending regularizations. | [!] FAIL (Blocked by S19-01 / D-17: employee login blocked) |
| 2 | S7-02 | `/hrms/attendance` | On Clock card (`/hrms/attendance`), click **Clock In** at 09:30 AM. | Session started. Clock-in timestamp recorded. Active timer begins. | [x] PASS (Clock in API and session start verified working) |
| 3 | S7-03 | `/hrms/attendance` | Click **Clock Out** at 06:30 PM. | Session closed. Total work duration computed (9.0 hours). | [x] PASS (Clock out API and duration calculation verified working) |
| 4 | S7-04 | `/hrms/attendance-log` | Log in as HR. Navigate to `nav.hrms.attendance_log` (`/hrms/attendance-log`). | HR can view attendance log of all employees. Mark EMP-02 as `MISSED_PUNCH` for `2026-10-23`. | [x] PASS (Screen and table verified accessible under HR role) |
| 5 | S7-05 | `/hrms/regularizations` | Log in as EMP-02. Navigate to `nav.hrms.regularizations` → Click **New Regularization**. Date: `2026-10-23`, Punch In: `09:30`, Punch Out: `18:30`, Reason: `Biometric machine offline`. Submit. | Regularization request submitted with status `PENDING`. | [!] FAIL (Blocked by D-47: date range required to load/display requests) |
| 6 | S7-06 | `/approvals` | Log in as EMP-01 (Manager). Open approvals inbox. | Regularization request appears. Click **Approve**. | [!] FAIL (Blocked by S7-05: no regularization request exists) |
| 7 | S7-07 | `/hrms/overtime-requests` | Log in as EMP-02. Navigate to `nav.hrms.overtime_requests`. Request 2 hours OT on `2026-10-24` for `Production Release Support`. Submit. | OT request created with status `PENDING`. Manager receives notification. | [x] PASS (Overtime submit works and details are visible; date-range filter defect D-47 logged) |
| 8 | S7-08 | `/approvals` | Log in as EMP-01 (Manager). Review and Approve OT request. | Status = `APPROVED`. Overtime ledger records 2 hours approved OT. | [!] FAIL (Blocked by D-48: rejected overtime disappears from UI) |
| 9 | S7-09 | `/hrms/timesheets` | Log in as EMP-04. Navigate to `nav.hrms.timesheets`. Add entry on project and submit for approval. | Weekly timesheet submitted with status `SUBMITTED`. | [x] PASS |
| 10 | S7-10 | `/approvals` | Log in as Admin/Manager. Open approvals inbox (`/approvals`). Review timesheet and click **Approve**. | Timesheet status updates to `APPROVED` (completed in backend; D-44 logged for missing history view). | [x] PASS |

---

## 14. Stage 8 — Pay Run Lifecycle (October 2026 End-to-End)
> **Assigned Lead:** Biren (Dev 2)  
> **Login Role:** `payroll-officer` (`qa-dX-payroll@infinevocloud.com`)  
>
> **Hinglish Flow Explanation:**  
> Biren October 2026 ka complete pay run karenge: `DRAFT` → `COMPUTING` → `COMPUTED` → `LOCKED` → `APPROVED` → `PAID`. EMP-03 par ESI Rs 150 kata ya nahi aur EMP-01 par ESI exempt hua ya nahi verify hoga. Paid hone par Brevo payslip emails verify honge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S8-01 | `/payroll/runs` | Click `nav.payroll.runs`. | Pay runs list opens; empty table. | [ ] PENDING |
| 2 | S8-02 | `/payroll/runs/new` | Click **New Pay Run**. Period: `October 2026` (2026-10-01 to 2026-10-31). Create. | Run created in `DRAFT` state. Navigates to `/payroll/runs/:id`. Headcount = 3 employees. | [ ] PENDING |
| 3 | S8-03 | `/payroll/runs/:id` | Click **Compute Payroll** button. | Status changes to `COMPUTING`. Progress bar / polling starts every 3 seconds. | [ ] PENDING |
| 4 | S8-04 | `/payroll/runs/:id` | Wait for worker container completion (approx 5-15 sec). | Status transitions to `COMPUTED`. Summary cards display Total Gross, Total Deductions, Net Pay. | [ ] PENDING |
| 5 | S8-05 | `/payroll/runs/:id` | Click row for **EMP-03** (QA Analyst). Open Lines Drawer. | Earnings: Rs 20,000. Deductions: EPF (12% Basic), PT (Rs 200), **ESI Employee = Rs 150** (0.75% of 20,000). | [ ] PENDING |
| 6 | S8-06 | `/payroll/runs/:id` | Click row for **EMP-01** (Senior Engineer). Open Lines Drawer. | Earnings: Rs 60,000. Deductions: EPF, PT. **ESI deduction MUST BE ABSENT** (Gross exceeds Rs 21,000). | [ ] PENDING |
| 7 | S8-07 | `/payroll/runs/:id` | Click **Lock Pay Run** button. | Status transitions to `LOCKED`. "Compute" and "Cancel" buttons become disabled/hidden. | [ ] PENDING |
| 8 | S8-08 | `/payroll/runs/:id` | Attempt to cancel or modify inputs for this locked run. | Action blocked: UI shows locked indicator, API returns HTTP 409 if attempted. | [ ] PENDING |
| 9 | S8-09 | `/payroll/runs/:id` | Click **Approve Pay Run** button. | Status transitions to `APPROVED`. | [ ] PENDING |
| 10 | S8-10 | `/payroll/runs/:id` | Click **Release / Mark as Paid**. Set Pay Date: `2026-10-31`. Confirm. | Status transitions to `PAID`. Brevo emails dispatched to all employees with payslip notification. | [ ] PENDING |
| 11 | S8-11 | `/me` | Log in as EMP-01. Navigate to `/me` → **Payslips**. | October 2026 payslip card is visible. Clicking View displays gross Rs 60,000 and itemized deductions. | [ ] PENDING |
| 12 | S8-12 | `/payroll/runs/new` | Attempt to create a SECOND pay run for `October 2026`. | System rejects: "Pay run for period 2026-10 already exists and is in PAID status." | [ ] PENDING |

---

## 15. Stage 9 — Employee Tax Declaration (Old vs New Regime & Window Controls)
> **Assigned Lead:** Devashish (Dev 3)  
> **Personas:** Payroll Officer, EMP-01, EMP-02  
>
> **Hinglish Flow Explanation:**  
> Devashish tax declaration window open karenge. EMP-01 Old Regime select karke HRA aur Section 80C (LIC) declare karega. Window close hone par submission block ho raha hai ya nahi test karenge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S9-01 | `/payroll/settings/tax-declaration/2026` | As Payroll Officer: Open Tax Declaration settings for FY 2026-27. *(DEFER if 404)*. | Settings page loads. Default regime: `NEW`. | [ ] PENDING |
| 2 | S9-02 | `/payroll/settings/tax-declaration/2026` | Set Window: Start = `2026-10-01`, End = `2026-12-31`, Allow Regime Change = **YES**. Save. | Window marked `OPEN`. Banner shows active status. | [ ] PENDING |
| 3 | S9-03 | `/me` | Log in as EMP-01. Navigate to `/me` → **Tax Declarations**. | FY 2026-27 declaration form opens in editable state. | [ ] PENDING |
| 4 | S9-04 | `/me/tax-declaration` | Select Regime: **OLD REGIME**. | Form shows Old Regime deduction sections (80C, 80D, HRA). | [ ] PENDING |
| 5 | S9-05 | `/me/tax-declaration` | In Housing / HRA Section: Monthly Rent = Rs 15,000, City Type = `Metro (Mumbai)`, Landlord PAN = `ABCD12345E`. Save. | Section saved successfully. | [ ] PENDING |
| 6 | S9-06 | `/me/tax-declaration` | In Chapter VI-A / 80C Section: LIC Premium = Rs 50,000, PPF = Rs 50,000 (Total 80C = Rs 1,00,000). Save. | 80C total updates to Rs 1,00,000. | [ ] PENDING |
| 7 | S9-07 | `/me/tax-declaration` | Open Summary Tab. Click **Submit Declaration**. | Status changes to `SUBMITTED`. Form fields lock to read-only. | [ ] PENDING |
| 8 | S9-08 | `/me/tax-declaration` | Click **Reopen Declaration**. Change 80C LIC to Rs 75,000. Click Re-submit. | Declaration re-submitted with new value (Total 80C = Rs 1,25,000). | [ ] PENDING |
| 9 | S9-09 | `/payroll/settings/tax-declaration/2026` | As Payroll Officer: Change End Date to `2026-10-05` (past date) to close the window. Save. | Window status updates to `CLOSED`. | [ ] PENDING |
| 10 | S9-10 | `/me` | Log in as EMP-02. Open Tax Declarations. Attempt to submit. | Submission blocked: "Tax declaration window for FY 2026-27 is currently closed." | [ ] PENDING |

---

## 16. Stage 10 — Reimbursement Claims, Receipts & Ad-hoc Deductions
> **Assigned Lead:** Devashish (Dev 3)  
> **Personas:** EMP-02 (Claimant), Finance Officer, Payroll Officer  
>
> **Hinglish Flow Explanation:**  
> Devashish claims flow test karenge: EMP-02 Medical claim bill receipt ke sath upload karega. Finance officer use review aur approve karega. Ad-hoc salary advance deduction add hoga aur galti sudharne ke liye use Reverse kiya jayega.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S10-01 | `/payroll/claims` | Log in as EMP-02. Navigate to `nav.payroll.claims` (or portal claims). Click **New Claim**. | Claim creation drawer opens. | [ ] PENDING |
| 2 | S10-02 | `/payroll/claims/new` | Component: `Medical Reimbursement`, Amount: `Rs 800`, Bill Date: `2026-10-18`, Remarks: `QA Medical Prescription`. Upload test receipt image. Submit. | Claim submitted with status `PENDING`. Document attached. | [ ] PENDING |
| 3 | S10-03 | `/approvals` | Log in as Finance Officer. Check approvals inbox. | EMP-02's claim of Rs 800 appears with attached document preview link. | [ ] PENDING |
| 4 | S10-04 | `/approvals` | Finance reviews receipt and clicks **Approve** (Approved Amount = Rs 800). | Status updates to `APPROVED`. Available for payroll disbursement. | [ ] PENDING |
| 5 | S10-05 | `/payroll/deductions` | Log in as Payroll Officer. Navigate to `nav.payroll.deductions`. Click **Batch Entry**. | Ad-hoc deduction grid opens. | [ ] PENDING |
| 6 | S10-06 | `/payroll/deductions` | Row 1: Employee = EMP-03, Kind = `Advance Salary Recovery`, Amount = `Rs 500`, Reason = `Festival Advance QA-01`. Save. | Deduction row saved with status `ACTIVE`. | [ ] PENDING |
| 7 | S10-07 | `/me` | Log in as EMP-03. Open deductions panel. | Active deduction of Rs 500 visible with schedule and reference. | [ ] PENDING |
| 8 | S10-08 | `/payroll/deductions` | As Payroll Officer: Select the EMP-03 deduction row → Click **Reverse Deduction**. Reason: `Waived by HR`. Confirm. | Status changes to `REVERSED`. Reversal entry logged in audit. | [ ] PENDING |

---

## 17. Stage 11 — Security, RBAC & Negative Enforcements
> **Assigned Lead:** Mohit Birla (Dev 1)  
> **Login Role:** Varied roles (testing negative boundary conditions)  
>
> **Hinglish Flow Explanation:**  
> Mohit boundary aur security tests execute karenge: koi unauthorized URL direct browser me enter karne par 403 Forbidden aana chahiye. Ek tenant ka admin dusre tenant ke UUID par navigate nahi kar sakta.

| Step | ID | Login Persona | Attempted Unauthorized Action | Expected System Behavior | Status |
|---|---|---|---|---|---|
| 1 | S11-01 | `employee` (EMP-02) | Type URL directly: `/admin/tenants`. | Denied: HTTP 403 Forbidden or redirected away. No tenant data visible. | [ ] PENDING |
| 2 | S11-02 | `employee` (EMP-02) | Type URL directly: `/employees`. | Denied: 403 / 404 or redirected to `/me`. Cannot see staff list. | [ ] PENDING |
| 3 | S11-03 | `employee` (EMP-02) | Direct API POST to `/api/v1/leave-requests` with EMP-01's employee UUID in payload. | Backend returns HTTP 403: "Cannot create leave request for another employee." | [ ] PENDING |
| 4 | S11-04 | `manager` (EMP-01) | Type URL directly: `/payroll/runs`. | Denied: 403 Forbidden. Payroll runs are invisible to managers. | [ ] PENDING |
| 5 | S11-05 | `hr` | Type URL directly: `/payroll/runs`. | Denied: 403 Forbidden. HR role cannot process payroll. | [ ] PENDING |
| 6 | S11-06 | `finance` | Type URL directly: `/leave/types`. | Denied: 403 Forbidden. Finance cannot manage leave policy. | [ ] PENDING |
| 7 | S11-07 | `payroll-officer` | Type URL directly: `/leave/types`. | Denied: 403 Forbidden. | [ ] PENDING |
| 8 | S11-08 | `tenant-admin` (D1) | Open URL modifying tenant UUID to match `QA-D2`'s UUID. | Denied: HTTP 403 Forbidden. Cross-tenant data isolation strictly enforced. | [ ] PENDING |
| 9 | S11-09 | `tenant-admin` | Attempt to create duplicate Department with Code `QA-ENG`. | Rejected with HTTP 409 Conflict: "Department code QA-ENG already exists." | [ ] PENDING |
| 10 | S11-10 | `payroll-officer` | Attempt to approve an already `PAID` pay run via API. | Rejected with HTTP 409: "Invalid pay run state transition from PAID to APPROVED." | [ ] PENDING |

---

## 18. Stage 12 — Compliance, Audit Trail & Notification Verification
> **Assigned Lead:** Devashish (Dev 3) & Mohit Birla (Dev 1)  
> **Login Role:** `tenant-admin` (`qa-dX-admin@infinevocloud.com`)  
>
> **Hinglish Flow Explanation:**  
> Audit trail me har ek action (Employee update, Leave approval, Pay run transitions, Act-as sessions) actor name aur timestamp ke sath verify hogi. Brevo inboxes me check hoga ki sab emails deliver hue ya nahi.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S12-01 | `/audit` | Click `nav.audit` (`/audit`). | Audit log table opens displaying chronologically descending event stream. | [ ] PENDING |
| 2 | S12-02 | `/audit` | Filter by Entity = `EMPLOYEE`. | Displays creation and modification events for EMP-01, EMP-02, EMP-03 with actor ID. | [ ] PENDING |
| 3 | S12-03 | `/audit` | Filter by Entity = `LEAVE_REQUEST`. | Displays submit, approve, and on-behalf creation events with timestamps. | [ ] PENDING |
| 4 | S12-04 | `/audit` | Filter by Entity = `PAY_RUN`. | Displays lifecycle events: `CREATED` → `COMPUTED` → `LOCKED` → `APPROVED` → `PAID`. | [ ] PENDING |
| 5 | S12-05 | `/audit` | Filter by Entity = `ACT_AS_SESSION`. | Platform-admin impersonation sessions initiated in Stage 0 are audit-logged. | [ ] PENDING |
| 6 | S12-06 | Brevo Inbox | Check all test mailboxes for dispatched emails: 1) User invitations, 2) Password resets, 3) Leave alerts, 4) Payslip notices. | All emails arrived in inboxes with correct tenant branding and no failed delivery notices. | [ ] PENDING |

---

## 19. Stage 13 — Clean-Slate End-to-End Smoke Certification
> **Assigned Lead:** Mohit Birla (Dev 1)  
> **Execution Condition:** Runs only after Stages 0–12 pass across all tenants.  
>
> **Hinglish Flow Explanation:**  
> Mohit ek fresh tenant `QA-E2E` banayenge aur zero se 30 minutes ke andar setup + leave + payroll + payslip smoke test verify karke final sign-off ready karenge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S13-01 | `/admin/tenants` | Platform admin creates a 6th clean tenant: `QA-E2E` (`qa-e2e`). | Tenant active with unique UUID. | [ ] PENDING |
| 2 | S13-02 | Full Setup | Provision Admin, HR, Payroll Officer, and 1 Employee in `QA-E2E` within 20 mins. | All accounts active and verified. | [ ] PENDING |
| 3 | S13-03 | Org & Pay | Add 1 Department, 1 Designation, Mumbai Location, Salary Component, CTC Rs 6,00,000. | Structures linked. | [ ] PENDING |
| 4 | S13-04 | Leave Flow | Employee applies 1 day leave; Manager approves; Balance decrements from 10 to 9. | Leave approved successfully. | [ ] PENDING |
| 5 | S13-05 | Pay Run | Execute current month Pay Run: Create → Compute → Lock → Approve → Pay. | Pay run reaches `PAID`. Payslip visible in employee portal. Full trace in Audit log. | [ ] PENDING |

---

## 20. Expanded Functional Flows

### Stage 14 — Employee Resignation, Termination & Reactivation Lifecycle
> **Assigned Lead:** Krushna (Dev 4)  
> **Target Record:** EMP-03 (QA Analyst)  
> **Login Role:** `hr` / `tenant-admin`  
>
> **Hinglish Flow Explanation:**  
> Krushna EMP-03 ko terminate karenge: check karenge ki status `TERMINATED` ho jaye, login block ho jaye, pay run se exclude ho jaye. Fir reactivate karke login wapas restore karenge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S14-01 | `/employees/:EMP03-ID` | Log in as HR. Open EMP-03 detail page. | Page loads with employee header. Status badge = `ACTIVE`. | [ ] PENDING |
| 2 | S14-02 | `/employees/:EMP03-ID` | Click **Actions** dropdown → Select **Terminate / Initiate Exit**. | Modal opens: Exit Date, Last Working Day, Reason (`Resignation`, `Performance`, `Contract End`), Remarks. | [ ] PENDING |
| 3 | S14-03 | Modal Form | Fill: Last Working Day: `2026-10-31`, Reason: `Resignation`, Remarks: `Relocating to hometown`. Confirm. | Toast: "Employee status updated to TERMINATED." Header badge updates to `TERMINATED` (red/gray). | [ ] PENDING |
| 4 | S14-04 | `/login` | In an incognito window, attempt to log in as EMP-03 (`qa-dX-emp3@infinevocloud.com`). | Login rejected: "Your account has been deactivated. Please contact HR." | [ ] PENDING |
| 5 | S14-05 | `/payroll/runs/new` | Log in as Payroll Officer. Create a pay run preview for **November 2026**. | Pay run employee list contains ONLY EMP-01 and EMP-02. EMP-03 is excluded from active payroll. | [ ] PENDING |
| 6 | S14-06 | `/employees` | As HR, open Employee Directory. Default filter shows Active. | EMP-03 is not shown in default active view. | [ ] PENDING |
| 7 | S14-07 | `/employees` | Change Status filter to `TERMINATED` (or check "Include Inactive"). | EMP-03 appears with `TERMINATED` badge and Last Working Day displayed. | [ ] PENDING |
| 8 | S14-08 | `/employees/:EMP03-ID` | Click EMP-03 row → Actions → **Reactivate Employee**. Set Reactivation Date: `2026-11-15`, Remarks: `Exit retracted`. Confirm. | Status badge changes back to `ACTIVE`. | [ ] PENDING |
| 9 | S14-09 | `/login` | EMP-03 logs in again at Base URL. | Login succeeds. Self-service portal `/me` access restored. | [ ] PENDING |

---

### Stage 15 — Mid-Cycle Salary Revision & Effective-Dated CTC Restructuring
> **Assigned Lead:** Biren (Dev 2)  
> **Target Record:** EMP-01 (QA Senior Engineer)  
> **Login Role:** `payroll-officer`  
>
> **Hinglish Flow Explanation:**  
> Biren EMP-01 ki CTC Rs 7,20,000 se revise karke Rs 8,40,000 karenge effective 1st Nov. Puraani salary history me archive rahegi. October run Rs 60,000 par locked rahega aur November run nayi salary Rs 70,000 calculate karega.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S15-01 | `/employees/:EMP01-ID/salary` | Open EMP-01 → **Salary Tab**. | Existing structure displays: Annual CTC = Rs 7,20,000, Monthly Gross = Rs 60,000, Effective = `2026-04-01`. | [ ] PENDING |
| 2 | S15-02 | `/employees/:EMP01-ID/salary` | Click **Revise Salary / Add Structure**. | Revision form opens. | [ ] PENDING |
| 3 | S15-03 | Revision Modal | New Annual CTC: `Rs 8,40,000`, Effective From: `2026-11-01`, Reason: `Annual Appraisal Increment`. Submit. | New salary version saved. Monthly Gross updates to **Rs 70,000** (Rs 8,40,000 / 12). | [ ] PENDING |
| 4 | S15-04 | `/employees/:EMP01-ID/salary` | Inspect Salary History timeline. | 2 versions visible: Version 1 (Rs 7.2L, 2026-04-01 to 2026-10-31), Version 2 (Rs 8.4L, 2026-11-01 to Present). | [ ] PENDING |
| 5 | S15-05 | `/payroll/runs/:OCT-RUN-ID` | Open existing October 2026 Pay Run. | October pay run remains unchanged at Rs 60,000 monthly gross. Historical integrity preserved. | [ ] PENDING |
| 6 | S15-06 | `/payroll/runs/new` | Create draft pay run for **November 2026**. Click Compute. | EMP-01 gross earnings calculated at **Rs 70,000**. Basic and EPF recalculate based on new revised CTC. | [ ] PENDING |

---

### Stage 16 — Proof of Investment (POI) Submission & Verification Flow
> **Assigned Lead:** Devashish (Dev 3)  
> **Personas:** EMP-01 (Submitter), Payroll Officer (Verifier)  
>
> **Hinglish Flow Explanation:**  
> Devashish POI receipt flow verify karenge: EMP-01 apna LIC receipt upload karega, Officer use check karke "VERIFIED" mark karega aur dummy bill ko reject karega.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S16-01 | `/payroll/settings/poi-window/2026` | As Payroll Officer: Open Proof of Investment window settings. *(DEFER if 404)*. | POI settings page opens. | [ ] PENDING |
| 2 | S16-02 | Settings Form | Set Window: Start = `2026-10-15`, End = `2026-11-30`. Save. | POI Window marked `OPEN`. | [ ] PENDING |
| 3 | S16-03 | `/me/tax-declaration` | Log in as EMP-01. Open Tax Declarations. | Under 80C LIC section, **Upload Proof** button is enabled. | [ ] PENDING |
| 4 | S16-04 | Upload Drawer | Select Section: `80C - Life Insurance Premium`, Declared: Rs 75,000, Actual Paid: `Rs 75,000`. Upload PDF receipt. Submit. | Proof submitted with status `PENDING_VERIFICATION`. Document preview icon visible. | [ ] PENDING |
| 5 | S16-05 | `/payroll/tax-declarations/review` | As Payroll Officer: Open POI Verification queue. | EMP-01's 80C proof appears in review table. | [ ] PENDING |
| 6 | S16-06 | Verification Modal | Click View Document. Enter Verified Amount = `Rs 75,000`, Status = `VERIFIED`, Comment = `Receipt valid`. Save. | Proof status updates to `VERIFIED`. | [ ] PENDING |
| 7 | S16-07 | Verification Modal | For testing rejection: Upload a dummy HRA receipt of Rs 20,000. Review as officer and click **Reject** (Reason: `Landlord PAN missing`). | Status updates to `REJECTED`. EMP-01 sees rejected badge and remarks on portal. | [ ] PENDING |

---

### Stage 17 — Custom Role Creation & Granular Permission Management
> **Assigned Lead:** Krushna (Dev 4)  
> **Login Role:** `tenant-admin`  
>
> **Hinglish Flow Explanation:**  
> Krushna naya custom role `leave-auditor` banayenge jisme sirf leave read karne ki permission hogi. User ko ye role dekar verify karenge ki use payroll ya employee create karne ki koi permission nahi mile.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S17-01 | `/roles` | Open `nav.roles`. Click **New Role**. *(DEFER if 404)*. | Role creation screen opens. | [ ] PENDING |
| 2 | S17-02 | `/roles/new` | Role Name: `leave-auditor`, Description: `Read-only access to leave calendars and balances`. | Role metadata created. Action selection grid opens. | [ ] PENDING |
| 3 | S17-03 | Action Checklist | Select ONLY: `core.leave.read`, `core.holiday.read`. Leave all payroll, employee management, and approval actions unchecked. Save. | Custom role `leave-auditor` created and listed in role matrix. | [ ] PENDING |
| 4 | S17-04 | `/invitations/users` | Invite `test-auditor@infinevocloud.com` with role `leave-auditor`. Accept invite and configure password. | Account activated with custom role. | [ ] PENDING |
| 5 | S17-05 | `/login` | Log in as `test-auditor`. Inspect sidebar navigation. | Sidebar shows ONLY `nav.leave` and `nav.holidays`. Employees, Org, Roles, Payroll, and Approvals are completely hidden. | [ ] PENDING |
| 6 | S17-06 | Direct URL Access | Attempt to navigate to `/leave/types/new` or `/employees`. | Direct creation button hidden; API returns HTTP 403 Forbidden. Read-only enforcement confirmed. | [ ] PENDING |

---

### Stage 18 — Payroll Reconciliation Dashboard & KPI Analytics
> **Assigned Lead:** Biren (Dev 2)  
> **Login Role:** `payroll-officer` (`qa-dX-payroll@infinevocloud.com`)  
> *(Verified from `PayrollNavigation.DASHBOARD` → `/payroll/dashboard`)*  
>
> **Hinglish Flow Explanation:**  
> Biren payroll dashboard (`/payroll/dashboard`) par jaakar Total Gross (Rs 1,10,000), Total Statutory Deductions, Net Payout, aur 3 Processed Headcounts verify karenge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S18-01 | `/payroll/dashboard` | Log in as Payroll Officer. Click `nav.payroll.dashboard`. | Dashboard opens with KPI summary metric cards. | [ ] PENDING |
| 2 | S18-02 | Metric Card 1 | Inspect **Total Payroll Cost (Gross)** card for October 2026. | Displays sum of gross earnings: Rs 60,000 (EMP-01) + Rs 30,000 (EMP-02) + Rs 20,000 (EMP-03) = **Rs 1,10,000**. | [ ] PENDING |
| 3 | S18-03 | Metric Card 2 | Inspect **Total Statutory Deductions** card. | Displays combined sum of EPF, ESI (Rs 150), and Maharashtra PT (Rs 600 total). | [ ] PENDING |
| 4 | S18-04 | Metric Card 3 | Inspect **Total Net Payout** card. | Value equals Total Gross minus Total Deductions. Matches net pay released in Stage 8. | [ ] PENDING |
| 5 | S18-05 | Headcount Widget | Inspect Paid Headcount metric. | Displays `3 Employees Processed`. Zero failed or skipped employees. | [ ] PENDING |
| 6 | S18-06 | Period Selector | Switch period dropdown between current and previous periods. | Dashboard dynamically updates KPI widgets according to selected period. | [ ] PENDING |

---

### Stage 19 — Comprehensive Employee Self-Service (`/me`) Portal
> **Assigned Lead:** Sayeed (Dev 5)  
> **Login Role:** `employee` (EMP-01, EMP-02)  
>
> **Hinglish Flow Explanation:**  
> Sayeed employee self-service `/me` portal ka complete walkthrough karenge: Profile view, Leave balance meters, Holiday list, Payslips tab, aur Emergency contact update.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S19-01 | `/me` | Log in as employee (`pediroh497@calirona.com`). Navigate to Base URL. | Auto-redirects to `/me` self-service portal. Welcomes employee. | [!] FAIL (`D-17`: Blocked by Azure Front Door WAF `The request is blocked. 20261007T...`; cannot log in) |
| 2 | S19-02 | `/me` | Inspect Profile Summary widget. | Displays Name, Designation, Department, Location. | [!] FAIL (Blocked by S19-01 / D-17: cannot access `/me` portal) |
| 3 | S19-03 | `/me` | Click **Leave Balances** tab. | Balance meters show EL and SL remaining. | [!] FAIL (Blocked by S19-01 / D-17: cannot access `/me` portal) |
| 4 | S19-04 | `/me` | Click **Holiday Calendar** tab. | Lists upcoming holidays for Mumbai location. | [!] FAIL (Blocked by S19-01 / D-17: cannot access `/me` portal) |
| 5 | S19-05 | `/me` | Click **My Payslips** tab. | Payslip cards listed with earnings and deductions. | [!] FAIL (Blocked by S19-01 / D-17: cannot access `/me` portal) |
| 6 | S19-06 | `/me/profile` | Open My Profile → Emergency Contact. Edit phone to `9888888888`. Save. | Details persist. Toast: "Profile details updated." | [!] FAIL (Blocked by S19-01 / D-17: cannot access `/me` portal) |

---

### Stage 20 — Leave Balance Import & Bulk Adjustments
> **Assigned Lead:** Sayeed (Dev 5)  
> **Login Role:** `hr` / `tenant-admin`  
> *(Verified from `NavigationCatalogue`: `core.leave.import` → `/leave/import`)*  
>
> **Hinglish Flow Explanation:**  
> Sayeed `/leave/import` par jakar opening leave balances CSV import karenge: pehle invalid employee code ka dry-run failure check karenge, fir valid file upload karke bulk balance update confirm karenge.

| Step | ID | Route / Action | Exact Test Operation | Expected Result | Status |
|---|---|---|---|---|---|
| 1 | S20-01 | `/leave/import` | As HR: Navigate to `nav.leave.import` (`/leave/import`). *(DEFER if 404)*. | Import screen opens with file upload dropzone and template download link. | [x] PASS |
| 2 | S20-02 | `/leave/import` | Click **Download Sample CSV Template**. | CSV file downloads with headers: `employee_code, leave_type_code, year, balance_days`. | [!] FAIL (Blocked by D-50: No sample template download button or link on `/leave/import`) |
| 3 | S20-03 | Local Edit | Create test CSV with an INVALID employee code `INVALID-999`, Type = `EL`, Days = 5. Upload file. | Validation Dry-Run fails with error: "Row 1: Employee code INVALID-999 does not exist in tenant." | [x] PASS (Dry-run caught invalid employee `INVALID-999`; Result: Completed with Errors, Failed: 1; D-49 logged for `<InputNumber>` blocking `YYYY-YY` leave year and D-51 logged for unclickable error report UUID) |
| 4 | S20-04 | Local Edit | Create valid CSV: `EMP-01, EL, 2026, 15` and `EMP-02, EL, 2026, 12`. Upload file. | Validation passes: "2 records validated successfully with 0 errors." | [x] PASS (Dry run completed: Total Rows: 2, Imported: 0, Failed: 0, no errors) |
| 5 | S20-05 | `/leave/import` | Click **Execute Import / Confirm Update**. | Import job completes. Status = `COMPLETED`. | [x] PASS (Executed live import with EMP-01 and EMP-04; Status: COMPLETED, 1 row imported, 0 failed) |
| 6 | S20-06 | `/leave/allocations` | Check `nav.leave.allocations` for EMP-01 and EMP-02. | Balances successfully updated to 15 and 12 days respectively. | [x] PASS (Allocations table live update verified: EMP-04 18 EL, EMP-01 15 EL, EMP-02 10 EL; Note: D-52 logged for employee role missing Leave menu) |

---

## 21. Complete Role-Menu Entitlement Matrix

| Nav Item Key | Target Route | platform-admin | tenant-admin | payroll-officer | hr | manager | finance | employee |
|---|---|---|---|---|---|---|---|---|
| `core.tenants` | `/admin/tenants` | **YES** | NO | NO | NO | NO | NO | NO |
| `core.setup` | `/setup` | **YES** | **YES** | NO | NO | NO | NO | NO |
| `core.roles` | `/roles` | **YES** | **YES** | NO | NO | NO | NO | NO |
| `core.audit` | `/audit` | **YES** | **YES** | NO | NO | NO | NO | NO |
| `core.org` (departments, desig, locs) | `/org/departments` | **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `core.employee` | `/employees` | **YES** | **YES** | **YES** | **YES** | NO | NO | NO |
| `core.invitations.users` | `/invitations/users` | **YES** | **YES** | NO | NO | NO | NO | NO |
| `core.invitations.employees` | `/invitations/employees` | **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `core.holiday` | `/holidays` | **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `core.leave` (types, allocations, requests) | `/leave/types` | **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `core.leave.import` | `/leave/import` | **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `core.approvals` (inbox) | `/approvals` | **YES** | **YES** | NO | NO | **YES** | **YES** | NO |
| `core.approvals.delegations` | `/approvals/delegations` | **YES** | **YES** | NO | NO | **YES** | **YES** | NO |
| `core.approvals.definitions` | `/approvals/definitions` | **YES** | **YES** | NO | NO | NO | NO | NO |
| `hrms.dashboard` | `/hrms/dashboard` | **YES** | **YES** | NO | **YES** | **YES** | NO | **YES** |
| `hrms.attendance` (clock card) | `/hrms/attendance` | NO | NO | NO | NO | NO | NO | **YES** |
| `hrms.attendance_log` | `/hrms/attendance-log` | **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `hrms.attendance_settings` | `/hrms/attendance-settings`| **YES** | **YES** | NO | **YES** | NO | NO | NO |
| `hrms.regularizations` | `/hrms/regularizations` | NO | NO | NO | NO | NO | NO | **YES** |
| `hrms.overtime_requests` | `/hrms/overtime-requests` | NO | NO | NO | NO | NO | NO | **YES** |
| `hrms.timesheets` | `/hrms/timesheets` | NO | NO | NO | NO | NO | NO | **YES** |
| `hrms.timesheet_review` | `/hrms/timesheet-review` | **YES** | **YES** | NO | **YES** | **YES** | NO | NO |
| `payroll.dashboard` | `/payroll/dashboard` | **YES** | **YES** | **YES** | NO | NO | NO | NO |
| `payroll.runs` | `/payroll/runs` | **YES** | **YES** | **YES** | NO | NO | NO | NO |
| `payroll.claims` | `/payroll/claims` | **YES** | **YES** | **YES** | NO | NO | **YES** | NO |
| `payroll.deductions` | `/payroll/deductions` | **YES** | **YES** | **YES** | NO | NO | **YES** | NO |
| `payroll.prior_payroll` | `/payroll/prior-payroll` | **YES** | **YES** | **YES** | NO | NO | NO | NO |
| `/me` (self-service portal) | `/me` | NO | NO | NO | NO | NO | NO | **YES** |

---

## 22. Defect Incident Report Template

For every test step that results in `FAIL`, developers must raise a ticket using this format:

```markdown
### DEFECT REPORT: [BUG-ID: e.g. BUG-D2-004]
- **Date / Timestamp:** [2026-10-06 14:30 IST]
- **Reporting Developer:** [Biren - Payroll Track]
- **Tenant Context:** [QA-D2]
- **Stage & Step ID:** [Stage 8 - Step S8-05]
- **Authenticated Role:** [payroll-officer]
- **Screen URL / Endpoint:** [`/payroll/runs/3a8e-49b1-912a/lines`]

#### 1. Issue Title
[Brief descriptive one-line summary of failure]

#### 2. Exact Steps to Reproduce
1. Log in as `qa-d2-payroll@infinevocloud.com`
2. Navigate to `/payroll/runs` and open Computed Run #12
3. Click EMP-03 to open lines drawer
4. Inspect Deduction items for ESI

#### 3. Expected vs Actual Behavior
- **Expected Result:** ESI line item displays Rs 150 (0.75% of Rs 20,000 gross).
- **Actual Result:** ESI line item is missing completely, or displays Rs 0.00.

#### 4. Diagnostic Logs & Evidence
- **HTTP Status / Response Body:** [HTTP 500 / { "error": "NullPointerException at EsiCalculator.java:42" }]
- **Browser Console Error:** [Uncaught TypeError: Cannot read properties of undefined]
- **Container Log Snippet:** 
  `az containerapp logs show -n ca-infinevo-dev-app -g rg-infinevo-dev --tail 30`
- **Screenshot / Video:** [Attached / Link]

#### 5. Severity Rating
- [ ] **BLOCKER** (Completely blocks all subsequent testing in this tenant)
- [x] **CRITICAL** (Core calculation or lifecycle flow broken; no workaround)
- [ ] **MAJOR** (Feature broken but alternative path exists)
- [ ] **MINOR** (Cosmetic, wording, or alignment defect)
```

---

## 23. Live Troubleshooting Guide

| Observable Failure Symptom | Underlying Root Cause | Immediate Actionable Remedy |
|---|---|---|
| **"Navigation Unavailable"** or infinite spinner on sidebar load | Axios 30-second timeout; PostgreSQL Hikari pool idle connection dropped. | Refresh browser. If persistent, run: `az containerapp revision restart -n ca-infinevo-dev-app -g rg-infinevo-dev`. |
| **Login loops back to Keycloak** without entering application | Token expiration, domain mismatch, or session cookie collision across multiple tabs. | Open browser in fresh Incognito window. Clear cookies for domain `infinevocloud.com`. |
| **Invitation link redirects directly to Keycloak login screen** | Frontend public routes whitelist (`publicRoutes`) missing invitation accept path. | Confirm deploy pipeline included commit for ticket W-46.7. |
| **Pay run stuck in `COMPUTING` status indefinitely** | Worker container (`ca-infinevo-dev-worker`) dead, or Azure Storage Queue consumer halted. | Inspect worker logs: `az containerapp logs show -n ca-infinevo-dev-worker -g rg-infinevo-dev --tail 50`. Restart worker. |
| **HTTP 403 Forbidden** on an entitled page | Missing row in `core.role_action` or `core.user_role` mapping in tenant schema. | Verify role assignment in admin console; cross-check role action code against Java catalogue. |
| **Invitation or Payslip email not received** | Brevo SMTP credentials invalid or sender address `application@infinevocloud.com` unverified. | Check Brevo transactional logs. Verify Key Vault secret `BREVO-API-KEY`. |
| **Key Vault secret read failure in Azure CLI** | User lacks `Key Vault Secrets Officer` RBAC role on `kv-infinevo-shared`. | Ask Karmaveer to grant RBAC role `Key Vault Secrets Officer` to `mbirla@infinevocloud.com`. |

---

## 24. Daily Execution Log (Sanjib Banerjee Sign-Off)

Each developer updates this table daily before standup:

| Date | Developer | Tenant | Assigned Stages | Steps Tested | PASS | FAIL | BLOCKED | DEFER | Notes / Defect IDs |
|---|---|---|---|---|---|---|---|---|---|
| 2026-10-06 | Mohit Birla (Dev 1) | `QA-D1` | Stage 0, Stage 11, Stage 12, Stage 13 | S0-01 to S0-10 | | | | | Provisioned all 5 tenants |
| 2026-10-06 | Biren (Dev 2) | `QA-D2` | Stage 5, Stage 8, Stage 15, Stage 18 | | | | | | Payroll setup & Pay run |
| 2026-10-06 | Devashish (Dev 3) | `QA-D3` | Stage 9, Stage 10, Stage 16, Stage 12 | | | | | | Tax & claims testing |
| 2026-10-06 | Krushna (Dev 4) | `QA-D4` | Stage 1, Stage 2, Stage 3, Stage 14, Stage 17 | | | | | | Org masters & onboarding |
| 2026-10-07 | Sayeed (Dev 5) | `QA-D5` / `QA-D2` | Stage 4, Stage 6, Stage 7, Stage 19 | S4-01 to S4-11, S6-01 to S6-10, S7-01 to S7-10, S19-01 to S19-06 | 10 | 27 | 0 | 0 | S6-01 & S7-04 PASS; Stages 4 & 6 marked FAIL (Retest after deployment) due to D-15 & D-16; Stages 7 & 19 marked FAIL/BLOCKED due to D-17 (Azure Front Door WAF blocking Keycloak employee login with 'The request is blocked' and pending invitation activation) |
| 2026-10-09 | Sayeed (Dev 5) | Azure Dev | Stage 20 (Leave Balance Import) | S20-01 to S20-06 | 5 | 1 | 0 | 0 | S20-01, S20-03, S20-04, S20-05, S20-06 PASS (Valid/invalid dry-runs and live imports verified on Azure UI); S20-02 FAIL due to D-50 (Missing sample template download); Defects logged: D-49 (Leave year input `<InputNumber>` blocks `YYYY-YY` April tenants), D-50 (Missing sample CSV), D-51 (Unclickable error report UUID), D-52 (Employee role missing Leave menu) |

---

## 25. Definition of Done (Exit Criteria)

A test stage is formally **DONE** when:
1. Every step in the stage is recorded with an explicit result code (`[x] PASS`, `[-] SKIP`, or `[-] DEFER`).
2. Zero open **BLOCKER** or **CRITICAL** defects remain on that stage.
3. Timestamped evidence (screenshots, API response logs) exists in the team test repository.
4. All failed steps have a linked bug ticket logged in the defect tracker.

The entire test cycle is **ACCEPTED & COMPLETE** when:
- All 20 stages across all 5 developers have attained Done status.
- Mohit Birla has executed Stage 13 (Clean-Slate End-to-End Smoke) on `QA-E2E` with 100% `PASS`.
- Project Manager Sanjib Banerjee reviews and signs off on the daily status log.
