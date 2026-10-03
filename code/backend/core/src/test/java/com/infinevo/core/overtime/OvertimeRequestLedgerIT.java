package com.infinevo.core.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for overtime request state transitions against real PayInputService (W-40.5 §7).
 */
@SpringBootTest(classes = OvertimeTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            OvertimeTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class OvertimeRequestLedgerIT extends AbstractIntegrationTest {

    @Autowired
    private OvertimeService overtimeService;

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = OvertimeTestSchema.insertTenant("RequestLedger " + UUID.randomUUID());
        employee = OvertimeTestSchema.insertEmployee(tenant, "OT-REQ-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("after submit, zero core.pay_input rows exist for the employee")
    void afterSubmit_zeroPayInputRowsExist() {
        OvertimeResponse submitted = overtimeService.submit(
                new OvertimeEntry(employee, LocalDate.of(2026, 4, 10), new BigDecimal("4.50"), null, "Extra hours"));

        assertThat(submitted.status()).isEqualTo(OvertimeStatus.PENDING);
        assertThat(submitted.source()).isEqualTo(OvertimeSource.REQUEST);
        assertThat(submitted.payInputId()).isNull();

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).isEmpty();
    }

    @Test
    @DisplayName("after approve, exactly one OVERTIME row with quantity = hours; second approve leaves one")
    void afterApprove_exactlyOneLedgerRow_secondApproveLeavesOne() {
        OvertimeResponse submitted = overtimeService.submit(
                new OvertimeEntry(employee, LocalDate.of(2026, 4, 10), new BigDecimal("3.50"), null, "Extra hours"));

        OvertimeResponse approved = overtimeService.approve(submitted.id());

        assertThat(approved.status()).isEqualTo(OvertimeStatus.APPROVED);
        assertThat(approved.payInputId()).isNotNull();
        assertThat(approved.postedPeriod()).isEqualTo(YearMonth.of(2026, 4));

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).hasSize(1);
        var row = ledger.rows().get(0);
        assertThat(row.id()).isEqualTo(approved.payInputId());
        assertThat(row.kind()).isEqualTo(PayInputKind.OVERTIME);
        assertThat(row.quantity()).isEqualByComparingTo("3.50");
        assertThat(row.sourceRef()).isEqualTo("overtime_request:" + submitted.id());

        // Second approve is idempotent
        OvertimeResponse secondApprove = overtimeService.approve(submitted.id());
        assertThat(secondApprove.status()).isEqualTo(OvertimeStatus.APPROVED);

        var ledgerAfterSecond = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledgerAfterSecond.rows()).hasSize(1);
    }

    @Test
    @DisplayName("reject leaves zero ledger rows")
    void reject_leavesZeroLedgerRows() {
        OvertimeResponse submitted = overtimeService.submit(new OvertimeEntry(
                employee, LocalDate.of(2026, 4, 10), new BigDecimal("2.00"), null, "Rejected request"));

        OvertimeResponse rejected = overtimeService.reject(submitted.id());

        assertThat(rejected.status()).isEqualTo(OvertimeStatus.REJECTED);
        assertThat(rejected.payInputId()).isNull();

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).isEmpty();

        // Reject is idempotent
        OvertimeResponse secondReject = overtimeService.reject(submitted.id());
        assertThat(secondReject.status()).isEqualTo(OvertimeStatus.REJECTED);
        assertThat(payInputService.forEmployee(employee, YearMonth.of(2026, 4)).rows())
                .isEmpty();
    }

    @Test
    @DisplayName("cancel of a PENDING row leaves zero ledger rows and no reversal row")
    void cancelOfPendingRow_leavesZeroLedgerRows() {
        OvertimeResponse submitted = overtimeService.submit(new OvertimeEntry(
                employee, LocalDate.of(2026, 4, 10), new BigDecimal("1.50"), null, "Withdrawn request"));

        OvertimeResponse cancelled = overtimeService.cancel(submitted.id());

        assertThat(cancelled.status()).isEqualTo(OvertimeStatus.CANCELLED);
        assertThat(cancelled.payInputId()).isNull();

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).isEmpty();
    }

    @Test
    @DisplayName("cancel of a REJECTED row throws NotCancellableException")
    void cancelOfRejectedRow_throwsNotCancellableException() {
        OvertimeResponse submitted = overtimeService.submit(
                new OvertimeEntry(employee, LocalDate.of(2026, 4, 10), new BigDecimal("2.00"), null, "To be rejected"));
        overtimeService.reject(submitted.id());

        assertThatThrownBy(() -> overtimeService.cancel(submitted.id()))
                .isInstanceOf(OvertimeService.NotCancellableException.class);
    }
}
