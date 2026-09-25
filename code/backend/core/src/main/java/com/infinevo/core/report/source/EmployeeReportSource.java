package com.infinevo.core.report.source;

import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.report.ReportColumn;
import com.infinevo.core.report.ReportDefinitionService;
import com.infinevo.core.report.ReportFilters;
import com.infinevo.core.report.ReportSource;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Employees, one row each (W-23.1) — the first of the three Core consumers of the one export path.
 *
 * <p><strong>Soft-deleted employees are excluded in the query</strong>, not filtered afterwards —
 * {@code EmployeeRepository}'s warning to {@code W-13.3} applies here too: a query that forgets
 * {@code is_deleted} returns the dead silently.
 *
 * <p>One query with left joins for the three org masters, streamed. Not
 * {@code EmployeeResponse.from} per row, which initialises up to three lazy proxies each — the N+1 the
 * W-14.1 review named.
 */
@Component
public class EmployeeReportSource implements ReportSource {

    public static final String CODE = "employee";

    static final String FILTER_STATUS = "status";

    static final List<ReportColumn> COLUMNS = List.of(
            ReportColumn.text("employee_number", "Employee number"),
            ReportColumn.text("first_name", "First name"),
            ReportColumn.text("middle_name", "Middle name"),
            ReportColumn.text("last_name", "Last name"),
            ReportColumn.text("gender", "Gender"),
            new ReportColumn("date_of_joining", "Date of joining", ReportColumn.Type.DATE),
            new ReportColumn("termination_date", "Termination date", ReportColumn.Type.DATE),
            ReportColumn.text("status", "Status"),
            ReportColumn.text("work_email", "Work email"),
            ReportColumn.text("mobile", "Mobile"),
            ReportColumn.text("department", "Department"),
            ReportColumn.text("designation", "Designation"),
            ReportColumn.text("work_location", "Work location"));

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<ReportColumn> columns() {
        return COLUMNS;
    }

    @Override
    public Set<String> filterNames() {
        return Set.of(FILTER_STATUS);
    }

    @Override
    public Stream<Map<String, Object>> rows(ReportFilters filters) {
        UUID tenantId = TenantContext.require();
        EmploymentStatus status =
                filters.get(FILTER_STATUS).map(EmployeeReportSource::status).orElse(null);

        String jpql = "select e.employeeNumber, e.firstName, e.middleName, e.lastName, e.gender,"
                + " e.dateOfJoining, e.terminationDate, e.status, e.workEmail, e.mobile,"
                + " d.name, g.name, w.name"
                + " from Employee e left join e.department d left join e.designation g left join e.workLocation w"
                + " where e.tenantId = :tenant and e.deleted = false"
                + (status == null ? "" : " and e.status = :status")
                + " order by e.employeeNumber";
        TypedQuery<Object[]> query = entityManager
                .createQuery(jpql, Object[].class)
                .setParameter("tenant", tenantId)
                .setHint(Streaming.FETCH_SIZE, Streaming.FETCH_ROWS)
                .setHint(Streaming.READ_ONLY, true);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getResultStream().map(EmployeeReportSource::row);
    }

    private static Map<String, Object> row(Object[] r) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            row.put(COLUMNS.get(i).name(), r[i]);
        }
        return row;
    }

    private static EmploymentStatus status(String value) {
        try {
            return EmploymentStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ReportDefinitionService.ValidationException(
                    Map.of(FILTER_STATUS, "status must be ACTIVE, SUSPENDED or TERMINATED"));
        }
    }
}
