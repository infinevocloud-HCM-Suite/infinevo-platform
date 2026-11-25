-- Migration: V199__exit_process.sql
-- Description: W-103 Employee resignation, exit clearances & handover to Payroll.

CREATE TABLE hrms.exit_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    resignation_date DATE NOT NULL,
    requested_lwd DATE NOT NULL,
    approved_lwd DATE NULL,
    exit_type VARCHAR(30) NOT NULL DEFAULT 'RESIGNATION',
    reason_category VARCHAR(50) NOT NULL,
    reason_notes TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN (
        'SUBMITTED',
        'MANAGER_APPROVED',
        'CLEARANCE_IN_PROGRESS',
        'READY_FOR_FNF',
        'SETTLED',
        'REJECTED',
        'WITHDRAWN'
    )),
    approved_by VARCHAR(100) NULL,
    approved_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_exit_request_active_employee
    ON hrms.exit_request (tenant_id, employee_id)
    WHERE status NOT IN ('SETTLED', 'REJECTED', 'WITHDRAWN');

ALTER TABLE hrms.exit_request ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.exit_request
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

CREATE TABLE hrms.exit_clearance_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    exit_request_id UUID NOT NULL REFERENCES hrms.exit_request(id) ON DELETE CASCADE,
    department VARCHAR(50) NOT NULL CHECK (department IN ('IT', 'FINANCE', 'ADMIN', 'HR')),
    item_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CLEARED', 'WAIVED')),
    cleared_by VARCHAR(100) NULL,
    cleared_at TIMESTAMPTZ NULL,
    remarks VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_exit_clearance_tenant_request
    ON hrms.exit_clearance_item (tenant_id, exit_request_id);

ALTER TABLE hrms.exit_clearance_item ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.exit_clearance_item
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
