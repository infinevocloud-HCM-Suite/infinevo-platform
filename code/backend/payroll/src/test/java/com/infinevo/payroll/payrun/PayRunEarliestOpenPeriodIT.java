package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-147: {@link PayRunRepository#findEarliestOpenPeriod} against real PostgreSQL. A new TDS record starts at the
 * month this finds, so it must skip closed runs (approved, paid, cancelled), ignore off-cycle runs, and never
 * see another tenant's runs.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class PayRunEarliestOpenPeriodIT extends AbstractIntegrationTest {

    /** The set EmployeeTdsServiceImpl.CLOSED_RUN_STATUSES passes (checked by EmployeeTdsRulesTest.closedStatuses). */
    private static final EnumSet<PayRunStatus> CLOSED =
            EnumSet.of(PayRunStatus.APPROVED, PayRunStatus.PAID, PayRunStatus.CANCELLED);

    @Autowired
    private PayRunRepository payRunRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Approved, paid and cancelled regular runs and off-cycle runs are skipped; the first open month wins")
    void skipsClosedAndOffCycle() throws SQLException {
        run(TENANT_A, "2026-04", "REGULAR", "PAID");
        run(TENANT_A, "2026-05", "REGULAR", "APPROVED");
        run(TENANT_A, "2026-06", "REGULAR", "CANCELLED");
        run(TENANT_A, "2026-07", "OFF_CYCLE", "DRAFT");
        run(TENANT_A, "2026-08", "REGULAR", "COMPUTED");
        run(TENANT_A, "2026-09", "REGULAR", "DRAFT");

        assertThat(earliestOpen(TENANT_A, "2026-04", "2026-10")).contains("2026-08");
        // Only closed and off-cycle runs before August: nothing open.
        assertThat(earliestOpen(TENANT_A, "2026-04", "2026-07")).isEmpty();
    }

    @Test
    @DisplayName("Every status other than approved, paid and cancelled counts as open")
    void openStatuses() throws SQLException {
        run(TENANT_A, "2026-04", "REGULAR", "LOCKED");
        run(TENANT_A, "2026-05", "REGULAR", "FAILED");

        assertThat(earliestOpen(TENANT_A, "2026-04", "2026-10")).contains("2026-04");
        assertThat(earliestOpen(TENANT_A, "2026-05", "2026-10")).contains("2026-05");
    }

    @Test
    @DisplayName("Another tenant's open run is never found")
    void tenantScoped() throws SQLException {
        run(TENANT_B, "2026-04", "REGULAR", "DRAFT");
        run(TENANT_A, "2026-06", "REGULAR", "DRAFT");

        assertThat(earliestOpen(TENANT_A, "2026-04", "2026-10")).contains("2026-06");
        assertThat(earliestOpen(TENANT_B, "2026-04", "2026-10")).contains("2026-04");
    }

    private Optional<String> earliestOpen(UUID tenantId, String from, String to) {
        TenantContext.set(tenantId);
        try {
            return new TransactionTemplate(transactionManager)
                    .execute(status ->
                            payRunRepository.findEarliestOpenPeriod(tenantId, PayRunType.REGULAR, CLOSED, from, to));
        } finally {
            TenantContext.clear();
        }
    }

    private static void run(UUID tenantId, String period, String runType, String status) throws SQLException {
        YearMonth month = YearMonth.parse(period);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.payrun (
                            id, tenant_id, period, period_start, period_end, cutoff_date, pay_date, paid_on,
                            run_type, status, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'seed', 'seed')
                        """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, period);
            ps.setDate(4, Date.valueOf(start));
            ps.setDate(5, Date.valueOf(end));
            ps.setDate(6, Date.valueOf(end.minusDays(5)));
            ps.setDate(7, Date.valueOf(end));
            ps.setDate(8, "PAID".equals(status) ? Date.valueOf(end) : null);
            ps.setString(9, runType);
            ps.setString(10, status);
            ps.executeUpdate();
        }
    }
}
