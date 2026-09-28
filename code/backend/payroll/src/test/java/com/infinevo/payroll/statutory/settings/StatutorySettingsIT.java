package com.infinevo.payroll.statutory.settings;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
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
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Acceptance integration tests for statutory settings (W-31.1, spec section 7).
 *
 * <p>Verifies:
 * <ul>
 *   <li>GET with no row returns defaults and source=DEFAULT and writes nothing.
 *   <li>PUT creates the setting row.
 *   <li>Second PUT updates the same row (count stays 1).
 *   <li>GET returns the numbers as numbers (BigDecimal with scale 4).
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class StatutorySettingsIT extends AbstractIntegrationTest {

    @Autowired
    private StatutorySettingsService settingsService;

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
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName(
            "EPF lifecycle: GET returns defaults without writing, PUT creates, second PUT updates, numbers remain numeric")
    void epfLifecycle() throws Exception {
        TenantContext.set(TENANT_A);

        // 1. GET with no row returns defaults and source=DEFAULT
        EpfSettingResponse initial = settingsService.epf(TENANT_A);
        assertThat(initial.source()).isEqualTo(SettingSource.DEFAULT);
        assertThat(initial.isEnabled()).isFalse();
        assertThat(initial.id()).isNull();
        assertThat(initial.employeeRate()).isEqualByComparingTo(new BigDecimal("12.0000"));

        // Crucial acceptance rule: GET wrote nothing to the database!
        assertThat(rawRowCount("epf_setting")).isEqualTo(0);

        // 2. PUT creates row
        EpfSettingRequest createReq = new EpfSettingRequest(
                true,
                "EPF-REG-12345",
                LocalDate.now().minusYears(1),
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                true,
                true,
                false,
                true,
                58,
                true,
                true,
                false,
                false,
                false);

        EpfSettingResponse created = settingsService.saveEpf(createReq);
        assertThat(created.source()).isEqualTo(SettingSource.PERSISTED);
        assertThat(created.id()).isNotNull();
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.registrationNumber()).isEqualTo("EPF-REG-12345");
        assertThat(created.employeeRate()).isEqualByComparingTo(new BigDecimal("12.0000"));

        // Table now has exactly 1 row
        assertThat(rawRowCount("epf_setting")).isEqualTo(1);

        // 3. Second PUT updates the same row (row count remains 1)
        EpfSettingRequest updateReq = new EpfSettingRequest(
                true,
                "EPF-REG-UPDATED",
                LocalDate.now().minusYears(1),
                DeductionCycle.MONTHLY,
                new BigDecimal("10.0000"), // updated employee rate
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("18000.0000"), // updated wage ceiling
                true,
                true,
                false,
                true,
                60, // updated senior age
                true,
                true,
                false,
                false,
                false);

        EpfSettingResponse updated = settingsService.saveEpf(updateReq);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.registrationNumber()).isEqualTo("EPF-REG-UPDATED");
        assertThat(updated.employeeRate()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(updated.wageCeiling()).isEqualByComparingTo(new BigDecimal("18000.0000"));
        assertThat(updated.epsSeniorAge()).isEqualTo(60);

        // Row count stays 1
        assertThat(rawRowCount("epf_setting")).isEqualTo(1);

        // 4. GET returns the updated numbers as numbers
        EpfSettingResponse fetched = settingsService.epf(TENANT_A);
        assertThat(fetched.source()).isEqualTo(SettingSource.PERSISTED);
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.employeeRate()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(fetched.wageCeiling()).isEqualByComparingTo(new BigDecimal("18000.0000"));
    }

    @Test
    @DisplayName(
            "ESI lifecycle: GET returns defaults without writing, PUT creates, second PUT updates, numbers remain numeric")
    void esiLifecycle() throws Exception {
        TenantContext.set(TENANT_A);

        // 1. GET with no row returns defaults and source=DEFAULT
        EsiSettingResponse initial = settingsService.esi(TENANT_A);
        assertThat(initial.source()).isEqualTo(SettingSource.DEFAULT);
        assertThat(initial.isEnabled()).isFalse();
        assertThat(initial.id()).isNull();
        assertThat(initial.employeeRate()).isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(initial.wageCeiling()).isEqualByComparingTo(new BigDecimal("21000.0000"));

        // Crucial acceptance rule: GET wrote nothing to the database!
        assertThat(rawRowCount("esi_setting")).isEqualTo(0);

        // 2. PUT creates row
        EsiSettingRequest createReq = new EsiSettingRequest(
                true,
                "ESI-REG-98765",
                LocalDate.now().minusMonths(6),
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"),
                true,
                false);

        EsiSettingResponse created = settingsService.saveEsi(createReq);
        assertThat(created.source()).isEqualTo(SettingSource.PERSISTED);
        assertThat(created.id()).isNotNull();
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.registrationNumber()).isEqualTo("ESI-REG-98765");
        assertThat(created.employeeRate()).isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(created.employerRate()).isEqualByComparingTo(new BigDecimal("3.2500"));

        // Table now has exactly 1 row
        assertThat(rawRowCount("esi_setting")).isEqualTo(1);

        // 3. Second PUT updates the same row (row count remains 1)
        EsiSettingRequest updateReq = new EsiSettingRequest(
                false, // disable
                "ESI-REG-98765",
                LocalDate.now().minusMonths(6),
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("25000.0000"), // updated ceiling
                false,
                false);

        EsiSettingResponse updated = settingsService.saveEsi(updateReq);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.wageCeiling()).isEqualByComparingTo(new BigDecimal("25000.0000"));

        // Row count stays 1
        assertThat(rawRowCount("esi_setting")).isEqualTo(1);

        // 4. GET returns the updated values
        EsiSettingResponse fetched = settingsService.esi(TENANT_A);
        assertThat(fetched.source()).isEqualTo(SettingSource.PERSISTED);
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.isEnabled()).isFalse();
        assertThat(fetched.wageCeiling()).isEqualByComparingTo(new BigDecimal("25000.0000"));
    }

    private static int rawRowCount(String table) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll." + table);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
