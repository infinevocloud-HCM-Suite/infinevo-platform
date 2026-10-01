package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * W-29.1 §7 — tenant A cannot read, lock or cancel tenant B's run, through the service and as
 * {@code app_user} on a raw connection with no Java in the way; tenant B may create a run for the
 * period tenant A already has.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunRlsIT extends AbstractIntegrationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID runOfB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_B);
        givenSchedule();
        PayRunTestSchema.insertPayableEmployee(TENANT_B, "B-01");
        runOfB = payRunService.create(APRIL).id();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Through the service, tenant A cannot read, list, lock or cancel tenant B's run")
    void serviceRefusesCrossTenant() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> payRunService.get(runOfB)).isInstanceOf(PayRunNotFoundException.class);
        assertThatThrownBy(() -> payRunService.employees(runOfB, null, PageRequest.of(0, 10)))
                .isInstanceOf(PayRunNotFoundException.class);
        assertThatThrownBy(() -> payRunService.lock(runOfB)).isInstanceOf(PayRunNotFoundException.class);
        assertThatThrownBy(() -> payRunService.cancel(runOfB)).isInstanceOf(PayRunNotFoundException.class);
        assertThat(payRunService.list(null, PageRequest.of(0, 10)).getContent()).isEmpty();

        TenantContext.set(TENANT_B);
        assertThat(payRunService.get(runOfB).status()).isEqualTo(PayRunStatus.DRAFT);
    }

    @Test
    @DisplayName("As app_user bound to tenant A, B's run and rows are invisible and an update touches nothing")
    void policyHidesAndProtectsTenantBRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            assertThat(count(conn, "SELECT count(*) FROM payroll.payrun WHERE id = ?", runOfB))
                    .isZero();
            assertThat(count(conn, "SELECT count(*) FROM payroll.employee_payrun WHERE payrun_id = ?", runOfB))
                    .isZero();
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE payroll.payrun SET status = 'CANCELLED' WHERE id = ?")) {
                ps.setObject(1, runOfB);
                assertThat(ps.executeUpdate()).isZero();
            }
            conn.rollback();

            // The control: the same reads bound to B see the rows that were hidden.
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(count(conn, "SELECT count(*) FROM payroll.payrun WHERE id = ?", runOfB))
                    .isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM payroll.employee_payrun WHERE payrun_id = ?", runOfB))
                    .isEqualTo(1);
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Tenant A may create a run for the period tenant B already has")
    void otherTenantMayUseTheSamePeriod() {
        TenantContext.set(TENANT_A);
        givenSchedule();

        PayRunResponse runOfA = payRunService.create(APRIL);

        assertThat(runOfA.id()).isNotEqualTo(runOfB);
        assertThat(runOfA.period()).isEqualTo("2026-04");
    }

    private void givenSchedule() {
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
    }

    private static long count(Connection conn, String sql, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
