package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
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
 * W-19 §7 — {@code 12-core-contracts.md} §6 decision 2: a retried caller cannot post the same
 * source event twice, and reversing that event is not "posting it twice".
 *
 * <p>Against the real {@code uk_pay_input_tenant_source} index, not a mocked exception — proving the
 * unit test's translation actually matches what Postgres raises.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("Idempotency " + UUID.randomUUID());
        employee = PayInputTestSchema.insertEmployee(tenant, "IDEM-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A second record with the same (source_module, source_ref) is refused by the unique index")
    void secondRecordWithSameSourceIsRefused() {
        PayInputCommand command = new PayInputCommand(
                employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:idem-1");

        payInputService.record(command);

        assertThatThrownBy(() -> payInputService.record(command))
                .isInstanceOf(PayInputService.DuplicatePayInputException.class);
    }

    @Test
    @DisplayName("A reversal of that row is accepted because reverses_id is set")
    void reversalOfTheSameSourceIsAccepted() {
        PayInputCommand command = new PayInputCommand(
                employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:idem-2");
        PayInputResponse original = payInputService.record(command);

        PayInputResponse reversal = payInputService.reverse(original.id(), "correction");

        assertThat(reversal.sourceRef()).isEqualTo(original.sourceRef());
        assertThat(reversal.reversesId()).isEqualTo(original.id());
        assertThat(reversal.id()).isNotEqualTo(original.id());
    }

    @Test
    @DisplayName("A different source_ref for the same event is accepted")
    void aDifferentSourceRefIsAccepted() {
        payInputService.record(new PayInputCommand(
                employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:idem-3"));

        PayInputResponse second = payInputService.record(new PayInputCommand(
                employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:idem-4"));

        assertThat(second.sourceRef()).isEqualTo("lop:idem-4");
    }

    @Test
    @DisplayName("A second reversal of the same row is refused, and the total still nets to zero")
    void secondReversalOfTheSameRowIsRefused() {
        PayInputResponse original = payInputService.record(new PayInputCommand(
                employee,
                YearMonth.of(2026, 4),
                PayInputKind.LOP_DAYS,
                new BigDecimal("2.00"),
                null,
                "hrms",
                "lop:idem-5"));
        payInputService.reverse(original.id(), "correction");

        assertThatThrownBy(() -> payInputService.reverse(original.id(), "retried"))
                .isInstanceOf(PayInputService.AlreadyReversedException.class);

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).hasSize(2);
        assertThat(ledger.quantityTotalsByKind().get(PayInputKind.LOP_DAYS)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A reversal cannot itself be reversed")
    void aReversalCannotBeReversed() {
        PayInputResponse original = payInputService.record(new PayInputCommand(
                employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:idem-6"));
        PayInputResponse reversal = payInputService.reverse(original.id(), "correction");

        assertThatThrownBy(() -> payInputService.reverse(reversal.id(), "undo the correction"))
                .isInstanceOf(PayInputService.AlreadyReversedException.class);

        assertThat(payInputService.forEmployee(employee, YearMonth.of(2026, 4)).rows())
                .hasSize(2);
    }

    @Test
    @DisplayName("Two reversals racing each other: the index lets exactly one through")
    void concurrentReversalsLeaveExactlyOneRow() throws Exception {
        PayInputResponse original = payInputService.record(new PayInputCommand(
                employee, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:idem-7"));

        // Both threads pass the service's exists-check before either inserts: the barrier holds
        // them at the same point, so only uk_pay_input_tenant_reverses can decide the race.
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<Throwable> attempt = () -> {
            TenantContext.set(tenant);
            try {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                payInputService.reverse(original.id(), "race");
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                TenantContext.clear();
            }
        };
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            java.util.List<Throwable> outcomes = java.util.List.of(
                    java.util.Optional.ofNullable(first.get(30, java.util.concurrent.TimeUnit.SECONDS))
                            .orElse(new Success()),
                    java.util.Optional.ofNullable(second.get(30, java.util.concurrent.TimeUnit.SECONDS))
                            .orElse(new Success()));

            assertThat(outcomes).filteredOn(t -> t instanceof Success).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(t -> t instanceof PayInputService.AlreadyReversedException)
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).hasSize(2);
        assertThat(ledger.quantityTotalsByKind().get(PayInputKind.LOP_DAYS)).isEqualByComparingTo("0.00");
    }

    /** Marker for "the call returned normally" in the race test above. */
    private static final class Success extends Throwable {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }
}
