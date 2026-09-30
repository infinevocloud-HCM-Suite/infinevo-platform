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

/**
 * W-29.2 §7 — as {@code app_user} bound to tenant A, none of tenant B's lines are visible, and the
 * service refuses B's lines to A; bound to B, the same read sees them.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunLineRlsIT extends AbstractIntegrationTest {

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID runOfB;
    private UUID employeeOfB;

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
        employeeOfB = PayRunTestSchema.insertWorkedExampleEmployee(
                TENANT_B, "B-01", PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_B));
        runOfB = payRunService.create(YearMonth.of(2026, 7)).id();
        payRunService.lock(runOfB);
        worker.computeNow(runOfB);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("As app_user bound to A, B's lines are invisible; bound to B they are there")
    void policyHidesTenantBLines() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            assertThat(countLines(conn)).isZero();
            conn.rollback();

            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(countLines(conn)).isEqualTo(7);
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Through the service, tenant A cannot read tenant B's lines")
    void serviceRefusesCrossTenantLines() {
        TenantContext.set(TENANT_A);
        assertThatThrownBy(() -> payRunService.lines(runOfB, employeeOfB)).isInstanceOf(PayRunNotFoundException.class);
    }

    private long countLines(Connection conn) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT count(*) FROM payroll.employee_payrun_line WHERE payrun_id = ?")) {
            ps.setObject(1, runOfB);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
