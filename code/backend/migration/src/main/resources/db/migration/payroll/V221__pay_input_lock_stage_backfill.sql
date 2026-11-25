-- Migration: V221__pay_input_lock_stage_backfill.sql
-- Description: D-144 - applies the founder's rule (2026-11-23: a month is CLOSED once its regular
-- run is approved, and stays closed when paid) to the months that are already there. V220 gave every
-- existing period lock the stage LOCKED; without this script every approved or paid month would
-- start taking entries again, which its run would never pay. It lives in payroll because it reads
-- payroll.payrun, and core does not reference a module schema (V060). Runs after core/V220.
--
-- Also clears D-138's leftovers: a period lock whose month has no live regular run - every regular
-- run for it was cancelled - was never undone, so the month kept moving entries to the next one.
-- Such a lock is removed. A month with a live run keeps its lock as it is.
--
-- Run lock rows (run_ref set) are not touched. Runs as the migration owner, so RLS does not filter.

UPDATE core.pay_input_period_lock l
SET stage = 'CLOSED',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'migration:V221'
WHERE l.run_ref IS NULL
  AND l.stage = 'LOCKED'
  AND EXISTS (
      SELECT 1 FROM payroll.payrun r
      WHERE r.tenant_id = l.tenant_id
        AND r.period = l.period
        AND r.run_type = 'REGULAR'
        AND r.status IN ('APPROVED','PAID'));

DELETE FROM core.pay_input_period_lock l
WHERE l.run_ref IS NULL
  AND l.stage = 'LOCKED'
  AND EXISTS (
      SELECT 1 FROM payroll.payrun r
      WHERE r.tenant_id = l.tenant_id AND r.period = l.period AND r.run_type = 'REGULAR')
  AND NOT EXISTS (
      SELECT 1 FROM payroll.payrun r
      WHERE r.tenant_id = l.tenant_id
        AND r.period = l.period
        AND r.run_type = 'REGULAR'
        AND r.status <> 'CANCELLED');
