-- Migration: V059__payrun_job_progress.sql
-- Description: W-29.4 the pay run computes on the worker. The run carries the job that computes it,
-- the attempt number and the progress the payroll screen shows; each employee row carries the
-- attempt that last computed it, so a run abandoned mid-way resumes instead of starting again.
-- Expand only: columns with defaults, nothing dropped.

ALTER TABLE payroll.payrun
    -- Soft reference to core.job_status.job_id (VARCHAR(64), V006): no FK across schemas (W-52).
    ADD COLUMN job_id VARCHAR(64),
    -- Incremented by every POST /compute; the job id ends with it.
    ADD COLUMN compute_attempt INT NOT NULL DEFAULT 0,
    ADD COLUMN compute_started_at TIMESTAMPTZ,
    -- Employees computed so far, out of the included count, for the progress bar.
    ADD COLUMN progress_done INT NOT NULL DEFAULT 0,
    ADD COLUMN progress_total INT NOT NULL DEFAULT 0;

ALTER TABLE payroll.employee_payrun
    -- The attempt that last computed this row. A resumed attempt skips rows already at its number.
    ADD COLUMN computed_attempt INT NOT NULL DEFAULT 0;

-- The worker's job-to-run lookup (DEBT-018).
CREATE INDEX idx_payrun_tenant_job ON payroll.payrun (tenant_id, job_id);
