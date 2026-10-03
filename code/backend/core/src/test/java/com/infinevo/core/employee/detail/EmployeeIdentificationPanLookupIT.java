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
import java.util.HashSet;
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
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Integration test for batch PAN lookup (W-36.5 §4, §7).
 * Verifies single-query execution, tenant binding, soft-delete exclusion, and duplicate PAN exclusion.
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
@WithMockUser(authorities = "core.employee.read")
class EmployeeIdentificationPanLookupIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeIdentificationService identificationService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private PermissionService permissionService;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeDetailTestSchema.apply();
    }

    @AfterAll
    static void cleanSchema() throws SQLException {
        EmployeeDetailTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        EmployeeTestSchema.seedTenants();
        EmployeeDetailTestSchema.clearAll();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UUID seedEmployee(UUID tenantId, String empNo, boolean deleted) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, date_of_joining, status, is_deleted, created_by, updated_by)
                        VALUES (?, ?, 'Test', DATE '2020-01-01', 'ACTIVE', ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, empNo);
            ps.setBoolean(3, deleted);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private void seedIdentification(UUID tenantId, UUID employeeId, String pan) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee_identification
                            (id, tenant_id, employee_id, pan_number, created_by, updated_by)
                        VALUES (gen_random_uuid(), ?, ?, ?, 'test', 'test')
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setString(3, pan);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Lookup resolves active PANs, ignores soft-deleted employees, and ignores duplicate PANs")
    void resolvesActivePansCorrectly() throws SQLException {
        TenantContext.set(TENANT_A);

        // 1. Live employee with valid PAN
        UUID emp1 = seedEmployee(TENANT_A, "EMP001", false);
        seedIdentification(TENANT_A, emp1, "ABCDE1234F");

        // 2. Soft-deleted employee with PAN -> must be excluded
        UUID emp2 = seedEmployee(TENANT_A, "EMP002", true);
        seedIdentification(TENANT_A, emp2, "DELTE1234F");

        // 3. Duplicate PAN on two live employees -> must be excluded as ambiguous
        UUID emp3 = seedEmployee(TENANT_A, "EMP003", false);
        UUID emp4 = seedEmployee(TENANT_A, "EMP004", false);
        seedIdentification(TENANT_A, emp3, "DUPES1234A");
        seedIdentification(TENANT_A, emp4, "DUPES1234A");

        // 4. Employee in Tenant B -> must not be visible to Tenant A
        UUID empB = seedEmployee(TENANT_B, "EMPB01", false);
        seedIdentification(TENANT_B, empB, "TENNB1234Z");

        Set<String> searchPans = Set.of("ABCDE1234F", "DELTE1234F", "DUPES1234A", "TENNB1234Z", "UNKNOWN123");
        Map<String, UUID> result = identificationService.employeeIdsByPan(searchPans);

        assertThat(result).hasSize(1);
        assertThat(result.get("ABCDE1234F")).isEqualTo(emp1);
        assertThat(result).doesNotContainKey("DELTE1234F");
        assertThat(result).doesNotContainKey("DUPES1234A");
        assertThat(result).doesNotContainKey("TENNB1234Z");
        assertThat(result).doesNotContainKey("UNKNOWN123");
    }

    @Test
    @DisplayName("Lookup for 500 PANs executes in exactly one database query (DEBT-019)")
    void resolves500PansInSingleQuery() throws SQLException {
        TenantContext.set(TENANT_A);

        // Create 1 live employee and 499 non-existent PAN queries
        Set<String> pans = new HashSet<>();
        UUID emp = seedEmployee(TENANT_A, "PERF001", false);
        seedIdentification(TENANT_A, emp, "PERFA1000A");
        pans.add("PERFA1000A");

        for (int i = 1; i < 500; i++) {
            pans.add(String.format("DUMMY%04dA", i));
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Map<String, UUID> result = identificationService.employeeIdsByPan(pans);

        assertThat(result).hasSize(1);
        assertThat(result.get("PERFA1000A")).isEqualTo(emp);
        assertThat(stats.getQueryExecutionCount()).isEqualTo(1);
    }
}
