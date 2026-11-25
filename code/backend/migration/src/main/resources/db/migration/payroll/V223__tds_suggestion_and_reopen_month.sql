-- Migration: V223__tds_suggestion_and_reopen_month.sql
-- Description: D-151, two changes the founder approved on 2026-11-24.
--
-- (1) An officer's TDS figure sticks. When the active payroll.employee_tds row was set by an officer
--     (source = 'OFFICER'), an automatic recalculation no longer supersedes it: the computed figure is
--     kept on that row as a suggestion the officer applies or clears. Expand only - seven nullable
--     columns, all set together or all empty. Applying the suggestion supersedes the row as before.
--
-- (2) Cancelling an approved regular run reopens its month. V220 let the application remove only a
--     LOCKED period lock (delete_locked_period_lock_only); a CLOSED month stayed closed when its run was
--     cancelled. The policy is replaced so a period lock of either stage may be removed. Run locks
--     (run_ref set) are still never removed. The service removes a CLOSED lock only when it cancels the
--     month's APPROVED regular run; a PAID run cannot be cancelled (PayRunStatus), so a paid month is
--     never reopened.
--
-- (3) Data fix for (1). Before D-147 the calculator labelled a salary-default figure (no declaration) as
--     'OFFICER' (TdsFigures: source = declarationId != null ? DECLARATION : OFFICER). Left as they are,
--     those rows would now count as officer figures, and recalculation would only ever suggest for them.
--     A calculator row is told apart by its note, '<TRIGGER> <tax_computation id>'
--     (TaxRecalculationServiceImpl), so a row is relabelled only when that id is a real tax computation
--     of the same tenant and employee. A figure an officer typed is never touched.
--
-- Lives in payroll because it alters a payroll table; payroll may reference core (as V221 does).
-- V223 was free on every branch when this was written.

ALTER TABLE payroll.employee_tds
    ADD COLUMN suggested_regime VARCHAR(3) NULL CHECK (suggested_regime IN ('OLD','NEW')),
    ADD COLUMN suggested_declaration_id UUID NULL REFERENCES payroll.employee_investment_declaration(id),
    ADD COLUMN suggested_annual_gross NUMERIC(19,4) NULL CHECK (suggested_annual_gross >= 0),
    ADD COLUMN suggested_annual_taxable_income NUMERIC(19,4) NULL CHECK (suggested_annual_taxable_income >= 0),
    ADD COLUMN suggested_annual_tax NUMERIC(19,4) NULL CHECK (suggested_annual_tax >= 0),
    ADD COLUMN suggested_note VARCHAR(255) NULL,
    ADD COLUMN suggested_at TIMESTAMPTZ NULL;

ALTER TABLE payroll.employee_tds
    ADD CONSTRAINT ck_employee_tds_suggestion_complete CHECK (
        (suggested_at IS NULL) = (suggested_annual_tax IS NULL)
        AND (suggested_at IS NULL) = (suggested_annual_gross IS NULL)
        AND (suggested_at IS NULL) = (suggested_annual_taxable_income IS NULL)
        AND (suggested_at IS NULL) = (suggested_regime IS NULL));

COMMENT ON COLUMN payroll.employee_tds.suggested_annual_tax IS
    'D-151: the annual tax an automatic recalculation computed while an officer''s figure is active. '
    'Shown to the officer, never used by the pay run; null when there is no suggestion.';

DROP POLICY delete_locked_period_lock_only ON core.pay_input_period_lock;

CREATE POLICY delete_period_lock_only ON core.pay_input_period_lock
    AS RESTRICTIVE
    FOR DELETE
    USING (run_ref IS NULL);

UPDATE payroll.employee_tds t
   SET source = 'DECLARATION'
 WHERE t.source = 'OFFICER'
   AND t.note ~ '^[A-Z_]+ [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
   AND EXISTS (
       SELECT 1
         FROM payroll.tax_computation c
        WHERE c.id = split_part(t.note, ' ', 2)::uuid
          AND c.tenant_id = t.tenant_id
          AND c.employee_id = t.employee_id);
