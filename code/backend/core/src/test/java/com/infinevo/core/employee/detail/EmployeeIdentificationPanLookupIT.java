package com.infinevo.core.employee.detail;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * W-36.5 — {@link EmployeeIdentificationService#employeeIdsByPan} against real Postgres, as
 * {@code app_user} (spec section 7).
 *
 * <p>The query count is read from Hibernate's own statistics, switched on for this context only, so
 * "one query for 500 PANs" is measured rather than inferred from the repository signature
 * (DEBT-019). The context is not shared with the other {@link CoreFeatureTestApp} tests because the
 * property differs, which is the price of measuring it.
 */
@SpringBootTest(
        classes = CoreFeatureTestApp.class,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class EmployeeIdentificationPanLookupIT extends AbstractIntegrationTest {

    private static final int EMPLOYEES = 500;

    @Autowired
    private EmployeeIdentificationService service;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private PermissionService permissionService;

    /** PAN to employee id, as seeded in tenant A. */
    private Map<String, UUID> seeded;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeDetailTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeDetailTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        EmployeeTestSchema.seedTenants();
        EmployeeDetailTestSchema.clearAll();
        seeded = seedWithPans(TENANT_A, "A-", EMPLOYEES);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("500 PANs resolve in one SQL statement, every one to the employee that holds it")
    void fiveHundredPansInOneQuery() {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        TenantContext.set(TENANT_A);
        statistics.clear();

        Map<String, UUID> found = service.employeeIdsByPan(seeded.keySet());

        assertThat(found).hasSize(EMPLOYEES).isEqualTo(seeded);
        assertThat(statistics.getPrepareStatementCount())
                .as("one IN query, never one per PAN (DEBT-019)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("A soft-deleted employee's PAN is not matched")
    void softDeletedEmployeesAreExcluded() throws SQLException {
        String pan = pan(7);
        softDelete(seeded.get(pan));
        TenantContext.set(TENANT_A);

        Map<String, UUID> found = service.employeeIdsByPan(Set.of(pan, pan(8)));

        assertThat(found).containsOnlyKeys(pan(8));
    }

    @Test
    @DisplayName("A PAN held by two live employees is left out, not guessed")
    void ambiguousPanIsLeftOut() throws SQLException {
        String shared = pan(1);
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.employee_identification SET pan_number = ? WHERE employee_id = ?")) {
            ps.setString(1, shared);
            ps.setObject(2, seeded.get(pan(2)));
            ps.executeUpdate();
        }
        TenantContext.set(TENANT_A);

        Map<String, UUID> found = service.employeeIdsByPan(Set.of(shared, pan(3)));

        assertThat(found).containsOnlyKeys(pan(3));
    }

    @Test
    @DisplayName("Lower-case input is upper-cased before matching; blanks are ignored")
    void inputIsNormalised() {
        TenantContext.set(TENANT_A);

        Map<String, UUID> found = service.employeeIdsByPan(Set.of(" " + pan(4).toLowerCase() + " ", " "));

        assertThat(found).containsExactly(Map.entry(pan(4), seeded.get(pan(4))));
        assertThat(service.employeeIdsByPan(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("A PAN held only in another tenant is not matched")
    void otherTenantIsInvisible() throws SQLException {
        Map<String, UUID> ofB = seedWithPans(TENANT_B, "B-", 1);
        String panOfB = "ZZZZZ0001Z";
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.employee_identification SET pan_number = ? WHERE employee_id = ?")) {
            ps.setString(1, panOfB);
            ps.setObject(2, ofB.values().iterator().next());
            ps.executeUpdate();
        }
        TenantContext.set(TENANT_A);

        assertThat(service.employeeIdsByPan(Set.of(panOfB))).isEmpty();
    }

    // ── helpers

    private static String pan(int n) {
        return String.format("ABCDE%04dF", n);
    }

    /** Seeds {@code count} employees, each with an identification row and a distinct PAN, in two statements. */
    private static Map<String, UUID> seedWithPans(UUID tenantId, String numberPrefix, int count) throws SQLException {
        Map<String, UUID> byPan = new HashMap<>();
        try (Connection conn = EmployeeTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.employee
                        (tenant_id, employee_number, first_name, date_of_joining, status, created_by, updated_by)
                    SELECT ?, ? || g, 'E' || g, DATE '2026-04-01', 'ACTIVE', 'test', 'test'
                    FROM generate_series(1, ?) g
                    """)) {
                ps.setObject(1, tenantId);
                ps.setString(2, numberPrefix);
                ps.setInt(3, count);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.employee_identification (tenant_id, employee_id, pan_number, created_by, updated_by)
                    SELECT tenant_id, id,
                           'ABCDE' || lpad(substring(employee_number FROM length(?) + 1), 4, '0') || 'F',
                           'test', 'test'
                    FROM core.employee
                    WHERE tenant_id = ? AND employee_number LIKE ? || '%'
                    RETURNING pan_number, employee_id
                    """)) {
                ps.setString(1, numberPrefix);
                ps.setObject(2, tenantId);
                ps.setString(3, numberPrefix);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byPan.put(rs.getString(1), rs.getObject(2, UUID.class));
                    }
                }
            }
        }
        return byPan;
    }

    private static void softDelete(UUID employeeId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE core.employee SET is_deleted = true WHERE id = ?")) {
            ps.setObject(1, employeeId);
            ps.executeUpdate();
        }
    }
}
