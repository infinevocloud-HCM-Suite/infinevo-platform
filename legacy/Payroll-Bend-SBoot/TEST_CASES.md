# Payroll Project - Test Cases

---

## Module: Deduction & Reimbursement Integration with PayRun

---

### Section 1: Deduction CRUD Operations

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 1 | Create deduction with valid data | POST deduction with valid employeeId, amount, month (future), reason | 201 - Deduction created with status ACTIVE | [ ] | - | - |
| 2 | Create deduction with past month | POST deduction with month in the past | 400 - "Deduction month cannot be in the past" | [ ] | - | - |
| 3 | Create deduction with invalid employee | POST deduction with non-existent employeeId | 400/404 - Employee not found | [ ] | - | - |
| 4 | Update deduction when status is ACTIVE | PUT with new amount/reason on ACTIVE deduction | 200 - Updated successfully | [ ] | - | - |
| 5 | Update deduction when status is INPAYRUN | PUT on deduction with status INPAYRUN | 400 - "Cannot update deduction. It is already in payrun or processed." | [ ] | - | - |
| 6 | Update deduction when status is PROCESSED | PUT on deduction with status PROCESSED | 400 - "Cannot update deduction. It is already in payrun or processed." | [ ] | - | - |
| 7 | Delete deduction when status is ACTIVE | DELETE on ACTIVE deduction | 200 - Deleted successfully | [ ] | - | - |
| 8 | Delete deduction when status is INPAYRUN | DELETE on INPAYRUN deduction | 400 - "Cannot delete deduction. It is already in payrun or processed." | [ ] | - | - |
| 9 | Delete deduction when status is PROCESSED | DELETE on PROCESSED deduction | 400 - "Cannot delete deduction. It is already in payrun or processed." | [ ] | - | - |
| 10 | Delete deduction of another organization | DELETE with wrong organizationId | 404 - Not found | [ ] | - | - |
| 11 | Update deduction of another organization | PUT with wrong organizationId | 404 - Not found | [ ] | - | - |

---

### Section 2: Reimbursement CRUD Operations

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 12 | Create reimbursement request | POST with valid data (type, amount, billDate, description) | 201 - Created with status PENDING, paymentStatus UNPAID | [ ] | - | - |
| 13 | Approve reimbursement with month | Admin approves with approvedAmount and reimbursementMonth | Status changes to APPROVED, paymentStatus stays UNPAID, month set | [ ] | - | - |
| 14 | Reject reimbursement | Admin rejects with remarks | Status changes to REJECTED, paymentStatus stays UNPAID | [ ] | - | - |
| 15 | Verify rejected reimbursement NOT picked by payrun | Create payrun for the month | Rejected reimbursement not included in calculation | [ ] | - | - |

---

### Section 3: PayRun Creation - Deduction Integration

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 16 | PayRun picks up ACTIVE deductions for the month | Create deduction for Sep 2026, then create payrun for Sep 2026 | claimDeduction shows deduction amount, totalDeductions includes it, netPay reduced | [ ] | - | - |
| 17 | PayRun does NOT pick deductions for different month | Create deduction for Oct 2026, create payrun for Sep 2026 | claimDeduction = 0 for that employee | [ ] | - | - |
| 18 | PayRun does NOT pick INPAYRUN deductions | Deduction already INPAYRUN from previous payrun | Not picked up again, claimDeduction = 0 | [ ] | - | - |
| 19 | PayRun does NOT pick PROCESSED deductions | Deduction already PROCESSED | Not picked up, claimDeduction = 0 | [ ] | - | - |
| 20 | Multiple deductions for same employee same month | Create 2 deductions (500 + 700) for same month | claimDeduction = 1200, both statuses change to INPAYRUN | [ ] | - | - |
| 21 | Deduction status changes to INPAYRUN after payrun creation | Check employee_deduction table after POST /api/payruns | Status = INPAYRUN | [ ] | - | - |
| 22 | claimDeductionStatus shows INPAYRUN in employee list | GET /api/payrun-employees/list/{id} | claimDeductionStatus = "INPAYRUN" | [ ] | - | - |

---

### Section 4: PayRun Creation - Reimbursement Integration

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 23 | PayRun picks up APPROVED + UNPAID reimbursements for the month | Approve reimbursement for "2026-08", create payrun for Aug 2026 | claimReimbursement shows approvedAmount, added to netPay | [ ] | - | - |
| 24 | PayRun does NOT pick REJECTED reimbursements | Rejected reimbursement for same month | Not included in claimReimbursement | [ ] | - | - |
| 25 | PayRun does NOT pick PENDING reimbursements | Pending reimbursement for same month | Not included | [ ] | - | - |
| 26 | PayRun does NOT pick reimbursements for different month | Approved reimbursement for "2026-09", payrun for Aug 2026 | claimReimbursement = 0 | [ ] | - | - |
| 27 | PayRun does NOT pick already INPAYRUN reimbursements | Reimbursement already INPAYRUN | Not picked again | [ ] | - | - |
| 28 | PayRun does NOT pick PAID reimbursements | Reimbursement already PAID | Not picked | [ ] | - | - |
| 29 | Multiple reimbursements for same employee same month | 2 approved reimbursements (1000 + 500) | claimReimbursement = 1500, both marked INPAYRUN | [ ] | - | - |
| 30 | Reimbursement paymentStatus changes to INPAYRUN after payrun creation | Check DB after POST /api/payruns | paymentStatus = INPAYRUN, payrunId set | [ ] | - | - |
| 31 | claimReimbursementStatus shows INPAYRUN in employee list | GET /api/payrun-employees/list/{id} | claimReimbursementStatus = "INPAYRUN" | [ ] | - | - |

---

### Section 5: Net Pay Calculation Accuracy

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 32 | Net pay with only deduction claim | Employee has 1000 deduction, no reimbursement | netPay = earnings + benefits + reimbursements - totalDeductions (includes 1000 claim) | [ ] | - | - |
| 33 | Net pay with only reimbursement claim | Employee has 1000 approved reimbursement, no deduction | netPay = earnings + benefits + reimbursements + 1000 (claim reimb) - totalDeductions | [ ] | - | - |
| 34 | Net pay with both deduction and reimbursement | Employee has 1000 deduction + 1000 reimbursement | Net effect = 0, netPay same as without claims | [ ] | - | - |
| 35 | Net pay with deduction > reimbursement | 2000 deduction + 500 reimbursement | Net pay reduced by 1500 compared to no claims | [ ] | - | - |
| 36 | Net pay with reimbursement > deduction | 500 deduction + 2000 reimbursement | Net pay increased by 1500 compared to no claims | [ ] | - | - |
| 37 | Total deductions at payrun level includes claims | GET /api/payruns/{id} | totalDeductions = sum of all employee totalDeductions (includes claim deductions) | [ ] | - | - |
| 38 | totalClaimDeduction at payrun level | GET /api/payruns/{id} | totalClaimDeduction = sum of all employee claimDeduction values | [ ] | - | - |
| 39 | totalClaimReimbursement at payrun level | GET /api/payruns/{id} | totalClaimReimbursement = sum of all employee claimReimbursement values | [ ] | - | - |

---

### Section 6: PayRun Approve

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 40 | Approve payrun - deduction status becomes PROCESSED | PUT /api/payruns/{id}/approve, check employee_deduction table | status = PROCESSED | [ ] | - | - |
| 41 | Approve payrun - reimbursement status becomes PAID | PUT /api/payruns/{id}/approve, check employee_reimbursement_request table | paymentStatus = PAID | [ ] | - | - |
| 42 | Approve payrun - employee list shows PROCESSED/PAID | GET /api/payrun-employees/list/{id} after approval | claimDeductionStatus = "PROCESSED", claimReimbursementStatus = "PAID" | [ ] | - | - |
| 43 | After approval, deduction cannot be deleted | Try DELETE on PROCESSED deduction | 400 - Cannot delete | [ ] | - | - |
| 44 | After approval, deduction cannot be updated | Try PUT on PROCESSED deduction | 400 - Cannot update | [ ] | - | - |

---

### Section 7: PayRun Reject

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 45 | Reject payrun - deduction reverts to ACTIVE | PUT /api/payruns/{id}/reject, check DB | status = ACTIVE | [ ] | - | - |
| 46 | Reject payrun - reimbursement reverts to UNPAID | PUT /api/payruns/{id}/reject, check DB | paymentStatus = UNPAID, payrunId = null | [ ] | - | - |
| 47 | Reject payrun - employee list shows ACTIVE/UNPAID | GET /api/payrun-employees/list/{id} after rejection | claimDeductionStatus = "ACTIVE", claimReimbursementStatus = "UNPAID" | [ ] | - | - |
| 48 | After rejection, deduction can be updated | PUT on ACTIVE deduction | 200 - Updated | [ ] | - | - |
| 49 | After rejection, deduction can be deleted | DELETE on ACTIVE deduction | 200 - Deleted | [ ] | - | - |
| 50 | After rejection, claims picked up by next payrun | Create new payrun for same month | Claims included again with correct amounts | [ ] | - | - |

---

### Section 8: PayRun Delete

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 51 | Delete payrun - deduction reverts to ACTIVE | DELETE /api/payruns/{id}, check DB | status = ACTIVE | [ ] | - | - |
| 52 | Delete payrun - reimbursement reverts to UNPAID | DELETE /api/payruns/{id}, check DB | paymentStatus = UNPAID, payrunId = null | [ ] | - | - |
| 53 | After deletion, deduction can be updated | PUT on ACTIVE deduction | 200 - Updated | [ ] | - | - |
| 54 | After deletion, deduction can be deleted | DELETE on ACTIVE deduction | 200 - Deleted | [ ] | - | - |
| 55 | After deletion, claims picked up by next payrun | Create new payrun for same month | Claims included again | [ ] | - | - |

---

### Section 9: UI - PayRun Employee Table

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 56 | Claim Deduction column visible in preview page | Create payrun with deductions, check preview.js table | "CLAIM DEDUCTION" column shows correct amount | [ ] | - | - |
| 57 | Claim Reimbursement column visible in preview page | Create payrun with reimbursements, check preview.js table | "CLAIM REIMBURSEMENT" column shows correct amount | [ ] | - | - |
| 58 | Claim Deduction column visible in summary page | Approve payrun, check summary.js table | "CLAIM DEDUCTION" column shows correct amount | [ ] | - | - |
| 59 | Claim Reimbursement column visible in summary page | Approve payrun, check summary.js table | "CLAIM REIMBURSEMENT" column shows correct amount | [ ] | - | - |
| 60 | DEDUCTIONS column shows correct total (including claim) | Check preview.js DEDUCTIONS column | Shows totalDeductions from backend (EPF + PT + TDS + claim deduction) | [ ] | - | - |
| 61 | Columns show ₹0 when no claims | Employee with no deductions/reimbursements | Both columns show ₹0 | [ ] | - | - |

---

### Section 10: UI - Salary Slip / Payslip

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 62 | Claim Deduction visible on admin payslip view | View payslip of employee with claim deduction | "Claim Deduction" row appears under DEDUCTIONS with correct amount | [ ] | - | - |
| 63 | Claim Reimbursement visible on admin payslip view | View payslip of employee with claim reimbursement | "Claim Reimbursement" row appears under EARNINGS with correct amount | [ ] | - | - |
| 64 | Claim Deduction visible on employee portal payslip | Employee views their own payslip | "Claim Deduction" row appears under DEDUCTIONS | [ ] | - | - |
| 65 | Claim Reimbursement visible on employee portal payslip | Employee views their own payslip | "Claim Reimbursement" row appears under EARNINGS | [ ] | - | - |
| 66 | Payslip hides claim rows when amount is 0 | Employee with no claims | No "Claim Deduction" or "Claim Reimbursement" rows shown | [ ] | - | - |
| 67 | Payslip total deductions includes claim deduction | Check "Total Deductions" on payslip | Includes EPF + PT + TDS + claim deduction | [ ] | - | - |
| 68 | Payslip net pay is accurate | Check "Net Pay" on payslip | Matches: grossEarnings + claimReimbursement - totalDeductions | [ ] | - | - |

---

### Section 11: Edge Cases & Month Matching

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 69 | Deduction month format matching | Deduction saved as "2026-09-01" (LocalDate), payrun processingPeriod = "September 2026" | Correctly matched and picked up | [ ] | - | - |
| 70 | Reimbursement month format matching | Reimbursement saved as "2026-09" (String), payrun processingPeriod = "September 2026" | Correctly matched and picked up (converted to "2026-09") | [ ] | - | - |
| 71 | Employee with no CTC but has deduction | Employee without CTC structure but has ACTIVE deduction | Employee skipped from payrun (no CTC = not eligible), deduction stays ACTIVE | [ ] | - | - |
| 72 | Multiple employees with different claims | Emp A: 1000 deduction, Emp B: 2000 reimbursement, Emp C: both | Each employee shows their own correct values independently | [ ] | - | - |
| 73 | PayRun for month with zero claims | No deductions or reimbursements for that month | claimDeduction = 0, claimReimbursement = 0 for all employees, no errors | [ ] | - | - |
| 74 | Create two payruns for same month (should not duplicate) | Create payrun 1 (claims go INPAYRUN), delete it, create payrun 2 | Payrun 2 picks up claims again (reverted to ACTIVE after delete) | [ ] | - | - |
| 75 | Payrun with LOP + deduction + reimbursement | Employee has LOP days + deduction + reimbursement | All three factors correctly applied to netPay without conflict | [ ] | - | - |

---

### Section 12: Payment & Post-Payment

| # | Test Scenario | Steps | Expected Result | Status | Tested By | Remarks |
|---|---------------|-------|-----------------|--------|-----------|---------|
| 76 | Payment API includes claim fields in response | POST /api/payruns/{id}/payment | After payment, employee payslip API returns claimDeduction and claimReimbursement | [ ] | - | - |
| 77 | Payslip sent via email includes claim data | Check email payslip after payment | Claim deduction and reimbursement visible on email payslip | [ ] | - | - |
| 78 | After payment, deduction stays PROCESSED | Check DB after full flow (create → approve → payment) | status = PROCESSED permanently | [ ] | - | - |
| 79 | After payment, reimbursement stays PAID | Check DB after full flow | paymentStatus = PAID permanently | [ ] | - | - |

---

## Summary

| Section | Total Tests | Pass | Fail | Pending |
|---------|-------------|------|------|---------|
| Deduction CRUD | 11 | - | - | 11 |
| Reimbursement CRUD | 4 | - | - | 4 |
| PayRun Creation - Deduction | 7 | - | - | 7 |
| PayRun Creation - Reimbursement | 9 | - | - | 9 |
| Net Pay Calculation | 8 | - | - | 8 |
| PayRun Approve | 5 | - | - | 5 |
| PayRun Reject | 6 | - | - | 6 |
| PayRun Delete | 5 | - | - | 5 |
| UI - PayRun Table | 6 | - | - | 6 |
| UI - Salary Slip | 7 | - | - | 7 |
| Edge Cases | 7 | - | - | 7 |
| Payment & Post-Payment | 4 | - | - | 4 |
| **TOTAL** | **79** | - | - | **79** |
