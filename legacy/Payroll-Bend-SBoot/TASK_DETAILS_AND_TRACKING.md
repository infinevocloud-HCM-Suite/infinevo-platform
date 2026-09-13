# Payroll Project - Task Tracker

---

## Task Board

| # | Module | Task | Assigned To | Email | Priority | Status | Start Date | Due Date | Blocker | Requirement Created By | Assigned By | Remarks |
|---|--------|------|-------------|-------|----------|--------|------------|----------|---------|------------------------|-------------|---------|
| 1 | Deduction | Create DELETE API - only allow if status is ACTIVE, reject if INPAYRUN/PROCESSED | Biren |  | High | TODO | - | - | - | Sayeed | Sayeed | Endpoint: `DELETE /api/employee-deductions/{id}`. Check status in `employee_deduction` table before deleting. Return 400 if not ACTIVE. |
| 2 | Deduction | Create UPDATE API - only allow if status is ACTIVE, reject if INPAYRUN/PROCESSED | Biren | - | High | TODO | - | - | - | Sayeed | Sayeed | Endpoint: `PUT /api/employee-deductions/{id}`. Updatable fields: deductionAmount, deductionMonth, reason, remarks, proofUrl, proofPublicId. |

---

## Status Legend

| Status | Meaning |
|--------|---------|
| TODO | Not started |
| IN PROGRESS | Currently being worked on |
| IN REVIEW | Code done, waiting for review |
| TESTING | Under QA testing |
| DONE | Completed and verified |
| BLOCKED | Cannot proceed due to blocker |

---

## Test Tracker

| # | Module | Task Ref | Test Scenario | Expected Result | Tested By | Test Status | Remarks |
|---|--------|----------|---------------|-----------------|-----------|-------------|---------|
| 1 | Deduction | Task #1 | Delete deduction with status ACTIVE | 200 - Deleted successfully | - | TODO | - |
| 2 | Deduction | Task #1 | Delete deduction with status INPAYRUN | 400 - "Cannot delete deduction. It is already in payrun or processed." | - | TODO | - |
| 3 | Deduction | Task #1 | Delete deduction with status PROCESSED | 400 - "Cannot delete deduction. It is already in payrun or processed." | - | TODO | - |
| 4 | Deduction | Task #1 | Delete deduction with invalid ID | 404 - Not found | - | TODO | - |
| 5 | Deduction | Task #1 | Delete deduction of another organization | 404 - Not found (org filter) | - | TODO | - |
| 6 | Deduction | Task #2 | Update deduction with status ACTIVE | 200 - Updated successfully | - | TODO | - |
| 7 | Deduction | Task #2 | Update deduction with status INPAYRUN | 400 - "Cannot update deduction. It is already in payrun or processed." | - | TODO | - |
| 8 | Deduction | Task #2 | Update deduction with status PROCESSED | 400 - "Cannot update deduction. It is already in payrun or processed." | - | TODO | - |
| 9 | Deduction | Task #2 | Update deduction with invalid ID | 404 - Not found | - | TODO | - |
| 10 | Deduction | Task #2 | Update deduction of another organization | 404 - Not found (org filter) | - | TODO | - |
| 11 | Deduction | Task #2 | Update with missing required fields | 400 - Validation error | - | TODO | - |

---

## Notes

- Both APIs are independent of payrun flow. They only check `status` column in `employee_deduction` table.
- `organizationId` comes from request header for multi-tenant isolation.
- Once deduction is `INPAYRUN` or `PROCESSED`, it is immutable (no edit/delete).
