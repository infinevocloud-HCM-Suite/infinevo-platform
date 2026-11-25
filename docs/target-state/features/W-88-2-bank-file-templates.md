# Feature: Bank file templates — the company designs its own bank upload file

| Field | Value |
|---|---|
| **Feature ID** | `W-88.2` · `PAY-BANK-FILE-TEMPLATES` |
| **Owner** | Claude (`dev-claude-w88-2`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`, `code/frontend` (`src/payroll/settings`, `src/payroll/payrun`) |
| **Builds on** | `W-88` on `main` `6600fed2` — `payroll/payrun/bankfile/`, its omission rules and `X-BankFile-*` totals |
| **Status** | Ready — founder design decisions 2026-11-25; standing approval, no spec stop |
| **Written by** | Claude, 2026-11-25 |

## Founder decisions (2026-11-25)

| # | Decision |
|---|---|
| 1 | Not one bank's layout: the user names each column and picks what fills it, and saves the result as a template |
| 2 | Many templates per company, reused every month |
| 3 | A file can cover all employees, a filtered set, or ticked ones |
| 4 | Payroll officer and tenant admin edit templates |
| 5 | Generate from the pay run page; templates live under Payroll › Settings › Bank files |
| 6 | Every generated file is stored and listed on the pay run with who, when, rows and total |
| 7 | W-88's layout ships as a built-in "Generic bulk NEFT" template |

## Size

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `payroll` | 1 | Pass |
| Flyway migration | 1 — `V224` | 1 | Pass |
| Testable behaviour | an officer builds a template, then downloads a pay run's bank file in that layout | 1 | Pass |
| Frontend area | `payroll/settings` (template editor) and `payroll/payrun` (generate drawer, history) | 1 | **Over by one** — the second is the place the template is used |

Size **M**.

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The bank file has one fixed layout of ten columns. Every bank's bulk upload wants its own column names, order and formats, so the officer edits the CSV by hand each month | `BankFileServiceImpl.java` `COLUMNS` (W-88) |
| 2 | The file always covers every included employee. A company that pays two banks, or pays a few people late, cannot make a file for part of the run | `BankFileServiceImpl.compute` reads every `INCLUDED` row |
| 3 | Nothing records that a file was made, so nobody can tell which file went to the bank | `PayRunController.exportBankFile` streams and forgets |

## 2. Scope

**In scope**
- `payroll.bank_file_template`: a named, ordered list of columns, a file format (CSV or XLSX) and a header-row switch, per tenant.
- A fixed **field catalogue** in code (§4.1); each column is a catalogue field, a fixed text, or a serial number, with a heading and, for dates and amounts, a format.
- A built-in read-only template, *Generic bulk NEFT*, identical to W-88's one-click file (same columns, same cells; a unit test compares them). It can be copied into an editable one.
- Template API: list, get, create, update, delete, copy.
- Generate: preview and create a file for a pay run from a template and an employee selection; the file is stored as a tenant `EXPORT` document and recorded in `payroll.bank_file_export`.
- History: list the files made for a run; download one again.
- Screens: Payroll › Settings › Bank files (list + editor); on the pay run page, **Bank file** opens a drawer: template, employees, preview, generate; a **Bank files** card lists the history.

**Out of scope**
- Fixed-width text layouts (some CMS uploads). CSV and XLSX only; a later ticket if a customer's bank needs one.
- Sending the file to the bank (H2H/API). The officer uploads it to net banking.
- Fields outside the catalogue (custom employee fields, PAN). A new field is added in code.
- Changing W-88's `GET …/bank-file`; it stays as the one-click generic download.

## 3. Flow

```
Settings › Bank files --> POST /api/v1/payroll/bank-file-templates (name, format, header, columns[])
Pay run page --> Bank file drawer --> GET  …/bank-file-templates            (built-in + tenant's)
             --> choose template + employees --> POST …/payruns/{id}/bank-files/preview
                     <-- first 20 rows, totals, omitted employee numbers with reasons
             --> Generate --> POST …/payruns/{id}/bank-files
                     --> W-88 omission rules --> rows in template order --> CsvWriter / XlsxStreamingWriter
                     --> DocumentService.store(EXPORT, null, file) --> payroll.bank_file_export row
                     <-- the export record; the browser then downloads it
             --> Bank files card --> GET …/payruns/{id}/bank-files --> GET …/bank-files/{exportId}/download
```

## 4. Backend changes (`payroll`)

### 4.1 Field catalogue — `BankFileField` (enum)

| Field | Value written | Source |
|---|---|---|
| `EMPLOYEE_NUMBER` | `E-101` | `core.employee.employee_number` |
| `EMPLOYEE_NAME` | first middle last | `core.employee` |
| `FIRST_NAME`, `LAST_NAME` | as stored | `core.employee` |
| `ACCOUNT_HOLDER_NAME` | as stored; falls back to employee name when blank (W-88's one-click file now does the same) | `core.employee_bank` |
| `ACCOUNT_NUMBER` | as text, leading zeros kept | `core.employee_bank` |
| `IFSC` | upper case (W-88's one-click file now does the same) | `core.employee_bank` |
| `BANK_NAME` | as stored | `core.employee_bank` |
| `ACCOUNT_TYPE` | `SAVINGS` / `CURRENT` / `SALARY` | `core.employee_bank` |
| `NET_PAY` | amount format (§4.2) | `payroll.employee_payrun.net_pay` |
| `PAY_PERIOD` | `2026-11`, or `Nov 2026` with format `MMM yyyy` | `payroll.payrun.period` |
| `PAID_ON` | date format (§4.2); blank until the run is paid | `payroll.payrun.paid_on` |
| `VALUE_DATE` | date format; the date the file is generated | server clock, Asia/Kolkata |
| `WORK_EMAIL`, `MOBILE` | as stored | `core.employee` |
| `DEPARTMENT`, `DESIGNATION` | name | `core.department`, `core.designation` |
| `NARRATION` | `Salary 2026-11` — W-88's narration | `payroll.payrun.period` |

Each column is one of:

| Column `source` | Needs | Writes |
|---|---|---|
| `FIELD` | `field` from the catalogue | that value |
| `FIXED` | `value` (≤ 100 chars) | the same text on every row — e.g. `NEFT`, the company's debit account |
| `SERIAL` | — | 1, 2, 3 … in file order |

### 4.2 Formats

| Applies to | Options | Default |
|---|---|---|
| `NET_PAY` | `DECIMAL_2` (`65000.00`) · `WHOLE_RUPEES` (`65000`, `HALF_UP`) · `PAISE` (`6500000`) | `DECIMAL_2` |
| `PAID_ON`, `VALUE_DATE` | `dd/MM/yyyy` · `dd-MM-yyyy` · `yyyy-MM-dd` · `ddMMyyyy` · `MM/dd/yyyy` | `dd/MM/yyyy` |
| `PAY_PERIOD` | `yyyy-MM` · `MMM yyyy` · `MM/yyyy` | `yyyy-MM` |

A format on any other field is refused (`400`).

### 4.3 Rules

| Rule | Behaviour |
|---|---|
| Template name | required, ≤ 80 chars, unique per tenant ignoring case → `409` |
| Columns | 1 to 40; heading required, ≤ 60 chars; headings unique in the template → `400` |
| Built-in template | id `generic-neft`, never stored, cannot be changed or deleted (`409`); **copy** makes a tenant template from it |
| Who is in the file | W-88's rules unchanged: an `INCLUDED` employee with a bank row, `BANK_TRANSFER`, account number and IFSC, net pay > 0. The selection narrows that; it never adds anyone |
| Selection | `employeeIds` empty or absent = everyone the rules allow; otherwise only those ids, each of which must be in the run (`400` lists the strangers) |
| Run status | `APPROVED` or `PAID`, as W-88 → `409` otherwise |
| Sort | by employee number, as W-88 |
| CSV safety | `CsvWriter` (BOM, formula guard). XLSX through `XlsxStreamingWriter`; every cell is text so account numbers keep leading zeros |
| File name | template name made safe (`[A-Za-z0-9_-]`, others `_`) + `_` + period + extension |
| Storage | `DocumentService.store(DocumentKind.EXPORT, null, name, bytes)`; the export row keeps the document id. A storage failure fails the request (`503`) — a file the bank gets must be one the history shows |
| Template deleted later | its exports stay; `template_id` goes null, `template_name` keeps the name it had |

### 4.4 API

| Verb | Path | Action | Returns |
|---|---|---|---|
| GET | `/api/v1/payroll/bank-file-templates` | `payroll.bank_file.export` | built-in first, then the tenant's by name |
| GET | `/api/v1/payroll/bank-file-templates/fields` | `payroll.bank_file.export` | the catalogue, with each field's allowed formats |
| GET | `/api/v1/payroll/bank-file-templates/{id}` | `payroll.bank_file.export` | one template (`generic-neft` included) |
| POST | `/api/v1/payroll/bank-file-templates` | `payroll.settings.manage` | `201` |
| PUT | `/api/v1/payroll/bank-file-templates/{id}` | `payroll.settings.manage` | `200` |
| DELETE | `/api/v1/payroll/bank-file-templates/{id}` | `payroll.settings.manage` | `204` |
| POST | `/api/v1/payroll/bank-file-templates/{id}/copy` | `payroll.settings.manage` | `201`, named "Copy of …" (numbered when taken) |
| POST | `/api/v1/payroll/payruns/{id}/bank-files/preview` | `payroll.bank_file.export` | headings, first 20 rows, row count, file total, run net pay, omitted `[{employeeNumber, reason}]` |
| POST | `/api/v1/payroll/payruns/{id}/bank-files` | `payroll.bank_file.export` | `201`, the export record |
| GET | `/api/v1/payroll/payruns/{id}/bank-files` | `payroll.bank_file.export` | the run's exports, newest first |
| GET | `/api/v1/payroll/payruns/{id}/bank-files/{exportId}/download` | `payroll.bank_file.export` | the stored file |

Request body for preview and generate: `{ "templateId": "…", "employeeIds": ["…"] }`.

### 4.5 Classes

| Class | Role |
|---|---|
| `BankFileField`, `BankFileColumnSource`, `BankFileValueKind`, `BankFileValues` | catalogue and formats (§4.1, §4.2) |
| `BankFileColumn` (record, stored as JSON) | `source`, `field`, `value`, `heading`, `format` |
| `BankFileTemplate` (entity), `BankFileTemplateRepository` | the template |
| `BankFileExport` (entity), `BankFileExportRepository` | the history row |
| `BankFileTemplateService(Impl)` | CRUD, copy, the built-in, validation |
| `BankFileExportService(Impl)` | preview, generate, list, download; reads W-88's `BankFileServiceImpl` rows |
| `BankFileTemplateController`, `BankFileExportController` | §4.4 |
| `BankFileServiceImpl` | gains `run(payrunId, employeeIds)` returning rows **and** omissions with reasons; W-88's `generate` uses it, so both files apply the same rules. Its rows now carry the holder-name fallback and upper-case IFSC |

`BankFileRun` carries the employee records alongside the rows, so first and last name, work email, mobile, department and designation come from them; paid-on and period come from the run. Department and designation are read from each employee inside the preview or generate transaction.

## 5. Frontend changes

| Where | What |
|---|---|
| `payroll/settings/BankFileTemplates.jsx` | list: name, format, columns, updated; New, Edit, Copy, Delete; the built-in shows a lock and only Copy |
| `payroll/settings/BankFileTemplateEditor.jsx` | name, CSV/XLSX, header row; a table of columns with heading, source, field or text, format; move up/down; add, remove; a sample row from made-up data so the officer sees the layout before saving |
| `SettingsLayout.jsx`, `payroll/index.js` | "Bank files" tab, route `/payroll/settings/bank-files` |
| `payroll/payrun/BankFileDrawer.jsx` | template picker; employees: All, or a table with search by number or name, and ticks (department filter left for later); the table loads every page of the run, 100 at a time; Preview (table + totals + omitted list with reasons); Generate downloads the stored file |
| `payroll/payrun/BankFileHistory.jsx` | card on the run page: when, by whom, template, rows, total, Download |
| `RunPage.jsx` | a **Bank file…** button beside W-88's **Download Bank File**, same visibility rule (action + `APPROVED`/`PAID`); the history card |
| `bankFileService.js` | the calls in §4.4 |

## 6. Database changes — `V224__bank_file_templates.sql`

```sql
CREATE TABLE payroll.bank_file_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name VARCHAR(80) NOT NULL,
    file_format VARCHAR(8) NOT NULL CHECK (file_format IN ('CSV', 'XLSX')),
    include_header BOOLEAN NOT NULL DEFAULT true,
    columns JSONB NOT NULL,
    created_at, created_by, updated_at, updated_by
);
CREATE UNIQUE INDEX uk_bank_file_template_name ON payroll.bank_file_template (tenant_id, lower(name));

CREATE TABLE payroll.bank_file_export (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    payrun_id UUID NOT NULL REFERENCES payroll.payrun(id),
    template_id UUID NULL REFERENCES payroll.bank_file_template(id) ON DELETE SET NULL,
    template_name VARCHAR(80) NOT NULL,
    document_id UUID NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    row_count INT NOT NULL,
    file_total NUMERIC(19,4) NOT NULL,
    selected_employee_count INT NULL,   -- null = everyone
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL
);
CREATE INDEX idx_bank_file_export_run ON payroll.bank_file_export (tenant_id, payrun_id, created_at DESC);
```

Both tables: RLS on, `tenant_isolation` policy (the V190 form). No reference data.

## 7. Tests

| Test | Proves |
|---|---|
| `BankFileTemplateServiceTest` | name and heading rules, 40-column cap, format refused on a non-date/amount field, built-in cannot change, copy naming |
| `BankFileExportServiceTest` | columns in template order; FIXED and SERIAL; each amount and date format; omitted with reasons; CSV and XLSX; stored with `EXPORT` and recorded |
| `BankFileServiceTest` | selection narrows and never adds; a stranger id is refused; holder and IFSC rules; the built-in template gives W-88's file exactly |
| `ShippedMigrationsIT` (existing) | both tables have `tenant_id`, RLS on and the `tenant_isolation` policy |
| `BankFileGuardIT` | each endpoint's action; `403` without it |
| `BankFileTemplates.test.jsx`, `BankFileTemplateEditor.test.jsx` | list, built-in lock, add/move/remove columns, save body |
| `BankFileDrawer.test.jsx`, `BankFileHistory.test.jsx` | preview call with ticked ids, generate, download, omitted list |

## 8. Verification

```
cd code/backend && ./mvnw -B -pl payroll -am verify
cd code/frontend && npx vitest run src/payroll && npx eslint src/payroll
```

## 9. Risks

| Risk | Mitigation |
|---|---|
| A bank rejects the file for a detail the catalogue lacks | FIXED columns cover most; a new field is a one-line enum addition |
| Two officers generate at once | each gets its own stored file and history row; nothing is shared |
| A large run makes a large preview | preview returns 20 rows and totals; the file itself is built only on Generate |

## 10. Rollback

Drop the two tables in a new migration; W-88's one-click download is untouched.
