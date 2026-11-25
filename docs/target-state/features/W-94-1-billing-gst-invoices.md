# Feature: Billing, Pricing Matrix & GST Invoice Generation for Subscriptions

| Field            | Value                                                                |
| ---------------- | -------------------------------------------------------------------- |
| **Feature ID**   | `W-94.1` · `PLAT-BILLING`                                            |
| **Owner**        | Mohit (`dev-mohit`)                                                  |
| **Apps touched** | `code/backend/core`, `code/backend/migration`                        |
| **Related gaps** | none in `GAP_INVENTORY.md`; subscription billing is currently manual |
| **Status**       | Ready for founder review                                             |
| **Blocked by**   | `W-84.1` (Plan & employee limit, completed on main `27ec17ae`)       |

## 1. Size cap

| Axis                          | This spec                                                                                                                                                | Limit |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| Backend module                | `core`                                                                                                                                                   | 1     |
| Flyway migration              | `core/V198__billing_invoices_and_payments.sql`                                                                                                           | 1     |
| Externally testable behaviour | Calculates subscription fees from pricing matrix, generates GST tax invoices, records payments with transaction references, and tracks settlement status | 1     |
| Frontend area                 | none (UI in `W-94.2`)                                                                                                                                    | 1     |

---

## 2. Problem & Context

Under `W-84.1`, subscriptions record plan tier (`STARTER`, `GROWTH`, `ENTERPRISE`), support plan, and employee limits (`core.subscription`). However, Infinevo currently has no automated engine to:

1. Apply standardized per-employee pricing tiers and annual billing discounts.
2. Generate monthly/annual GST-compliant tax invoices for customer tenants.
3. Record offline and online payment transactions against issued invoices.

---

## 3. Pricing Matrix

### Plan Tiers & PEPM Rates

All plans are priced on a Per-Employee-Per-Month (PEPM) model with minimum monthly commitments:

| Plan Tier    | PEPM Rate (Monthly) | Minimum Headcount | Minimum Base / Month | Max Employee Limit | Included Features                                        |
| ------------ | ------------------- | ----------------- | -------------------- | ------------------ | -------------------------------------------------------- |
| `STARTER`    | ₹49 / user          | 10 users          | ₹490                 | 25                 | Core HR, Leave Management, Attendance                    |
| `GROWTH`     | ₹79 / user          | 25 users          | ₹1,975               | 100                | Starter + Full Payroll, Claims, Statutory (PF, ESI, TDS) |
| `ENTERPRISE` | ₹119 / user         | 100 users         | ₹11,900              | Unlimited          | Growth + Custom Workflows, Priority APIs, SLA            |

### Support Plan Add-ons

- `STANDARD`: ₹0 / month (Community & standard ticketing, 48h SLA).
- `PRIORITY`: ₹4,999 / month flat (Dedicated account manager, phone support, 4h SLA).

### Billing Frequency & Annual Discounts

- `MONTHLY`: Billed on the 1st of each calendar month based on active headcount.
- `ANNUAL`: Pre-billed annually with a **15% discount** applied to the plan base price:
  $$\text{Annual Net Plan Amount} = (\text{Active Employees} \times \text{PEPM Rate} \times 12) \times 0.85$$

---

## 4. Scope

**In scope:**

- PostgreSQL tables `core.billing_invoice`, `core.billing_invoice_item`, and `core.billing_payment` with tenant RLS.
- Invoice sequential numbering per financial year (e.g., `INF/2026-27/0001`).
- GST calculation engine:
  - Intrastate supply (Tenant GST state == Infinevo State): CGST (9%) + SGST (9%) = 18%.
  - Interstate supply (Tenant GST state != Infinevo State): IGST (18%).
  - SaaS HSN / SAC code: SAC `997331`.
- Payment recording engine:
  - Records payments against invoices with UTR / Transaction Reference.
  - Partial payment support: status stays `PARTIALLY_PAID` until cumulative payments $\ge$ `total_amount`.
  - Automatic transition of invoice to `PAID` when fully settled.
- REST API for platform admin to generate invoices, download PDFs, and record payments.

**Out of scope:**

- Payment gateway automated webhooks (handled in future integration).
- Customer frontend self-service screens (handled in `W-94.2`).

---

## 5. Database Changes

| Migration                                      | Change                                                                                          | Tenant-aware? | Reversible? |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------- | ------------- | ----------- |
| `core/V198__billing_invoices_and_payments.sql` | Create `core.billing_invoice`, `core.billing_invoice_item`, and `core.billing_payment` with RLS | yes           | yes         |

### Table: `core.billing_invoice`

```sql
CREATE TABLE core.billing_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    invoice_number VARCHAR(50) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    plan_tier VARCHAR(30) NOT NULL,
    billing_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' CHECK (billing_frequency IN ('MONTHLY', 'ANNUAL')),
    active_headcount INTEGER NOT NULL CHECK (active_headcount >= 0),
    subtotal NUMERIC(14,2) NOT NULL CHECK (subtotal >= 0),
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (discount_amount >= 0),
    taxable_amount NUMERIC(14,2) NOT NULL CHECK (taxable_amount >= 0),
    cgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    sgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    igst_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_tax NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount >= 0),
    amount_paid NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (amount_paid >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')),
    customer_gstin VARCHAR(15) NULL,
    sac_code VARCHAR(10) NOT NULL DEFAULT '997331',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_billing_invoice_number ON core.billing_invoice (invoice_number);
CREATE INDEX idx_billing_invoice_tenant_status ON core.billing_invoice (tenant_id, status);
```

### Table: `core.billing_invoice_item`

```sql
CREATE TABLE core.billing_invoice_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES core.billing_invoice(id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0)
);

CREATE INDEX idx_billing_invoice_item_invoice ON core.billing_invoice_item (invoice_id);
```

### Table: `core.billing_payment`

```sql
CREATE TABLE core.billing_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    invoice_id UUID NOT NULL REFERENCES core.billing_invoice(id),
    payment_date DATE NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('NEFT', 'RTGS', 'UPI', 'CREDIT_CARD', 'CHEQUE', 'NET_BANKING')),
    transaction_reference VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'FAILED', 'REFUNDED')),
    notes TEXT NULL,
    recorded_by VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_billing_payment_reference ON core.billing_payment (tenant_id, transaction_reference);
CREATE INDEX idx_billing_payment_invoice ON core.billing_payment (invoice_id);
```

---

## 6. Backend Changes

| Layer      | File                                                                        | Change                                                                                                  |
| ---------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Entity     | `core/billing/BillingInvoice.java`                                          | JPA entity mapping `core.billing_invoice`                                                               |
| Entity     | `core/billing/BillingInvoiceItem.java`                                      | JPA entity mapping `core.billing_invoice_item`                                                          |
| Entity     | `core/billing/BillingPayment.java`                                          | JPA entity mapping `core.billing_payment`                                                               |
| Repository | `core/billing/BillingInvoiceRepository.java`                                | Spring Data JPA repo for invoices                                                                       |
| Repository | `core/billing/BillingPaymentRepository.java`                                | Spring Data JPA repo for payments                                                                       |
| Service    | `core/billing/BillingInvoiceService.java`, `BillingInvoiceServiceImpl.java` | Implements pricing calculation, 15% annual discount, GST determination, and sequence numbering          |
| Service    | `core/billing/BillingPaymentService.java`, `BillingPaymentServiceImpl.java` | Records payments, updates invoice balance & status to `PAID`                                            |
| Controller | `core/billing/BillingInvoiceController.java`                                | Endpoints guarded by `@RequiresAction("core.tenant.view")` and `@RequiresAction("core.billing.manage")` |

### API Contract

| Method | Path                                          | Request Body             | Response Body                    | Auth                  |
| ------ | --------------------------------------------- | ------------------------ | -------------------------------- | --------------------- |
| `POST` | `/api/v1/core/billing/invoices/generate`      | `GenerateInvoiceRequest` | `BillingInvoiceResponse` (`201`) | `core.billing.manage` |
| `GET`  | `/api/v1/core/billing/invoices/{id}`          | —                        | `BillingInvoiceResponse`         | `core.tenant.view`    |
| `GET`  | `/api/v1/core/billing/invoices`               | —                        | `List<BillingInvoiceResponse>`   | `core.tenant.view`    |
| `POST` | `/api/v1/core/billing/invoices/{id}/payments` | `RecordPaymentRequest`   | `BillingPaymentResponse` (`201`) | `core.billing.manage` |
| `GET`  | `/api/v1/core/billing/invoices/{id}/payments` | —                        | `List<BillingPaymentResponse>`   | `core.tenant.view`    |

---

## 7. Verification

```bash
mvnw clean test -pl migration,core -am -Dtest="*BillingInvoice*,*BillingPayment*"
```

- Integration test `BillingInvoiceIT`: verifies pricing matrix math for Starter vs Growth vs Enterprise, 15% annual discount, and GST intrastate vs interstate tax split.
- Integration test `BillingPaymentIT`: tests partial payment -> full payment transition from `ISSUED` to `PAID`, ensuring duplicate UTR reference is rejected.
