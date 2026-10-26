-- Migration: V185__timesheet_task_repeatable.sql
-- Description: a task may appear on more than one row of a project line, as in legacy HRMS
-- (founder decision 2026-10-26: legacy task_entry repeats a task in a week - morning and afternoon
-- rows with their own descriptions - and those rows were being dropped). A date stays once per row
-- (uk_tde_tenant_entry_date); idx_tte_tenant_task still serves "is this task on a live timesheet?".

DROP INDEX hrms.uk_tte_tenant_entry_task;
