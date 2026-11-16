# Feature: Billing & GST Invoice Generation for Subscriptions

| Field | Value |
|---|---|
| **Feature ID** | `W-94.1` · `PLAT-BILLING` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md`; subscription billing is currently manual |
| **Status** | Ready for founder review |
| **Blocked by** | `W-84.1` (Plan & employee limit, completed on main `27ec17ae`), founder confirmation on plan pricing tiers |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V195__billing_invoices.sql` | 1 |
| Externally testable behaviour | Generates GST-compliant tax invoices for tenant subscriptions based on plan, headcount, and billing frequency | 1 |
| Frontend area | none (UI in `W-94.2`) | 1 |

---

## 2. Problem & Context

Under `W-84.1`, subscriptions record plan tier (`STARTER`, `GROWTH`, `ENTERPRISE`), support plan, and employee limits (`core.subscription`). However, Infinevo currently has no automated engine to generate monthly/annual GST tax invoices for customer tenants. Invoices must currently be prepared offline.

---

## 3. Scope

**In scope:**
- PostgreSQL table `core.billing_invoice` and `core.billing_invoice_item` (with RLS for customer view, plus platform admin full access).
- Invoice sequential numbering per financial year (e.g., `INF/2026-27/0001`).
- GST calculation engine:
  - Intrastate supply (Tenant state == Infinevo legal entity state): CGST (9%) + SGST (9%) = 18%.
  - Interstate supply (Tenant state != Infinevo legal entity state): IGST (18%).
  - HSN / SAC code for SaaS software services: SAC `997331`.
- Invoice generation trigger based on billing cycle (Monthly, Annual).
- REST API for platform admin to generate, preview, and download invoice PDF.

**Out of scope:**
- Payment gateway integration / Razorpay / Stripe (handled in `W-94.2`).
- Customer frontend self-service payment screens (handled in `W-94.2`).

---

## 4. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V195__billing_invoices.sql` | Create `core.billing_invoice` and `core.billing_invoice_item` with RLS | yes | yes |

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
    subtotal NUMERIC(14,2) NOT NULL CHECK (subtotal >= 0),
    cgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    sgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    igst_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_tax NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'CANCELLED')),
    customer_gstin VARCHAR(15) NULL,
    sac_code VARCHAR(10) NOT NULL DEFAULT '997331',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_billing_invoice_number
    ON core.billing_invoice (invoice_number);

CREATE INDEX idx_billing_invoice_tenant_status
    ON core.billing_invoice (tenant_id, status);
```

---

## 5. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `core/billing/BillingInvoice.java` | JPA entity mapping `core.billing_invoice` |
| Repository | `core/billing/BillingInvoiceRepository.java` | Spring Data JPA repo with tenant and platform queries |
| Service | `core/billing/BillingInvoiceService.java`, `BillingInvoiceServiceImpl.java` | Handles GST state determination, sequence numbering, and PDF rendering |
| Controller | `core/billing/BillingInvoiceController.java` | Endpoints guarded by `@RequiresAction("core.billing.manage")` |

---

## 6. Open Questions for Founder Review

1. **Pricing Matrix:** What are the per-employee-per-month (PEPM) base prices for `STARTER`, `GROWTH`, and `ENTERPRISE` tiers?
2. **Infinevo GSTIN & Place of Supply:** What is the legal registered state of Infinevo Cloud Private Limited (determines CGST+SGST vs IGST)?

---

## 7. Verification

```bash
mvnw clean test -pl migration,core -am -Dtest="*BillingInvoice*"
```
- Integration test `BillingInvoiceIT`: verifies sequential invoice numbering, interstate vs intrastate GST math, and tenant isolation.
