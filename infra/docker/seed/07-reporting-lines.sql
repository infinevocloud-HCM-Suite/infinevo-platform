-- Seed: one primary reporting line in Globex, so leave approval has an approver.
--
-- Local development only. Not a Flyway migration, and never run against a deployed
-- environment — seed.sh says the same thing and is the only thing that runs this file.
--
-- Why this file exists: the LEAVE approval definition V089 seeds for every tenant starts
-- with a REPORTING_MANAGER step. CoreApproverResolver answers it from
-- ReportingLineService.chainAbove, which reads core.reporting_line rows of kind PRIMARY
-- that are effective on the day (effective_from <= today, effective_to NULL or >= today).
-- An employee with no such line has no approver, the step is unassignable, and an
-- end-to-end "manager approves the employee's leave" run cannot pass. Locally, this
-- gives employee.globex a manager:
--
--   GLBX-0001 employee.globex  reports to  GLBX-0003 manager.globex  (PRIMARY, from 2024-04-01)
--
-- The second LEAVE step is ROLE "hr"; it needs no row here, because 03-user-roles.sql
-- grants hr.globex the hr role and 06-employees.sql links that login to GLBX-0002.
--
-- Idempotent without ON CONFLICT: V028 gives core.reporting_line no unique constraint
-- (only lookup indexes and a no-self-manage CHECK), so the guard is NOT EXISTS on an open
-- PRIMARY line for the employee. That also means a manager a developer later set through
-- PUT /api/v1/employees/{id}/reporting-line is kept, not overwritten, on the next re-run.
-- Runs after 06-employees.sql, which creates both employee rows this joins to.

INSERT INTO core.reporting_line
    (tenant_id, employee_id, manager_id, kind, effective_from, created_by, updated_by)
SELECT e.tenant_id, e.id, m.id, 'PRIMARY', DATE '2024-04-01', 'seed', 'seed'
FROM core.employee e
JOIN core.employee m
  ON m.tenant_id = e.tenant_id AND m.employee_number = 'GLBX-0003'
WHERE e.tenant_id = '22222222-2222-2222-2222-222222222222'
  AND e.employee_number = 'GLBX-0001'
  AND NOT EXISTS (
        SELECT 1 FROM core.reporting_line r
        WHERE r.tenant_id = e.tenant_id
          AND r.employee_id = e.id
          AND r.kind = 'PRIMARY'
          AND r.effective_to IS NULL
  );

SELECT format('seed 07-reporting-lines: %s primary reporting line(s) present', count(*)) AS status
FROM core.reporting_line
WHERE kind = 'PRIMARY';
