# W-15 Approval Engine — Evidence for Five Flows

## Summary: Differences Across Flows

| Flow | Entity | Status Field(s) | # Stages | Who Approves | Configurable? | Shared Code |
|---|---|---|---|---|---|---|
| **Leave** (HRMS) | LeaveRequests | reportingManagerStatus, hrStatus | 2 | Reporting Manager → HR | No | LeaveRequestServiceImpl |
| **Reimbursement** (Payroll) | EmployeeReimbursementRequest | status (single) | 1 | Admin only | No | AdminReimbursementController |
| **Proof of Investment** (Payroll) | ProofOfInvestmentDocument | status (single) | 1 | HR/Admin only | No | POIController |
| **Overtime** (HRMS) | OvertimeRequest | managerStatus, hrStatus | 2 | Manager, then HR or skip HR | No | OvertimeRequestController |
| **Attendance Regularization** | —— | —— | —— | NOT FOUND | —— | —— |

---

## 1. Leave Approval (HRMS)

**Flow:** Employee applies → Reporting Manager approval → HR approval.  
**Stages:** 2 (sequential; HR can only act after manager decides)

**Entity:** `LeaveRequests` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/LeaveRequests.java`

**Status fields:**
- `reportingManagerStatus` (Enum LeaveRequestStatus, not null, default PENDING) — line 56–57
- `hrStatus` (Enum LeaveRequestStatus, not null, default PENDING) — line 60–61
- `reportingManagerUpdatedAt` (LocalDateTime, nullable) — line 82–83
- `hrUpdatedAt` (LocalDateTime, nullable) — line 85–86
- `reportingManagerComment` (String, max 1000) — line 88–89
- `hrComment` (String, max 1000) — line 91–92

**Status enum:** `LeaveRequestStatus` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/enumuration/LeaveRequestStatus.java:3–7`
- PENDING
- APPROVED
- REJECTED

**Approval logic:**
- Stage 1 (Reporting Manager): Line 195–198 of LeaveRequestController checks `reportingManagerStatus == PENDING` + user has "reporting manager" role
- Stage 2 (HR): Line 200–204 checks `reportingManagerStatus != PENDING` (manager must have acted first) AND `hrStatus == PENDING` + user has "hr" role

**Controller:** `LeaveRequestController.updateLeaveRequestStatus()` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/LeaveRequestController.java:155–222`

- Line 156–160: Endpoint `PUT /leaves/{id}/status` with status and comment
- Line 163–175: Extract authenticated user and roles
- Line 185–189: Normalize role names to lowercase
- Line 195–204: Determine `actingRole` ("reporting manager" or "hr") based on stage and role
- Line 206–210: Return FORBIDDEN (403) if user not authorized
- Line 217: Call `leaveRequestsService.updateLeaveRequestStatus(id, newStatus, actingRole, comment)`

**Service:** `LeaveRequestServiceImpl.updateLeaveRequestStatus()` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/serviceimpl/leaverequest/LeaveRequestServiceImpl.java`

- Line 692–694: Check if both approved
- Line 695–706: If both approved and not yet generated, generate LOP allocation
- Line 708–711: Check if both rejected
- Line 711–718: If both rejected and not yet recredited, recredit leave balance

**Configurable?** No. Hard-coded to two stages with fixed roles.

---

## 2. Reimbursement Approval (Payroll)

**Flow:** Employee submits request → Admin approves or rejects (single stage).

**Entity:** `EmployeeReimbursementRequest` — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employeereimbursement/EmployeeReimbursementRequest.java`

**Status fields:**
- `status` (Enum ReimbursementStatus, not null) — line 63–64. **Single field; no separate manager/HR stages.**
- `approvedAmount` (BigDecimal, nullable, precision 12/2) — line 44–45
- `remarks` (String, max 500) — line 66–67
- `approvedBy` (String, max 200) — line 89–90
- `approvedAt` (LocalDateTime, nullable) — line 92–93
- `paymentStatus` (Enum ReimbursementPaymentStatus, not null, default) — line 70–71

**Status enum:** `ReimbursementStatus` — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/enumeration/employeereimbursement/ReimbursementStatus.java:3–7`
- PENDING
- APPROVED
- REJECTED

**Approval logic:**

**Controller:** `AdminReimbursementController` — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/employeereimbursement/AdminReimbursementController.java`

- Line 121–147: Endpoint `PUT /admin/reimbursements/{id}/approve` with `ApproveReimbursementRequestDTO` (approvedAmount, remarks)
- Line 128: Extract adminId from JWT token subject via `JWTUtil.getUserIdAndEmailFromToken().get("userId")`
- Line 133–134: Call `reimbursementService.approveReimbursement(id, organizationId, adminId, dto)`
- Line 161–187: Endpoint `PUT /admin/reimbursements/{id}/reject` with rejection remarks
- Line 168: Extract adminId from JWT
- Line 173–174: Call `reimbursementService.rejectReimbursement(..., adminId, dto)`

**Who approves?** Admin role (enforced by OrganizationRoleInterceptor; not explicitly shown in controller but cited in comment line 27).

**Configurable?** No. Single approval step, admin-only.

**Difference from Leave:** Approval is immediate (not sequential); only one person (admin) acts. No manager pre-approval or HR escalation.

---

## 3. Proof of Investment (Payroll)

**Flow:** Employee submits documents → HR reviews and approves/rejects.

**Entity:** `ProofOfInvestmentDocument` — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/taxCalculator/ProofOfInvestmentDocument.java`

**Status fields:**
- `status` (Enum DocumentStatus, not null, default PENDING) — line 40–41. **Single field.**
- `remarks` (String, max 1000) — line 43–44
- `submittedDate` (LocalDateTime, not null) — line 46–47
- `reviewedDate` (LocalDateTime, nullable) — line 49–50
- `reviewedBy` (String, max 200) — line 52–53
- `financialYear` (Integer, not null) — line 63–64

**Status enum:** `DocumentStatus` (inner enum) — `ProofOfInvestmentDocument.java:74–76`
- PENDING
- APPROVED
- REJECTED
- DRAFT

**Approval logic:**

**Controller:** `POIController` — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/taxCalculator/POIController.java`

- Line 192–217: Endpoint `PUT /api/proof-of-investment/admin/documents/{documentId}/status`
- Line 196: Request param `status` (DocumentStatus enum)
- Line 197: Optional `remarks`
- Line 199: Extract `reviewedBy = getCurrentUserId()` from JWT
- Line 204–205: Call `poiService.updateDocumentStatus(documentId, status, remarks, reviewedBy, organizationId)`

**Who approves?** HR/Admin (endpoint is under `/admin/` path; no explicit role check shown but path implies admin access).

**Configurable?** No. Single approval step, HR-only.

**Difference from Reimbursement:** POI has a "reviewed" timestamp and reviewer name recorded separately. Status includes DRAFT state. Otherwise same single-stage pattern.

---

## 4. Overtime (HRMS)

**Flow:** Employee submits → Manager approval → HR approval (optional escalation similar to Leave).

**Entity:** `OvertimeRequest` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/OvertimeRequest.java`

**Status fields:**
- `managerStatus` (Enum OvertimeStatus, not null, default PENDING) — line 44–45
- `hrStatus` (Enum OvertimeStatus, not null, default PENDING) — line 48–49. **Dual-field like Leave, but checked differently.**
- `managerUpdatedAt` (LocalDateTime, nullable) — line 51–52
- `hrUpdatedAt` (LocalDateTime, nullable) — line 54–55
- `managerComment` (String, max 1000) — line 64–65
- `hrComment` (String, max 1000) — line 67–68
- `managerEmployeeId` (String, not null) — line 70–71. **Manager is stored as employee ID.**
- `compOffCreated` (boolean, default false) — line 61–62

**Status enum:** `OvertimeStatus` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/enumuration/OvertimeStatus.java:3–6`
- PENDING
- APPROVED
- REJECTED

**Approval logic:**

**Controller:** `OvertimeRequestController.updateStatus()` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/OvertimeRequestController.java:94–140`

- Line 95–98: Endpoint `PUT /overtime/{id}/status` with status (String) and optional comment
- Line 100–106: Extract user and normalize roles to lowercase
- Line 112–118: Determine `actingRole`:
  - "manager" if user has manager role
  - "reporting manager" if user has reporting_manager role (fallback)
  - "hr" if user has hr role
  - Null if none match
- Line 120–123: Return FORBIDDEN if actingRole is null
- Line 130: Call `service.updateStatus(id, actingRole, newStatus, comment)`

**Difference from Leave:** Uses `manager` role as stage 1 approver, not "reporting manager" (line 112). But fallback to reporting manager exists (line 114–115). HR is stage 2 (line 116–117).

**Configurable?** No. Hard-coded role sequence.

**Stages:** Appears to be 2-stage like Leave (manager first, then HR), but the controller code does not enforce sequencing as strictly as LeaveRequestController.

---

## 5. Attendance Regularization

**Status: NOT FOUND in frozen system.**

- No entity named `AttendanceRegularization`, `Regularization`, or similar in HRMS.
- No controller handling attendance regularization approval flow.
- Searched: `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/` for "regularization" → no matches.
- Searched: `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/` for "regularization" → no matches.
- Leave consumption in Payroll (`EmployeeLeaveConsumptionController`) exists but does not include regularization approval.
- Attendance preference and mark-leave modules exist but no approval/review step for regularization found.

**Implication:** This flow must be designed as new in the target state or exists in a piece of code not yet migrated to the frozen snapshots.

---

## What Differs Across the Five Flows

1. **Number of stages:**
   - Leave: 2 (RM → HR)
   - Overtime: 2 (Manager → HR)
   - Reimbursement: 1 (Admin only)
   - POI: 1 (HR only)
   - Attendance Regularization: Not found

2. **Role names for stage 1:**
   - Leave: "reporting manager"
   - Overtime: "manager" (or fallback to "reporting manager")
   - Reimbursement: "admin"
   - POI: implicit admin
   - Regularization: N/A

3. **Single vs. dual status fields:**
   - Leave: Two fields (`reportingManagerStatus`, `hrStatus`)
   - Overtime: Two fields (`managerStatus`, `hrStatus`)
   - Reimbursement: One field (`status`)
   - POI: One field (`status`)
   - Regularization: N/A

4. **Sequencing enforcement:**
   - Leave: Strict (line 200–204 of LeaveRequestController: HR cannot act until manager decides)
   - Overtime: Not strict (controller allows any authorized role to update at any time)
   - Reimbursement: N/A (single stage)
   - POI: N/A (single stage)
   - Regularization: N/A

5. **Shared code:**
   - Leave uses `LeaveRequestServiceImpl` (no other flow imports it)
   - Overtime uses `OvertimeRequestService` (separate, similar pattern)
   - Reimbursement uses `EmployeeReimbursementService` (admin-specific)
   - POI uses `POIService` (separate)
   - **No shared approval engine code exists.** Each flow implements its own status transitions.

---

## Design Requirements for Unified Engine

The engine must support:
1. Single-stage approvals (Reimbursement, POI)
2. Dual-stage approvals with ordered sequencing (Leave, Overtime)
3. Different entity status field names (not always `status`)
4. Different role names for the same logical approver level (manager vs. reporting manager)
5. Optional reviewer name and timestamp capture (POI's `reviewedBy`, `reviewedDate`)
6. Comments/remarks per stage (Leave, Overtime, Reimbursement all have comment fields)
7. External state tracking (e.g., `compOffCreated` in Overtime, payment status in Reimbursement)
