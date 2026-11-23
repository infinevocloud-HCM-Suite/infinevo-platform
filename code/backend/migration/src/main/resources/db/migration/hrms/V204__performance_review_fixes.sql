-- Migration: V195__performance_review_fixes.sql
-- Description: Fix rating scale to 5 and allow null manager_id for employees without reporting lines (W-106.1)

UPDATE hrms.performance_cycle SET rating_scale_max = 5;

ALTER TABLE hrms.performance_cycle DROP CONSTRAINT IF EXISTS chk_perf_cycle_scale;
ALTER TABLE hrms.performance_cycle ADD CONSTRAINT chk_perf_cycle_scale CHECK (rating_scale_max = 5);

ALTER TABLE hrms.performance_review ALTER COLUMN manager_id DROP NOT NULL;

UPDATE hrms.performance_review SET manager_id = NULL WHERE manager_id = employee_id;

