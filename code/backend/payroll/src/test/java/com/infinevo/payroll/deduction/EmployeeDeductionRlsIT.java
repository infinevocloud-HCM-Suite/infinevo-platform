package com.infinevo.payroll.deduction;

import static com.infinevo.payroll.deduction.DeductionTestSchema.TENANT_A;
import static com.infinevo.payroll.deduction.DeductionTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * W-35.2 §7 — tenant A cannot read or reverse tenant B's deduction, through the service and as
 * {@code app_user} on a raw connection; a raw {@code INSERT} with B's id under A's binding is refused.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeDeductionRlsIT extends AbstractIntegrationTest {

    private static final YearMonth THIS_MONTH = YearMonth.now(ZoneId.of("Asia/Kolkata"));

    @Autowired
    private EmployeeDeductionService deductionService;

    private UUID employeeOfB;
    private UUID deductionOfB;

    @BeforeAll
    static void applySchema() throws Exception {
        DeductionTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        DeductionTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        DeductionTestSchema.clean();
        employeeOfB = DeductionTestSchema.insertEmployee(TENANT_B, "B-01", "ACTIVE");
        TenantContext.set(TENANT_B);
        deductionOfB = deductionService
                .enter(List.of(new EmployeeDeductionLineRequest(
                        employeeOfB, THIS_MONTH.toString(), "PENALTY", new BigDecimal("300"), "Late", null, null)))
                .rows()
                .get(0)
                .id();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Through the service, tenant A cannot read, list or reverse B's deduction, nor deduct from B's employee")
    void serviceRefusesCrossTenant() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> deductionService.get(deductionOfB))
                .isInstanceOf(EmployeeDeductionNotFoundException.class);
        assertThatThrownBy(() -> deductionService.reverse(deductionOfB, "not yours"))
                .isInstanceOf(EmployeeDeductionNotFoundException.class);
        assertThat(deductionService
                        .list(null, null, null, null, PageRequest.of(0, 10))
                        .getContent())
                .isEmpty();
        assertThatThrownBy(() -> deductionService.enter(List.of(new EmployeeDeductionLineRequest(
                        employeeOfB, THIS_MONTH.toString(), "OTHER", BigDecimal.TEN, "x", null, null))))
                .isInstanceOf(EmployeeDeductionValidationException.class);

        TenantContext.set(TENANT_B);
        assertThat(deductionService.get(deductionOfB).status()).isEqualTo(DeductionState.POSTED);
    }

    @Test
    @DisplayName("As app_user bound to A: B's row is invisible, an update touches nothing, an INSERT for B is refused")
    void policyHidesAndProtects() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            assertThat(count(conn, deductionOfB)).isZero();
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE payroll.employee_deduction SET status = 'REVERSED' WHERE id = ?")) {
                ps.setObject(1, deductionOfB);
                assertThat(ps.executeUpdate()).isZero();
            }
            conn.rollback();

            PayrollTestSchema.bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.employee_deduction
                        (tenant_id, employee_id, period, deduction_type, amount, reason, status,
                         pay_input_id, posted_period)
                    VALUES (?, ?, '2026-01', 'OTHER', 10, 'x', 'POSTED', ?, '2026-01')
                    """)) {
                ps.setObject(1, TENANT_B);
                ps.setObject(2, employeeOfB);
                ps.setObject(3, UUID.randomUUID());
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }
            conn.rollback();

            // The control: bound to B, the row is there.
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(count(conn, deductionOfB)).isEqualTo(1);
            conn.rollback();
        }
    }

    private static long count(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT count(*) FROM payroll.employee_deduction WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
