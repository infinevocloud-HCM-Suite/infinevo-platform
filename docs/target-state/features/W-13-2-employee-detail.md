# Feature: Employee detail sections

| Field | Value |
|---|---|
| **Feature ID** | `W-13.2` · from ticket #14 · `CORE-04` |
| **Promoted to** | `docs/target-state/features/W-13-2-employee-detail.md` on branch `W-13-2-employee-detail` — **`W-13-2` with hyphens**, never `W-13.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured), DEBT-004 (discounted) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` — the detail tables reference `core.employee` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | **5 scripts, one table each** | 1 — **exception granted 2026-09-22** |
| Externally testable behaviour | each detail section round-trips against its employee | 1 |
| Frontend area | none | 1 |

The founder granted the aggregate exception on the migration axis when approving the split:
five flat satellites of one aggregate root ship together rather than as five tickets. The
one-table-per-script rule — `migration/README.md:127-129` — still holds, so there are five
scripts, not one.

---

## 1. Problem

The five detail sections exist on one side each, so neither product can be copied.

| Section | HRMS | Payroll |
|---|---|---|
| Personal | `personal.java`, 11 columns | `EmployeePersonalDetail.java`, 16 including an embedded address |
| Contact | `Contact.java`, 18 columns | none — only `personalMail` and the embedded address |
| Identification | `Identification.java`, 12 columns | PAN only, on the personal row |
| Employment | `Work.java`, 10 columns | on the root row |
| Bank | none | `EmployeeBankDetail.java`, 9 columns |

Two products, two answers, and `02-data-model.md:69-73` names the target for each. The
residential address is modelled twice — free-text columns in HRMS `Contact.java`, an embedded
value object in Payroll's `EmployeePersonalDetail.java:188` — and they cannot both survive.

## 2. Scope

**In scope**

- `core.employee_personal`, `core.employee_contact`, `core.employee_identification`, `core.employee_employment`, `core.employee_bank`
- One-to-one with `core.employee`, each optional, each independently writable
- Read and write per section, under RLS
- **Added by the founder on 2026-09-23, after approval: this ticket turns the audit trail on.**
  `W-22.1` shipped the capture mechanism and it has recorded **nothing** since, because no
  production class carries `@Audited` — its own spec §2 records that `core.tenant` and
  `core.user_tenant` could not be the proof, since both are reached by raw JDBC and a Hibernate
  listener cannot observe them. These five tables are the first that can be. Two parts:
  1. Annotate the employee tables `@Audited`, so a change to a person's bank account or identity
     document produces an audit row.
  2. **Fix the redaction gap `W-22.1` deferred, first.** A property mapping to more than one
     column — an `@Embedded` or a `@ManyToOne` — falls back to its Java property name and
     `String.valueOf(object)`, and the deny-list matches database column names, so it cannot
     match. `employee_bank` and `employee_identification` are precisely the tables that hold an
     account number, an IFSC and a PAN. **Annotating them before fixing this writes that data
     into `core.audit_log` in clear**, so the order is not negotiable.

**Out of scope**

- The root record — `W-13.1`
- Search and listing — `W-13.3`
- The three approver levels on `Report.java` — they go to `W-14`'s `reporting_line`, per the split decision
- Document uploads behind identification proofs — `W-21` document store (#25)
- Any bank-detail validation against a real IFSC directory

## 3. Flow

```
[API client] --> [EmployeeDetailController] --> [section service]
   --> TenantContext bound by W-08 --> [core.employee_* under RLS]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../employee/EmployeeDetailController.java` | new — five sub-resources |
| Service | `core/.../employee/detail/*Service.java` | new, one per section |
| Entity | `core/.../employee/detail/EmployeePersonal.java` and four siblings | new, each `@Table(schema="core")` |
| Repository | `core/.../employee/detail/*Repository.java` | new, one per section |
| DTO | `core/.../employee/detail/*Request.java`, `*Response.java` | new |
| Enumeration | `core/.../employee/detail/PaymentMode.java`, `BankAccountType.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees/{id}/personal` | — | `EmployeePersonalResponse` | Bearer, tenant bound |
| PUT | `/api/v1/employees/{id}/personal` | request body | `200` | Bearer, tenant bound |

and the same pair for `/contact`, `/identification`, `/employment`, `/bank`.

`PUT` and not `POST`: a section is a part of an employee, created on first write. There is no
separate creation step and no way to have two of one section.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V015__employee_personal.sql` | `core.employee_personal` | yes | additive |
| `core/V016__employee_contact.sql` | `core.employee_contact` | yes | additive |
| `core/V017__employee_identification.sql` | `core.employee_identification` | yes | additive |
| `core/V018__employee_employment.sql` | `core.employee_employment` | yes | additive |
| `core/V019__employee_bank.sql` | `core.employee_bank` | yes | additive |

**Version numbers were assigned when the branch was cut** and are recorded above as shipped.
They start at `V015` and not at the `V011` the plan reserved, because `W-14.1` merged first and
took `V011`–`V014`. The live rule is the next number **above everything on `main`**, not the next
free one: a script below one that has already been applied fails `validate-on-migrate` on every
existing dev volume, and CI cannot catch it because CI starts from an empty database. The
sequence is global — `migration/README.md:17-31`.

Every table carries `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · its own columns · the four audit
columns.

- [x] `tenant_id` present on all five, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id)` unique on each; plus `(tenant_id, pan)` and `(tenant_id, aadhaar)` on identification
- [x] Money columns — none. Bank holds an account number, which is a **string**, never numeric
- [x] Expand / contract — five new tables, no destructive step

Each script carries its own `ENABLE ROW LEVEL SECURITY` and `tenant_isolation` policy in the
exact `CASE` form — `migration/README.md:76-123`.

**Address, settled.** HRMS's free-text columns lose to Payroll's structured form —
`address_line1`, `address_line2`, `city`, `state`, `state_code`, `zip_code` — on
`employee_contact`, because payroll filings need a state code and free text cannot supply one.
Permanent address repeats the same six columns rather than being a second row.

**`employee_employment`** takes six of HRMS `Work.java`'s ten columns — pay grade, workstation,
timezone and the two shift times — plus `Report.java`'s `note`. **As built, it is six columns and
not ten**, and the four left out are the ones that already have a home: `department` and `jobTitle`
are `core.employee.department_id` and `designation_id` (`W-14.1`, `V014__employee_org_columns.sql`),
and `doj` and `terminationDate` are `core.employee.date_of_joining` and `termination_date`
(`W-13.1`, `V010__employee.sql`). A second copy of a joining date is how two answers to "when did
this person start" get written, and a pay run cannot tell which is right. Its three approver columns
do **not** come here either; they go to `W-14`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../employee/detail/EmployeeDetailServiceTest.java` | first write creates, second updates, never two rows per section |
| Integration | `core/.../employee/detail/EmployeeDetailRlsIT.java` | tenant A cannot read any of the five tables for tenant B's employee |
| Integration | `core/.../employee/detail/EmployeeDetailCascadeIT.java` | a detail row cannot reference an employee in another tenant |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`EmployeeDetailCascadeIT` matters more than it looks: a foreign key alone does not stop a
cross-tenant reference, because `migration_user` owns the tables and bypasses RLS. The test
asserts the check as `app_user`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in employee_personal employee_contact employee_identification employee_employment employee_bank; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all five | `t` five times |
| Policy count per table | exactly one, named `tenant_isolation` |
| Bank account number type | `character varying`, never numeric |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Five scripts, and one forgets its RLS policy | **medium — this is the ticket's main hazard** | The verification loop checks all five; CI gate B checks it again |
| Bank account number stored as a number and leading zeros are lost | low, irreversible | Column is `varchar`, asserted in verification |
| A script omits its `core.` prefix and the table lands elsewhere silently | medium | `migration/README.md:43-50` — review reads the first word after `CREATE` |
| The approver columns get pulled in "while we are here" | medium | Named in **Out of scope**; `W-14` owns them |
| PAN and aadhaar are indexed, making them easy to enumerate | low | Indexes are tenant-scoped; RLS still applies to every read |

## 10. Rollback

Nothing is deployed. All five scripts are additive and forward-only —
`migration/README.md:135-143`. A section can be withdrawn from the API without touching the
schema.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all five, each in its own script |
| Flyway only, `ddl-auto` nowhere | five scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | `(tenant_id, employee_id)` on each, plus two on identification |
| Expand / contract | five new tables |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 missing tenant indexes | **Honoured** on all five |
| DEBT-004 secrets in `.properties` (`GAP_INVENTORY.md:42`) | **Discounted.** Bank details are data, not configuration, and no credential is introduced |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Is a bank account number encrypted at rest?** Postgres on Azure encrypts the whole disk, which covers loss of media but not a reader with `app_user`. Column-level encryption would mean it cannot be searched or indexed. **Recommend** disk encryption only for now, and a `W-57` security-hardening item to revisit.
2. **Permanent address as six repeated columns, or a second `employee_contact` row typed by purpose?** **Recommend** repeated columns — one row per employee keeps the RLS story and the unique index simple.
