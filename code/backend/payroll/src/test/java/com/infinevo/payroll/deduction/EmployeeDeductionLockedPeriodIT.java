package com.infinevo.payroll.deduction;

import static com.infinevo.payroll.deduction.DeductionTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
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

/**
 * W-35.2 §7 — a locked period is the ledger's to handle (§3): a line for it posts to the next open
 * period and {@code posted_period} says so; reversing a deduction whose period has since been locked
 * posts the reversal to the next open period, so the money comes back on the next payslip.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeDeductionLockedPeriodIT extends AbstractIntegrationTest {

    private static final YearMonth THIS_MONTH = YearMonth.now(ZoneId.of("Asia/Kolkata"));

    @Autowired
    private EmployeeDeductionService deductionService;

    @Autowired
    private PayInputService payInputService;

    private UUID employee;

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
        TenantContext.set(TENANT_A);
        employee = DeductionTestSchema.insertEmployee(TENANT_A, "L-01", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A line for a locked period posts to the next period; posted_period says so, period keeps the ask")
    void lockedPeriodRedirects() {
        payInputService.lock(THIS_MONTH);

        EmployeeDeductionResponse row =
                deductionService.enter(List.of(line())).rows().get(0);

        assertThat(row.period()).isEqualTo(THIS_MONTH.toString());
        assertThat(row.postedPeriod()).isEqualTo(THIS_MONTH.plusMonths(1).toString());
        assertThat(payInputService.forPeriod(THIS_MONTH.plusMonths(1)).rows())
                .extracting(PayInputResponse::id)
                .containsExactly(row.payInputId());
    }

    @Test
    @DisplayName("Reversing a deduction whose period has been locked posts the reversal to the next open period")
    void reversalOfLockedPeriodRedirects() {
        EmployeeDeductionResponse row =
                deductionService.enter(List.of(line())).rows().get(0);
        payInputService.lock(THIS_MONTH);

        EmployeeDeductionResponse reversed = deductionService.reverse(row.id(), "recovered in cash");

        assertThat(reversed.status()).isEqualTo(DeductionState.REVERSED);
        assertThat(payInputService.forPeriod(THIS_MONTH.plusMonths(1)).rows())
                .singleElement()
                .satisfies(reversal -> {
                    assertThat(reversal.id()).isEqualTo(reversed.reversalPayInputId());
                    assertThat(reversal.reversesId()).isEqualTo(row.payInputId());
                });
    }

    private EmployeeDeductionLineRequest line() {
        return new EmployeeDeductionLineRequest(
                employee, THIS_MONTH.toString(), "ADVANCE_RECOVERY", new BigDecimal("2500"), "Advance", null, null);
    }
}
