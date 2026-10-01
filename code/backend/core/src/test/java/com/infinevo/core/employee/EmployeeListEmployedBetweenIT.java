package com.infinevo.core.employee;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * W-29.1 §7 — {@code listEmployedBetween} returns exactly the employees a pay run considers, in one
 * statement ({@code QueryCountIT}'s style, per W-55), and never a soft-deleted row.
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
@TestPropertySource(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.infinevo.core.employee.EmployeeListEmployedBetweenIT$QueryCounter")
class EmployeeListEmployedBetweenIT extends AbstractIntegrationTest {

    public static class QueryCounter implements StatementInspector {
        public static final AtomicInteger COUNT = new AtomicInteger(0);

        @Override
        public String inspect(String sql) {
            if (sql != null && sql.trim().regionMatches(true, 0, "select", 0, 6)) {
                COUNT.incrementAndGet();
            }
            return sql;
        }
    }

    private static final LocalDate START = LocalDate.of(2026, 4, 1);
    private static final LocalDate END = LocalDate.of(2026, 4, 30);

    @Autowired
    private EmployeeService employeeService;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Returns the considered set for the period, ordered by employee number, in one SELECT")
    void returnsConsideredSetInOneStatement() throws SQLException {
        UUID active = insert(TENANT_A, "E-01", LocalDate.of(2026, 1, 1), "ACTIVE", null, false);
        UUID joinedOnLastDay = insert(TENANT_A, "E-02", END, "ACTIVE", null, false);
        UUID leftOnFirstDay = insert(TENANT_A, "E-03", LocalDate.of(2025, 6, 1), "TERMINATED", START, false);
        UUID leftMidMonth = insert(TENANT_A, "E-04", LocalDate.of(2025, 6, 1), "TERMINATED", END.minusDays(10), false);
        insert(TENANT_A, "E-05", END.plusDays(1), "ACTIVE", null, false); // joins next month
        insert(TENANT_A, "E-06", LocalDate.of(2025, 6, 1), "TERMINATED", START.minusDays(1), false); // left before
        insert(TENANT_A, "E-07", LocalDate.of(2025, 6, 1), "SUSPENDED", null, false);
        insert(TENANT_A, "E-08", LocalDate.of(2025, 6, 1), "ACTIVE", null, true); // soft-deleted
        insert(TENANT_B, "E-09", LocalDate.of(2025, 6, 1), "ACTIVE", null, false); // another tenant

        TenantContext.set(TENANT_A);
        QueryCounter.COUNT.set(0);
        List<EmployeeResponse> result = employeeService.listEmployedBetween(START, END);

        assertThat(result)
                .extracting(EmployeeResponse::id)
                .containsExactly(active, joinedOnLastDay, leftOnFirstDay, leftMidMonth);
        assertThat(QueryCounter.COUNT.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("A tenant with nobody employed in the period gets an empty list, not an error")
    void emptyWhenNobodyEmployed() throws SQLException {
        insert(TENANT_A, "E-10", END.plusDays(1), "ACTIVE", null, false);

        TenantContext.set(TENANT_A);
        assertThat(employeeService.listEmployedBetween(START, END)).isEmpty();
    }

    private static UUID insert(
            UUID tenantId, String number, LocalDate joined, String status, LocalDate terminated, boolean deleted)
            throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, date_of_joining, status, termination_date,
                             is_deleted, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            ps.setString(3, "Name " + number);
            ps.setObject(4, joined);
            ps.setString(5, status);
            ps.setObject(6, terminated);
            ps.setBoolean(7, deleted);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }
}
