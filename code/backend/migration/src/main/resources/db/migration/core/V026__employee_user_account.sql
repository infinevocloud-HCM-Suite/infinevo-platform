-- Migration: V026__employee_user_account.sql
-- Description: W-13.4 employee <-> login link column and tenant unique index

ALTER TABLE core.employee
    ADD COLUMN user_account_id UUID NULL REFERENCES core.user_account(id);

CREATE UNIQUE INDEX uk_employee_tenant_user_account
    ON core.employee (tenant_id, user_account_id) WHERE user_account_id IS NOT NULL;
