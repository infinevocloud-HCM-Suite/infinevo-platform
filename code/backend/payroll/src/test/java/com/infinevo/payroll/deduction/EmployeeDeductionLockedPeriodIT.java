package com.infinevo.payroll.deduction;

import static com.infinevo.payroll.deduction.DeductionTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.payinput.PayInputLockStage;
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
 * W-35.2 §7 as D-144 changes it: a line for a LOCKED month (run locked or computed) posts to that
 * month; a line for a CLOSED month (run approved) is refused with a 409 naming the month to use, and
 * nothing of the batch is written; reversing a deduction whose month has since been closed posts the
 * reversal to the next open month, so the money comes back on the next payslip.
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
    @DisplayName("A line for a LOCKED month posts to that month (D-144)")
    void lockedPeriodPostsThere() {
        payInputService.lock(THIS_MONTH);

        EmployeeDeductionResponse row =
                deductionService.enter(List.of(line())).rows().get(0);

        assertThat(row.period()).isEqualTo(THIS_MONTH.toString());
        assertThat(row.postedPeriod()).isEqualTo(THIS_MONTH.toString());
    }

    @Test
    @DisplayName("A line for a CLOSED month is refused, naming its line and the month to use; nothing is written")
    void closedPeriodIsRefused() {
        payInputService.lock(THIS_MONTH, PayInputLockStage.CLOSED);

        assertThatThrownBy(() -> deductionService.enter(List.of(line())))
                .isInstanceOf(DeductionPeriodClosedException.class)
                .hasMessageContaining("is closed (run approved). Enter this for")
                .satisfies(e ->
                        assertThat(((DeductionPeriodClosedException) e).line()).isZero());
        assertThat(payInputService.forPeriod(THIS_MONTH).rows()).isEmpty();
        assertThat(payInputService.forPeriod(THIS_MONTH.plusMonths(1)).rows()).isEmpty();
    }

    @Test
    @DisplayName("Reversing a deduction whose month has been closed posts the reversal to the next open month")
    void reversalOfLockedPeriodRedirects() {
        EmployeeDeductionResponse row =
                deductionService.enter(List.of(line())).rows().get(0);
        payInputService.lock(THIS_MONTH, PayInputLockStage.CLOSED);

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
