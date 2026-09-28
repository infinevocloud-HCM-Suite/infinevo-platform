package com.infinevo.payroll.statutory.settings;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Integration tests verifying PostgreSQL Row-Level Security on statutory settings tables (W-31.1, spec section 7).
 *
 * <p>Asserts as app_user that Tenant A reads Tenant B's settings as absent, not as B's row.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class StatutorySettingsRlsIT extends AbstractIntegrationTest {

    @Autowired
    private StatutorySettingsService settingsService;

    private UUID epfRowB;
    private UUID esiRowB;

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

        // Seed custom settings for Tenant B using migrationConnection
        epfRowB = seedEpf(TENANT_B, "EPF-B-CUSTOM", new BigDecimal("10.0000"), new BigDecimal("18000.0000"));
        esiRowB = seedEsi(TENANT_B, "ESI-B-CUSTOM", new BigDecimal("1.0000"), new BigDecimal("25000.0000"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Unbound app_user sees no rows in epf_setting and esi_setting")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            for (String table : new String[] {"epf_setting", "esi_setting"}) {
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Table %s must return 0 rows when unbound", table)
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A raw app_user connection cannot see Tenant B's settings rows")
    void tenantACannotSeeTenantBRowsRawSql() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            assertThat(countVisibleById(conn, "epf_setting", epfRowB)).isZero();
            assertThat(countVisibleById(conn, "esi_setting", esiRowB)).isZero();

            // Control: Tenant B sees its own rows
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(countVisibleById(conn, "epf_setting", epfRowB)).isEqualTo(1);
            assertThat(countVisibleById(conn, "esi_setting", esiRowB)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Tenant A reads settings as absent/defaults, never as Tenant B's custom row")
    void tenantASeesDefaultsThroughService() {
        TenantContext.set(TENANT_A);

        EpfSettingResponse epfA = settingsService.epf(TENANT_A);
        assertThat(epfA.source()).isEqualTo(SettingSource.DEFAULT);
        assertThat(epfA.registrationNumber()).isNull();
        assertThat(epfA.employeeRate()).isEqualByComparingTo(new BigDecimal("12.0000"));

        EsiSettingResponse esiA = settingsService.esi(TENANT_A);
        assertThat(esiA.source()).isEqualTo(SettingSource.DEFAULT);
        assertThat(esiA.registrationNumber()).isNull();
        assertThat(esiA.employeeRate()).isEqualByComparingTo(new BigDecimal("0.7500"));

        // Control: Tenant B reads its own custom settings
        TenantContext.set(TENANT_B);
        EpfSettingResponse epfB = settingsService.epf(TENANT_B);
        assertThat(epfB.source()).isEqualTo(SettingSource.PERSISTED);
        assertThat(epfB.id()).isEqualTo(epfRowB);
        assertThat(epfB.registrationNumber()).isEqualTo("EPF-B-CUSTOM");
        assertThat(epfB.employeeRate()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(epfB.wageCeiling()).isEqualByComparingTo(new BigDecimal("18000.0000"));

        EsiSettingResponse esiB = settingsService.esi(TENANT_B);
        assertThat(esiB.source()).isEqualTo(SettingSource.PERSISTED);
        assertThat(esiB.id()).isEqualTo(esiRowB);
        assertThat(esiB.registrationNumber()).isEqualTo("ESI-B-CUSTOM");
        assertThat(esiB.employeeRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(esiB.wageCeiling()).isEqualByComparingTo(new BigDecimal("25000.0000"));
    }

    private static UUID seedEpf(UUID tenantId, String regNumber, BigDecimal empRate, BigDecimal ceiling)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.epf_setting
                            (id, tenant_id, is_enabled, registration_number, deduction_cycle,
                             employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate,
                             wage_ceiling, eps_senior_age, consider_earned_wage)
                        VALUES (?, ?, true, ?, 'MONTHLY', ?, 12.0000, 8.3300, 0.5000, 0.5000, ?, 58, true)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, regNumber);
            ps.setBigDecimal(4, empRate);
            ps.setBigDecimal(5, ceiling);
            ps.executeUpdate();
            return id;
        }
    }

    private static UUID seedEsi(UUID tenantId, String regNumber, BigDecimal empRate, BigDecimal ceiling)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.esi_setting
                            (id, tenant_id, is_enabled, registration_number, deduction_cycle,
                             employee_rate, employer_rate, wage_ceiling)
                        VALUES (?, ?, true, ?, 'MONTHLY', ?, 3.2500, ?)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, regNumber);
            ps.setBigDecimal(4, empRate);
            ps.setBigDecimal(5, ceiling);
            ps.executeUpdate();
            return id;
        }
    }

    private static int countVisibleById(Connection conn, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
