package com.infinevo.payroll.salary;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for employee statutory profile lifecycle and persistence (W-26.2).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class StatutoryProfileIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeStatutoryProfileService statutoryProfileService;

    @Autowired
    private EmployeeStatutoryProfileRepository statutoryProfileRepository;

    private UUID employeeId;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-STAT-01", "Bob", "Builder");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Statutory profile returns default empty profile before first save")
    void returnsDefaultProfileBeforeFirstSave() {
        TenantContext.set(TENANT_A);

        StatutoryProfileResponse profile = statutoryProfileService.get(employeeId);
        assertThat(profile).isNotNull();
        assertThat(profile.eligibleForPf()).isFalse();
        assertThat(profile.eligibleForPt()).isFalse();
        assertThat(profile.eligibleForLwf()).isFalse();
        assertThat(profile.eligibleForEsi()).isFalse();
        assertThat(profile.eligibleForEps()).isFalse();
        assertThat(profile.contributesEpsOnHigherWages()).isFalse();
        assertThat(profile.director()).isFalse();
        assertThat(profile.pfAccountNumber()).isNull();
        assertThat(profile.uan()).isNull();
        assertThat(profile.esiNumber()).isNull();
    }

    @Test
    @DisplayName("Upsert creates profile on first save and updates on subsequent save without duplicating")
    void upsertCreatesAndUpdatesProfile() {
        TenantContext.set(TENANT_A);

        // 1. Initial upsert
        StatutoryProfileRequest req1 = new StatutoryProfileRequest(
                true, true, false, false, true, false, false, "MH/BAN/0012345/000/0000123", "100904838291", null);

        StatutoryProfileResponse created = statutoryProfileService.upsert(employeeId, req1);
        assertThat(created.id()).isNotNull();
        assertThat(created.eligibleForPf()).isTrue();
        assertThat(created.eligibleForPt()).isTrue();
        assertThat(created.eligibleForLwf()).isFalse();
        assertThat(created.eligibleForEsi()).isFalse();
        assertThat(created.eligibleForEps()).isTrue();
        assertThat(created.pfAccountNumber()).isEqualTo("MH/BAN/0012345/000/0000123");
        assertThat(created.uan()).isEqualTo("100904838291");
        assertThat(created.esiNumber()).isNull();

        // 2. Subsequent upsert (updates director and adds ESI)
        StatutoryProfileRequest req2 = new StatutoryProfileRequest(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                "MH/BAN/0012345/000/0000123",
                "100904838291",
                "31001234560001234");

        StatutoryProfileResponse updated = statutoryProfileService.upsert(employeeId, req2);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.eligibleForLwf()).isTrue();
        assertThat(updated.eligibleForEsi()).isTrue();
        assertThat(updated.director()).isTrue();
        assertThat(updated.esiNumber()).isEqualTo("31001234560001234");

        // 3. Confirm repository has exactly 1 row for employee
        long count = statutoryProfileRepository.count();
        assertThat(count).isEqualTo(1);
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, official_email) "
                                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, code.toLowerCase() + "@example.com");
            ps.executeUpdate();
        }
    }
}
