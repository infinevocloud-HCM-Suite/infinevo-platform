-- Migration: V231__billing_invoices_and_payments.sql
-- Schema: core
-- Purpose: W-94.1 subscription billing — billing_invoice, billing_invoice_item, billing_payment with RLS,
--          atomic FY invoice numbering, and the platform-only core.billing.manage action.
--
-- Notes vs spec W-94.1:
--   1. Numbered V231 (next free version after V230; V198 was superseded).
--   2. core.billing_invoice_item includes tenant_id + RLS + tenant_isolation policy per ShippedMigrationsIT
--      and CONVENTIONS.md Rule 7.
--   3. core.billing.manage is registered as a platform-only action (V136/V158 pattern) so only
--      platform-admin in the platform tenant (00000000-0000-0000-0000-000000000001) can hold it.

-- ── 1. Tables ───────────────────────────────────────────────────────────────

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

ALTER TABLE core.billing_invoice ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.billing_invoice
    FOR ALL
    USING (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    )
    WITH CHECK (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.billing_invoice TO app_user;

CREATE TABLE core.billing_invoice_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    invoice_id UUID NOT NULL REFERENCES core.billing_invoice(id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0)
);

CREATE INDEX idx_billing_invoice_item_invoice ON core.billing_invoice_item (tenant_id, invoice_id);

ALTER TABLE core.billing_invoice_item ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.billing_invoice_item
    FOR ALL
    USING (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    )
    WITH CHECK (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.billing_invoice_item TO app_user;

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
CREATE INDEX idx_billing_payment_invoice ON core.billing_payment (tenant_id, invoice_id);

ALTER TABLE core.billing_payment ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.billing_payment
    FOR ALL
    USING (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    )
    WITH CHECK (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.billing_payment TO app_user;

-- ── 2. Cross-tenant SECURITY DEFINER helpers for platform billing ───────────

CREATE OR REPLACE FUNCTION core.next_billing_invoice_number(p_financial_year text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_next integer;
BEGIN
    IF p_financial_year IS NULL OR p_financial_year !~ '^[0-9]{4}-[0-9]{2}$' THEN
        RAISE EXCEPTION 'Invalid financial year format: %', p_financial_year;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext('core.billing_invoice:' || p_financial_year));

    SELECT COALESCE(MAX(split_part(invoice_number, '/', 3)::integer), 0) + 1
      INTO v_next
      FROM core.billing_invoice
     WHERE invoice_number ~ ('^INF/' || p_financial_year || '/[0-9]+$');

    RETURN 'INF/' || p_financial_year || '/' || lpad(v_next::text, 4, '0');
END;
$$;

REVOKE EXECUTE ON FUNCTION core.next_billing_invoice_number(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.next_billing_invoice_number(text) TO app_user;

CREATE OR REPLACE FUNCTION core.billing_invoice_tenant(p_invoice_id uuid)
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT tenant_id FROM core.billing_invoice WHERE id = p_invoice_id;
$$;

REVOKE EXECUTE ON FUNCTION core.billing_invoice_tenant(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.billing_invoice_tenant(uuid) TO app_user;

CREATE OR REPLACE FUNCTION core.list_billing_invoice_tenants()
RETURNS TABLE (tenant_id uuid)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT DISTINCT bi.tenant_id
      FROM core.billing_invoice bi
     ORDER BY bi.tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION core.list_billing_invoice_tenants() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_billing_invoice_tenants() TO app_user;

-- ── 3. Platform-only action core.billing.manage ─────────────────────────────

INSERT INTO reference.action (code, name, module, description)
VALUES (
    'core.billing.manage',
    'Manage tenant billing and payments',
    'core',
    'Generate GST subscription invoices and record subscription payments across customer tenants'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- Allow core.billing.manage in the platform tenant (extends V158's list).
CREATE OR REPLACE FUNCTION core.restrict_platform_tenant_grant()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF NEW.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
       AND NEW.action_code NOT LIKE 'core.tenant.%'
       AND NEW.action_code NOT IN ('core.audit.read', 'core.user.manage', 'core.billing.manage') THEN
        RAISE EXCEPTION 'roles in the platform tenant may hold only core.tenant.*, core.audit.read, core.user.manage and core.billing.manage, not %',
            NEW.action_code
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.restrict_platform_tenant_grant() FROM PUBLIC;

-- Refuse core.billing.manage on any role outside platform-admin in the platform tenant (V136 pattern).
CREATE OR REPLACE FUNCTION core.restrict_billing_manage_grant()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF NEW.action_code = 'core.billing.manage'
       AND NOT (
           NEW.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
           AND EXISTS (
               SELECT 1
               FROM core.role r
               WHERE r.id = NEW.role_id
                 AND r.tenant_id = NEW.tenant_id
                 AND r.code = 'platform-admin'
                 AND r.is_system
           )
       ) THEN
        RAISE EXCEPTION 'core.billing.manage may be held only by platform-admin in the platform tenant'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.restrict_billing_manage_grant() FROM PUBLIC;

DROP TRIGGER IF EXISTS role_action_restrict_billing_manage ON core.role_action;
CREATE TRIGGER role_action_restrict_billing_manage
    BEFORE INSERT OR UPDATE ON core.role_action
    FOR EACH ROW
    EXECUTE FUNCTION core.restrict_billing_manage_grant();

-- Redefine core.seed_system_roles from V158 so customer tenants never receive core.billing.manage.
CREATE OR REPLACE FUNCTION core.seed_system_roles(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role (tenant_id, code, name, is_system)
    VALUES
        (p_tenant_id, 'platform-admin', 'Platform administrator', true),
        (p_tenant_id, 'tenant-admin', 'Tenant administrator', true),
        (p_tenant_id, 'hr', 'HR', true),
        (p_tenant_id, 'manager', 'Manager', true),
        (p_tenant_id, 'payroll-officer', 'Payroll officer', true),
        (p_tenant_id, 'finance', 'Finance', true),
        (p_tenant_id, 'employee', 'Employee', true)
    ON CONFLICT (tenant_id, code) DO NOTHING;

    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (
        SELECT 'platform-admin'::VARCHAR AS role_code, a.code AS action_code
        FROM reference.action a
        WHERE a.code NOT IN ('core.tenant.provision', 'core.billing.manage')
        UNION ALL
        SELECT 'tenant-admin', a.code
        FROM reference.action a
        WHERE a.code NOT IN ('core.tenant.provision', 'core.billing.manage')
        UNION ALL
        SELECT v.role_code, v.action_code
        FROM (VALUES
            ('hr', 'core.tenant.read'),
            ('hr', 'core.user.read'),
            ('hr', 'core.role.read'),
            ('hr', 'core.employee.read'),
            ('hr', 'core.employee.create'),
            ('hr', 'core.employee.update'),
            ('hr', 'core.employee.delete'),
            ('hr', 'core.employee.export'),
            ('hr', 'core.employee_identification.read'),
            ('hr', 'core.employee_identification.update'),
            ('hr', 'core.employee_bank.read'),
            ('hr', 'core.employee_bank.update'),
            ('hr', 'core.org.read'),
            ('hr', 'core.org.manage'),
            ('hr', 'core.audit.read'),
            ('hr', 'core.leave.read'),
            ('hr', 'core.leave.approve'),
            ('hr', 'core.leave_type.manage'),
            ('hr', 'core.leave_balance.manage'),
            ('hr', 'core.attendance.read'),
            ('hr', 'core.attendance.manage'),
            ('hr', 'core.attendance.export'),
            ('hr', 'core.overtime.read'),
            ('hr', 'hrms.timesheet.read'),
            ('hr', 'hrms.timesheet.approve'),
            ('hr', 'hrms.timesheet.export'),
            ('hr', 'core.holiday.read'),
            ('hr', 'core.holiday.manage'),
            ('hr', 'hrms.project.read'),
            ('hr', 'hrms.project.manage'),
            ('hr', 'core.approval.decide'),

            ('manager', 'core.employee.read_team'),
            ('manager', 'core.org.read'),
            ('manager', 'core.leave.read_team'),
            ('manager', 'core.leave.approve'),
            ('manager', 'core.attendance.read_team'),
            ('manager', 'hrms.timesheet.read_team'),
            ('manager', 'hrms.timesheet.approve'),
            ('manager', 'core.holiday.read'),
            ('manager', 'hrms.project.read_team'),
            ('manager', 'hrms.project.manage'),
            ('manager', 'core.approval.decide'),

            ('payroll-officer', 'core.employee.read'),
            ('payroll-officer', 'core.employee_identification.read'),
            ('payroll-officer', 'core.employee_bank.read'),
            ('payroll-officer', 'core.org.read'),
            ('payroll-officer', 'core.attendance.read'),
            ('payroll-officer', 'core.overtime.read'),
            ('payroll-officer', 'core.leave.read'),
            ('payroll-officer', 'payroll.settings.manage'),
            ('payroll-officer', 'payroll.structure.read'),
            ('payroll-officer', 'payroll.structure.manage'),
            ('payroll-officer', 'payroll.salary.read'),
            ('payroll-officer', 'payroll.salary.manage'),
            ('payroll-officer', 'payroll.run.read'),
            ('payroll-officer', 'payroll.run.execute'),
            ('payroll-officer', 'payroll.payslip.read'),
            ('payroll-officer', 'payroll.payslip.publish'),
            ('payroll-officer', 'payroll.tax_declaration.read'),
            ('payroll-officer', 'payroll.tax_declaration.verify'),
            ('payroll-officer', 'payroll.statutory_report.read'),
            ('payroll-officer', 'payroll.statutory_report.generate'),
            ('payroll-officer', 'payroll.bank_file.export'),
            ('payroll-officer', 'payroll.report.export'),
            ('payroll-officer', 'payroll.fbp.read'),
            ('payroll-officer', 'payroll.reimbursement_claim.read'),
            ('payroll-officer', 'payroll.employee_deduction.read'),
            ('payroll-officer', 'payroll.employee_deduction.manage'),

            ('finance', 'core.org.read'),
            ('finance', 'payroll.structure.read'),
            ('finance', 'payroll.salary.read'),
            ('finance', 'payroll.run.read'),
            ('finance', 'payroll.run.approve'),
            ('finance', 'payroll.payslip.read'),
            ('finance', 'payroll.statutory_report.read'),
            ('finance', 'payroll.bank_file.export'),
            ('finance', 'payroll.report.export'),
            ('finance', 'payroll.reimbursement_claim.read'),
            ('finance', 'payroll.employee_deduction.read'),

            ('employee', 'core.employee.read_own'),
            ('employee', 'core.employee.update_own'),
            ('employee', 'core.org.read'),
            ('employee', 'core.leave.apply'),
            ('employee', 'core.leave.read_own'),
            ('employee', 'hrms.attendance.mark'),
            ('employee', 'core.attendance.read_own'),
            ('employee', 'hrms.timesheet.submit'),
            ('employee', 'hrms.timesheet.read_own'),
            ('employee', 'core.holiday.read'),
            ('employee', 'payroll.payslip.read_own'),
            ('employee', 'payroll.tax_declaration.submit'),
            ('employee', 'payroll.tax_declaration.read_own'),
            ('employee', 'payroll.fbp.read_own'),
            ('employee', 'payroll.fbp.declare_own'),
            ('employee', 'payroll.reimbursement_claim.read_own'),
            ('employee', 'payroll.reimbursement_claim.submit_own'),
            ('employee', 'payroll.employee_deduction.read_own'),
            ('employee', 'hrms.project.read_own'),
            ('employee', 'hrms.overtime.request')
        ) AS v (role_code, action_code)
    ) AS grants
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    WHERE p_tenant_id <> '00000000-0000-0000-0000-000000000001'::uuid
       OR grants.action_code LIKE 'core.tenant.%'
       OR grants.action_code IN ('core.audit.read', 'core.user.manage', 'core.billing.manage')
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_system_roles(UUID) FROM PUBLIC;

-- Grant core.billing.manage solely to platform-admin in the platform tenant (if already seeded).
INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, 'core.billing.manage'
FROM core.role r
WHERE r.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  AND r.code = 'platform-admin'
  AND r.is_system
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
