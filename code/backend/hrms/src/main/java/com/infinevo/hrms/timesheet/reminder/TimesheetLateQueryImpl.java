package com.infinevo.hrms.timesheet.reminder;

import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLineKind;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.hrms.timesheet.TimesheetStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TimesheetLateQuery} (W-43.2): one JPQL, tenant-bound in every table it touches, every
 * parameter bound. Indexes that carry it: {@code uk_timesheet_tenant_employee_week},
 * the assignment index on {@code (tenant_id, employee_id, is_deleted)}, {@code idx_project_tenant_deleted_status}
 * and {@code idx_reporting_line_lookup} (spec §6).
 */
@Repository
@Transactional(readOnly = true)
public class TimesheetLateQueryImpl implements TimesheetLateQuery {

    /** Statuses that mean the employee did hand the week in. */
    private static final Set<TimesheetStatus> HANDED_IN =
            Set.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED, TimesheetStatus.REJECTED);

    /**
     * The manager is looked up with the employee, in the same statement, so the reminder and the escalation read one
     * answer. A manager who is not active, or who has been deleted, is no manager: the join finds nothing and the
     * employee is left out of the escalation, as one with no reporting line is.
     */
    private static final String LATE =
            """
            SELECT e.id, e.firstName, e.lastName, mgr.id
            FROM Employee e
            LEFT JOIN ReportingLine r
                   ON r.tenantId = :tenantId
                  AND r.employee = e
                  AND r.kind = :primary
                  AND r.effectiveFrom <= :weekEnd
                  AND (r.effectiveTo IS NULL OR r.effectiveTo >= :weekEnd)
            LEFT JOIN Employee mgr
                   ON mgr = r.manager
                  AND mgr.tenantId = :tenantId
                  AND mgr.deleted = false
                  AND mgr.status = :active
            WHERE e.tenantId = :tenantId
              AND e.deleted = false
              AND e.status = :active
              AND EXISTS (
                    SELECT 1 FROM Assignment a, Project p
                    WHERE a.tenantId = :tenantId
                      AND a.employeeId = e.id
                      AND a.deleted = false
                      AND p.id = a.projectId
                      AND p.tenantId = :tenantId
                      AND p.deleted = false
                      AND p.status = :started
                      AND (p.startDate IS NULL OR p.startDate <= :weekEnd)
                      AND (p.endDate IS NULL OR p.endDate >= :weekStart))
              AND NOT EXISTS (
                    SELECT 1 FROM Timesheet t
                    WHERE t.tenantId = :tenantId
                      AND t.employeeId = e.id
                      AND t.weekStartDate = :weekStart
                      AND t.status IN :handedIn)
            ORDER BY e.firstName, e.lastName, e.id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<LateEmployee> lateEmployees(UUID tenantId, LocalDate weekStart) {
        List<?> rows = entityManager
                .createQuery(LATE)
                .setParameter("tenantId", tenantId)
                .setParameter("primary", ReportingLineKind.PRIMARY)
                .setParameter("active", EmploymentStatus.ACTIVE)
                .setParameter("started", ProjectStatus.STARTED)
                .setParameter("handedIn", HANDED_IN)
                .setParameter("weekStart", weekStart)
                .setParameter("weekEnd", weekStart.plusDays(6))
                .getResultList();

        // One person is one row. A second reporting line in force on the same day (which the data should not hold)
        // would repeat them, so the first manager found is kept and the repeat dropped.
        Map<UUID, LateEmployee> byEmployee = new LinkedHashMap<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            UUID id = (UUID) cols[0];
            UUID manager = (UUID) cols[3];
            LateEmployee existing = byEmployee.get(id);
            if (existing == null || (existing.primaryManagerId() == null && manager != null)) {
                byEmployee.put(id, new LateEmployee(id, displayName((String) cols[1], (String) cols[2]), manager));
            }
        }
        return new ArrayList<>(byEmployee.values());
    }

    private static String displayName(String first, String last) {
        String name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        return name.isEmpty() ? "Employee" : name;
    }
}
