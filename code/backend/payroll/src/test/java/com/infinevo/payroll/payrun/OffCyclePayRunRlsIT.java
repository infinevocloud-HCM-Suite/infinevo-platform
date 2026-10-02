package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
 * W-30.2 §7 — tenant A cannot read, add inputs to, lock or compute tenant B's off-cycle run, through
 * the service and as {@code app_user} on a raw connection; B's tagged inputs are invisible to A.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class OffCyclePayRunRlsIT extends AbstractIntegrationTest {

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID employeeOfB;
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
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        employeeOfB = PayRunTestSchema.insertPayableEmployee(TENANT_B, "B-01");
        runOfB = payRunService
                .createOffCycle(LocalDate.of(2026, 4, 15), List.of(employeeOfB), null)
                .id();
        payRunService.addInputs(
                runOfB,
                List.of(new PayRunInputRequest(
                        employeeOfB, PayInputKind.ONE_TIME_PAYOUT, new BigDecimal("1000"), "b-bonus")));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Through the service, tenant A cannot read, add inputs to, lock or compute B's off-cycle run")
    void serviceRefusesCrossTenant() {
        TenantContext.set(TENANT_A);
        PayRunInputRequest input =
                new PayRunInputRequest(employeeOfB, PayInputKind.ONE_TIME_PAYOUT, new BigDecimal("1"), "a");

        assertThatThrownBy(() -> payRunService.get(runOfB)).isInstanceOf(PayRunNotFoundException.class);
        assertThatThrownBy(() -> payRunService.addInputs(runOfB, List.of(input)))
                .isInstanceOf(PayRunNotFoundException.class);
        assertThatThrownBy(() -> payRunService.lock(runOfB)).isInstanceOf(PayRunNotFoundException.class);
        assertThatThrownBy(() -> payRunService.compute(runOfB)).isInstanceOf(PayRunNotFoundException.class);
        assertThat(payRunService
                        .list(null, PayRunType.OFF_CYCLE, PageRequest.of(0, 10))
                        .getContent())
                .isEmpty();
        // A cannot name B's employee on a run of its own either: to A there is no such employee.
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        assertThatThrownBy(() -> payRunService.createOffCycle(LocalDate.of(2026, 4, 15), List.of(employeeOfB), null))
                .isInstanceOf(EmployeeNotInRunException.class)
                .hasMessageContaining(employeeOfB.toString());

        TenantContext.set(TENANT_B);
        assertThat(payRunService.get(runOfB).status()).isEqualTo(PayRunStatus.DRAFT);
    }

    @Test
    @DisplayName("As app_user bound to tenant A, B's off-cycle run and its tagged inputs are invisible")
    void policyHidesTenantBRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            assertThat(count(conn, "SELECT count(*) FROM payroll.payrun WHERE id = ?", runOfB))
                    .isZero();
            assertThat(count(conn, "SELECT count(*) FROM core.pay_input WHERE run_ref = ?", runOfB))
                    .isZero();
            conn.rollback();

            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(count(conn, "SELECT count(*) FROM payroll.payrun WHERE id = ?", runOfB))
                    .isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM core.pay_input WHERE run_ref = ?", runOfB))
                    .isEqualTo(1);
            conn.rollback();
        }
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
