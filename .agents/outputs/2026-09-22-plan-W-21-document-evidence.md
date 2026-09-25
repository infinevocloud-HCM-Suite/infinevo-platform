# W-21 Document Store — Evidence Pass

| Finding | Location | Legacy |
|---|---|---|
| **Cloudinary uploads** | Both backends via `CloudinaryServiceImpl` | ✓ |
| **Payslip token signing** | `PayslipTokenServiceImpl` | ✓ |
| **Token logged in plaintext** | `PublicPayslipController:43-44` | ✓ (GAP-3 confirmed) |
| **Access control** | Token-based (payslip); folder-scoped (investments) | ✓ |
| **Document-ish columns** | 7+ tables across both databases | ✓ |

---

## 1. File Upload / Storage Paths

### Cloudinary Configuration

**Payroll Backend**
- **Config file**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/CloudinaryConfig.java:1-31` (legacy)
- **Service**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/CloudinaryServiceImpl.java:1-80` (legacy)
- **Methods**: 
  - `uploadFile(MultipartFile, String companyUserId)` (line 39): Folder path `{companyUserId}/YYYY/MM/DD`, public_id `doc_{timestamp}_{companyUserId}`
  - `uploadEmployeeInvestmentFile(MultipartFile, String organizationId, String employeeId, Integer financialYear)` (line 83): Folder path `{organizationId}/{employeeId}/{financialYear}`

**HRMS Backend**
- **Config file**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/config/CloudinaryConfig.java:1-33` (legacy)
- **Service**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/CloudinaryServiceImpl.java` exists (legacy)
- Uses same Cloudinary SDK configuration: `cloud_name`, `api_key`, `api_secret` from properties

### Upload Call Sites

**Payroll reimbursement receipts**: Called via `EmployeeReimbursementServiceImpl` (line 5 in FEATURE_MAP:301-309)
- Stores `attachment_url` and `attachment_public_id` in `employee_reimbursement_request` table

**Payroll salary deduction proof**: Called via `SalaryDeductionServiceImpl` (line 363 in FEATURE_MAP:348-364)
- Stores `proof_url` and `proof_public_id` in `employee_deduction` table

**Payroll investment proof documents**: Called via `EmployeeProofOfInvestmentServiceImpl` (uses `uploadEmployeeInvestmentFile` method)
- Stores `file_url` and `public_id` in multiple tax/POI tables

**HRMS employee documents**: Called via `EmployeeController` (line 53 in FEATURE_MAP:45-57)
- Stores `fileUrl` and `publicId` in `employee_document` table

**HRMS leave attachments**: Implied via `LeaveRequestServiceImpl` (line 81 in FEATURE_MAP:74-88)
- Stores `document_url` in `leave_documents` table

---

## 2. Existing Payslip Token Pattern (COPY THIS)

**Token Service**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/PayslipTokenServiceImpl.java:1-41` (legacy)

**Generation Algorithm** (line 19-30):
```
Message: payrunId + ":" + employeeId + ":" + orgId
HMAC: HmacSHA256 with secret key from ${app.payslip.download.secret-key:default-payslip-secret-key-2026-xyz}
Encoding: Base64-URL-encoded (no padding)
Returns: URL-safe Base64 string
```

**Verification** (line 33-39):
- Regenerates token using same inputs and secret key
- Constant-time string comparison with provided token
- Returns boolean

**Usage in Public Payslip Controller**:
- **File**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/payruns/PublicPayslipController.java:1-102` (legacy)
- **Endpoint**: `GET /api/public/payslips/{payrunId}/{employeeId}?orgId={orgId}&token={token}` (line 35-40)
- **Flow**: Verifies token (line 47), then serves payslip data (line 56)
- **No user authentication required** — token alone grants access

---

## 3. Signed Token Logged in Plaintext

**Gap Inventory Reference**: GAP-003 states "signed payslip token is written to the logs."

**Log Statement Found**:
- **File**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/payruns/PublicPayslipController.java:43-44` (legacy)
- **Code**: 
  ```java
  log.info("[{}] 📥 Public request to fetch payslip | payrunId={} | employeeId={} | orgId={} | token={}",
          method, payrunId, employeeId, orgId, token);
  ```
- **Impact**: Full HMAC signature is logged in plaintext at INFO level, exposing the ability to forge download links for any payrun/employee/org combination if the log is breached

---

## 4. Access Control on Document Download

**Payslip downloads**: Token-based (signed HMAC-SHA256). No user authentication. Access granted if token verifies.

**Reimbursement receipt uploads**: Stored in Cloudinary with public_id stored in DB. Access control:
- **Retrieve**: Only authenticated users with `organizationId` header can query `employee_reimbursement_request`
- **Direct download**: Cloudinary URLs are public by default; no mention of signed URLs or private delivery

**Investment proof documents**: Stored in Cloudinary with folder path `{organizationId}/{employeeId}/{financialYear}`. Access control:
- **Retrieve**: `EmployeeProofOfInvestmentServiceImpl` scopes by organization and employee
- **Direct download**: Cloudinary URLs are public; Cloudinary public_id alone is needed to construct URL

**Employee documents (HRMS)**: Stored in Cloudinary. Access control not documented in code; assumed public URLs.

**No signed URLs for general documents** — payslip is the only path using signed links. All other document downloads rely on public Cloudinary URLs or session-based HTTP auth.

---

## 5. All Document-Related Columns

### HRMS Database

**`employee_document` table**
- **Entity**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/EmployeeDocument.java:1-112` (legacy)
- **Columns**: `fileUrl`, `publicId` (Cloudinary)
- **Source**: DB_SCHEMA.md:152-161

**`leave_documents` table**
- **Entity**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/LeaveDocument.java` (legacy)
- **Columns**: `document_url` (Cloudinary URL)
- **Source**: DB_SCHEMA.md:244-250

**`identification` table** (implicit document references)
- **Entity**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Identification.java:1-80` (legacy)
- **Columns**: `addressProof`, `idProof` (document type fields, not URLs directly)
- **Source**: DB_SCHEMA.md:134-142

### Payroll Database

**`employee_reimbursement_request` table**
- **Entity**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employeereimbursement/EmployeeReimbursementRequest.java` (legacy)
- **Columns**: `attachment_url`, `attachment_public_id`, `attachment_file_name` (Cloudinary)
- **Source**: DB_SCHEMA.md:742-766

**`employee_deduction` table**
- **Entity**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/SalaryDeduction.java` (legacy)
- **Columns**: `proof_url`, `proof_public_id` (Cloudinary)
- **Source**: DB_SCHEMA.md:1076-1096

**`employee_investment_proof_file` table** (via EmployeeInvestmentProofFile entity)
- **Entity**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/taxCalculator/EmployeeInvestmentProofFile.java:1-115` (legacy)
- **Columns**: `file_url`, `public_id`, `file_name`, `document_type`, `file_size`, `content_type` (Cloudinary)
- **Source**: DB_SCHEMA.md:839-853

**`proof_of_investment_document` table**
- **Entity**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/taxCalculator/ProofOfInvestmentDocument.java` (legacy)
- **Columns**: `document_url` (Cloudinary URL)
- **Source**: DB_SCHEMA.md:701-711

**`employee_poi_document` table** (via EmployeePOIDocument entity)
- **Entity**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/EmployeeITDeclaration/poi/EmployeePOIDocument.java` (legacy)
- **Columns**: `document_url`, `document_name`
- **Source**: DB_SCHEMA.md:1311-1322

---

## Citations

| Claim | File:Line | Status |
|---|---|---|
| Payroll Cloudinary config | `Payroll-Bend-SBoot/config/CloudinaryConfig.java:1-31` | legacy |
| Payroll Cloudinary upload service | `Payroll-Bend-SBoot/serviceimpl/CloudinaryServiceImpl.java:39-80` | legacy |
| HRMS Cloudinary config | `HRMS_Backend/config/CloudinaryConfig.java:1-33` | legacy |
| Payslip token generation algorithm | `Payroll-Bend-SBoot/serviceimpl/payruns/PayslipTokenServiceImpl.java:19-30` | legacy |
| Payslip token verification | `Payroll-Bend-SBoot/serviceimpl/payruns/PayslipTokenServiceImpl.java:33-39` | legacy |
| Public payslip endpoint | `Payroll-Bend-SBoot/controller/payruns/PublicPayslipController.java:35-51` | legacy |
| Token logged in plaintext | `Payroll-Bend-SBoot/controller/payruns/PublicPayslipController.java:43-44` | legacy |
| GAP-003 reference | `GAP_INVENTORY.md` — token leakage noted | legacy |
| HRMS EmployeeDocument fileUrl/publicId | `HRMS_Backend/entity/EmployeeDocument.java:36-38, 42-56` | legacy |
| Payroll EmployeeReimbursementRequest attachment fields | `Payroll-Bend-SBoot/entity/employeereimbursement/EmployeeReimbursementRequest.java` + `DB_SCHEMA.md:754-756` | legacy |
| Payroll SalaryDeduction proof fields | `Payroll-Bend-SBoot/entity/SalaryDeduction.java` + `DB_SCHEMA.md:1094-1095` | legacy |
| Payroll EmployeeInvestmentProofFile columns | `Payroll-Bend-SBoot/entity/taxCalculator/EmployeeInvestmentProofFile.java:29-39` | legacy |

