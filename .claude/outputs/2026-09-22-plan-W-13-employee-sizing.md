# W-13 Employee Master Shape — Sizing Evidence

## Summary

| Aspect | Status |
|---|---|
| HRMS `employee` table structure | 7 entities across relationships (Employee + personal/contact/identification/work/report + OurUsers link) |
| Payroll `employee` table structure | 1 entity (BasicDetails) + 2 related entities (EmployeePersonalDetail, EmployeeBankDetail) |
| Column count: HRMS employee tree | ~100 columns across 6 related entities |
| Column count: Payroll employee tree | ~80 columns including bank detail and residential address |
| Major differences | HRMS has Report entity (reporting hierarchy); Payroll has bank detail; both have personal detail but different schemas |
| Search/list endpoints | HRMS: `/employees/all` (no pagination), `/employees/search?query=...` (free-text) |
| Payroll search/list endpoints | `/api/employees` with pagination (page, size, sortBy, sortDir); no free-text search |

---

## Part B1 — Column Counts & Structure

### HRMS Database — Employee Profile Tables

**Entity hierarchy:** `Employee` is the root, linking to 5 OneToOne related entities + `OurUsers`

#### `employee` (root entity)
Entity: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Employee.java:14-154`

| Column | Count | Notes |
|---|---|---|
| `id` (PK) | 1 | |
| Relationships (OneToOne) | 5 | personal, identification, work, contact, report |
| Timestamps | 2 | `createdAt`, `updatedAt` (with `@CreationTimestamp` / `@UpdateTimestamp`) |
| **Total columns on Employee class** | **8** | — |

#### `personal` (related entity)
Entity: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/personal.java:1-151`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `empId` (FK + unique) | VARCHAR | 1 |
| `employmentStatus` | VARCHAR | 1 |
| `firstName` | VARCHAR | 1 |
| `middleName` | VARCHAR | 1 |
| `lastName` | VARCHAR | 1 |
| `dateOfBirth` | DATE | 1 |
| `gender` | VARCHAR | 1 |
| `maritalStatus` | VARCHAR | 1 |
| `nationality` | VARCHAR | 1 |
| `ethnicity` | VARCHAR | 1 |
| **Total in personal** | | **11** |

#### `contact` (related entity)
Entity: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Contact.java:1-225`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `residentialAddress` | VARCHAR | 1 |
| `permanentAddress` | VARCHAR | 1 |
| `city` | VARCHAR | 1 |
| `state` | VARCHAR | 1 |
| `country` | VARCHAR | 1 |
| `postalCode` | VARCHAR | 1 |
| `workEmail` | VARCHAR | 1 |
| `personalEmail` | VARCHAR | 1 |
| `mobileNumber` | VARCHAR | 1 |
| `primaryEmergencyContactName` | VARCHAR | 1 |
| `primaryEmergencyContactNumber` | VARCHAR | 1 |
| `relationshipToPrimaryEmergencyContact` | VARCHAR | 1 |
| `secondaryEmergencyContactName` | VARCHAR | 1 |
| `secondaryEmergencyContactNumber` | VARCHAR | 1 |
| `relationshipToSecondaryEmergencyContact` | VARCHAR | 1 |
| `familyDoctorName` | VARCHAR | 1 |
| `familyDoctorContactNumber` | VARCHAR | 1 |
| **Total in contact** | | **18** |

#### `identification` (related entity)
Entity: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Identification.java:1-125`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `immigrationStatus` | VARCHAR | 1 |
| `aadharCardNumber` | VARCHAR | 1 |
| `panCardNumber` | VARCHAR | 1 |
| `addressProof` | VARCHAR | 1 |
| `addressDocumentName` | VARCHAR | 1 |
| `addressDocumentNumber` | VARCHAR | 1 |
| `personalTaxId` | VARCHAR | 1 |
| `socialInsurance` | VARCHAR | 1 |
| `idProof` | VARCHAR | 1 |
| `documentName` | VARCHAR | 1 |
| `documentNumber` | VARCHAR | 1 |
| **Total in identification** | | **12** |

#### `work` (related entity)
Entity: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java:1-131`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `department` | VARCHAR | 1 |
| `jobTitle` | VARCHAR | 1 |
| `payGrade` | VARCHAR | 1 |
| `doj` (date of joining) | DATE | 1 |
| `terminationDate` | DATE | 1 |
| `workstationId` | VARCHAR | 1 |
| `timeZone` | VARCHAR | 1 |
| `shiftStartTime` | VARCHAR | 1 |
| `shiftEndTime` | VARCHAR | 1 |
| **Total in work** | | **10** |

#### `report` (related entity)
Entity: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Report.java:1-106`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `reportingManagerId` | VARCHAR | 1 |
| `reportingManagerName` | VARCHAR | 1 |
| `indirectManager` | VARCHAR | 1 |
| `firstLevelApprover` | VARCHAR | 1 |
| `secondLevelApprover` | VARCHAR | 1 |
| `thirdLevelApprover` | VARCHAR | 1 |
| `note` | VARCHAR (2000) | 1 |
| **Total in report** | | **8** |

**HRMS Employee Tree Total: ~67 columns across 6 entities** (Employee root + 5 related)

---

### Payroll Database — Employee Profile Tables

**Entity hierarchy:** `BasicDetails` (called `employee` in table) is the root, with optional OneToOne `EmployeePersonalDetail` and `EmployeeBankDetail`

#### `employee` (BasicDetails entity)
Entity: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:1-200+`

| Column | Type | Count |
|---|---|---|
| `id` (PK, named `employee_id` in table) | BIGINT | 1 |
| `employeeId` (unique) | VARCHAR | 1 |
| `employeeNumber` (unique) | VARCHAR | 1 |
| `firstName` | VARCHAR | 1 |
| `middleName` | VARCHAR | 1 |
| `lastName` | VARCHAR | 1 |
| `gender` | VARCHAR | 1 |
| `dateOfJoining` | VARCHAR | 1 |
| `hrUser` | VARCHAR | 1 |
| `department_id` (FK) | BIGINT | 1 |
| `designation_id` (FK) | BIGINT | 1 |
| `work_location_id` (FK) | BIGINT | 1 |
| `employeeStatus` | VARCHAR | 1 |
| `isPortalEnabled` | BOOLEAN | 1 |
| `isEligibleForPf` | BOOLEAN | 1 |
| `isEligibleForPt` | BOOLEAN | 1 |
| `isEligibleForLwf` | BOOLEAN | 1 |
| `director` (isDirector) | BOOLEAN | 1 |
| `eligibleForEps` | BOOLEAN | 1 |
| `canContributeToEpsOnHigherWages` | BOOLEAN | 1 |
| `esiNumber` | VARCHAR | 1 |
| `mobile` | VARCHAR | 1 |
| `workMail` | VARCHAR | 1 |
| `pfAccountNumber` | VARCHAR | 1 |
| `uan` | VARCHAR | 1 |
| `tags` (ElementCollection) | COLLECTION | 1 |
| `amountInPercentage` | DOUBLE | 1 |
| `organizationId` (FK) | BIGINT | 1 |
| `employeeUniqueId` (unique) | VARCHAR | 1 |
| `eligibleForEsi` | BOOLEAN | 1 |
| `isDeleted` | BOOLEAN | 1 |
| **Total in BasicDetails** | | **31** |

#### `employee_personal_detail` (related entity)
Entity: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeePersonalDetail.java:1-167`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `personalMail` | VARCHAR | 1 |
| `dateOfBirth` | DATE | 1 |
| `fatherName` | VARCHAR | 1 |
| `pan` (unique) | VARCHAR | 1 |
| `differentlyAbledType` | VARCHAR | 1 |
| `isEligibleForFullIncomeTaxExemption` | BOOLEAN | 1 |
| `customFields` (JSON/LOB) | LOB | 1 |
| `organizationId` (FK) | BIGINT | 1 |
| `employee_id` (FK, unique) | BIGINT | 1 |
| **Embedded: ResidentialAddress** | — | **6** (addressLine1, addressLine2, city, state, stateCode, zipCode) |
| **Total in EmployeePersonalDetail** | | **16** |

#### `employee_bank_detail` (related entity)
Entity: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeeBankDetail.java:1-121`

| Column | Type | Count |
|---|---|---|
| `id` (PK) | BIGINT | 1 |
| `paymentMode` | VARCHAR | 1 |
| `accountHolderName` | VARCHAR | 1 |
| `bankName` | VARCHAR | 1 |
| `ifscCode` | VARCHAR | 1 |
| `bankAccountNumber` (unique) | VARCHAR | 1 |
| `bankAccountType` | VARCHAR | 1 |
| `employee_id` (FK, unique) | BIGINT | 1 |
| `organizationId` (FK) | BIGINT | 1 |
| **Total in EmployeeBankDetail** | | **9** |

**Payroll Employee Tree Total: ~56 columns across 3 entities** (BasicDetails root + 2 related)

---

## Part B2 — Differences Between Products' Employee Tables

| Concept | HRMS | Payroll | Notes |
|---|---|---|---|
| **Root table** | `employee` + `personal` | `employee` (BasicDetails) | HRMS splits empId/employmentStatus into personal; Payroll keeps in root |
| **Employment status** | `personal.employmentStatus` | `basicDetails.employeeStatus` | Different column names |
| **Reporting hierarchy** | `report` entity (8 cols: reporting_manager_id, name, indirect_manager, 3-level approvers, note) | Not present | HRMS has explicit reporting structure; Payroll does not |
| **Bank details** | Not present | `employee_bank_detail` entity (payment mode, account holder, bank name, IFSC, account number, type) | Payroll tracks payment mechanism; HRMS does not |
| **Identification docs** | `identification` entity (aadhar, pan, passport, uan, immigration status, tax ID, social insurance) | `employee_personal_detail.pan` only | HRMS more detailed; Payroll minimal |
| **Contact/emergency** | `contact` entity (addresses, work/personal email, mobile, 2 emergency contacts + family doctor) | Not present; personal detail only has `personalMail` + embedded address | HRMS richer emergency contact model |
| **Address structure** | Separate columns in `contact` (residential, permanent, city, state, postal) | Embedded `ResidentialAddress` in `employee_personal_detail` (addressLine1, addressLine2, city, state, stateCode, zipCode) | Both support residential; Payroll uses embeddable pattern |
| **Audit timestamps** | `employee.createdAt`, `employee.updatedAt` | Not found in BasicDetails; present on related entities only | Inconsistent in Payroll |
| **Soft delete** | Not present | `basicDetails.isDeleted` (BOOLEAN) | Payroll only |
| **Portal eligibility** | Not present | `basicDetails.isPortalEnabled` | Payroll only |
| **Statutory eligibility** | Not present | PF (isEligibleForPf), PT (isEligibleForPt), LWF (isEligibleForLwf), ESI (eligibleForEsi), EPS (eligibleForEps), EPF contribution flag | Payroll tracks detailed statutory status; HRMS does not |

---

## Part B3 — Search & Listing Endpoints

### HRMS Backend

**Controller:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/EmployeeController.java`

| Endpoint | Query Shape | Pagination | Filters | Free-text |
|---|---|---|---|---|
| `GET /employees/all` (line 187) | No parameters | **No** — returns full List<Employee> | None | None |
| `GET /employees/search?query=...` (line 172) | `@RequestParam String query` | **No** — returns List<Map<String,String>> | None | **Yes** — calls `employeeService.searchEmployees(query)` |
| `GET /employees/{id}` (line 200) | Path parameter (Long) | N/A — single record | None | None |
| `GET /employees/ids` (line 264) | No parameters | **No** — returns List<String> of all employee IDs | None | None |

**Key limitation:** No pagination on list endpoints; full result sets returned.

### Payroll Backend

**Controller:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/employee/BasicDetailsController.java:134-200+` (active implementation)

| Endpoint | Query Shape | Pagination | Filters | Free-text |
|---|---|---|---|---|
| `POST /api/employees` | Request body: `BasicDetailsDTO` | N/A — create | N/A | N/A |
| `PUT /api/employees/{employeeId}` | Path param + body | N/A — update | N/A | N/A |
| `GET /api/employees/{employeeId}` (line 198) | Path parameter: `employeeId` (String) | N/A — single record | Organization scoped via header | None |
| `GET /api/employees` (line 77-100 in commented section, repeated active at line ~200+) | Query parameters: `page` (default 0), `size` (default 20, max 100), `sortBy` (default "id"), `sortDir` (default "asc") | **Yes** — `Pageable` with `PageRequest.of(pageIndex, pageSize, sort)` (line 80-90) | **Organization-scoped** via `@RequestHeader("organizationId") String organizationId` | **No** — no free-text search parameter |
| `DELETE /api/employees/{employeeId}` | Path parameter | N/A — delete | Org scoped | N/A |
| `POST /api/employees/imports` | Request body: List<BasicDetailsDTO> | N/A — bulk | N/A | N/A |

**Key differences:**
- Payroll uses Spring Data `Pageable` with sorting (tenant scoped via header).
- No free-text search (unlike HRMS).
- Organization (tenant) scoped via header, not path parameter.

---

## Part B4 — Gaps and Uncertainties

| Gap | Impact on Merge |
|---|---|
| HRMS `report` hierarchy vs Payroll absence | W-13 must decide: port HRMS model, drop it, or add synthetic reporting lines from Payroll's organization structure |
| Different audit column presence | W-13 must enforce uniform timestamp columns on merged `employee` table |
| Bank detail only in Payroll | HRMS tenants will need bank detail added during merge (can be empty for existing records) |
| Statutory eligibility flags only in Payroll | HRMS tenants must provide defaults (probably all true for backward compat) |
| Search behavior differs | New platform must choose: free-text like HRMS, or pagination like Payroll, or both |
| `employeeUniqueId` unique constraint in Payroll | HRMS has no equivalent; may collide if both systems assign different uniqueness schemes |

