package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-29.1 §7 and W-29.2 §3 — the eight allowed transitions; every other pair refuses; cancel twice
 * refuses; the computation methods move a run through COMPUTING.
 */
class PayRunTransitionsTest {

    private static final Set<String> ALLOWED = Set.of(
            "DRAFT->LOCKED",
            "DRAFT->CANCELLED",
            "LOCKED->CANCELLED",
            "LOCKED->COMPUTING",
            "COMPUTED->COMPUTING",
            "COMPUTED->APPROVED",
            "FAILED->COMPUTING",
            "COMPUTING->COMPUTED",
            "COMPUTING->FAILED",
            "APPROVED->PAID",
            "APPROVED->CANCELLED");

    @Test
    @DisplayName("Exactly the allowed transitions are permitted; all other pairs throw")
    void onlyTheImplementedTransitionsAreAllowed() {
        int refused = 0;
        for (PayRunStatus from : PayRunStatus.values()) {
            for (PayRunStatus to : PayRunStatus.values()) {
                String pair = from + "->" + to;
                if (ALLOWED.contains(pair)) {
                    assertThat(from.canTransitionTo(to)).as(pair).isTrue();
                    from.requireTransitionTo(to);
                } else {
                    assertThat(from.canTransitionTo(to)).as(pair).isFalse();
                    assertThatThrownBy(() -> from.requireTransitionTo(to))
                            .as(pair)
                            .isInstanceOf(IllegalPayRunTransitionException.class);
                    refused++;
                }
            }
        }
        assertThat(refused).isEqualTo(64 - ALLOWED.size());
    }

    @Test
    @DisplayName("A new run is DRAFT; lock stamps locked_at and locked_by")
    void lockStampsTheRun() {
        PayRun run = newRun();
        Instant at = Instant.parse("2026-04-25T10:00:00Z");

        run.lock("officer", at);

        assertThat(run.getStatus()).isEqualTo(PayRunStatus.LOCKED);
        assertThat(run.getLockedAt()).isEqualTo(at);
        assertThat(run.getLockedBy()).isEqualTo("officer");
    }

    @Test
    @DisplayName("Lock twice throws; cancel twice throws; lock after cancel throws")
    void repeatsAreRefused() {
        PayRun locked = newRun();
        locked.lock("officer", Instant.now());
        assertThatThrownBy(() -> locked.lock("officer", Instant.now()))
                .isInstanceOf(IllegalPayRunTransitionException.class);

        PayRun cancelled = newRun();
        cancelled.cancel("officer", Instant.now());
        assertThat(cancelled.getStatus()).isEqualTo(PayRunStatus.CANCELLED);
        assertThat(cancelled.getCancelledBy()).isEqualTo("officer");
        assertThatThrownBy(() -> cancelled.cancel("officer", Instant.now()))
                .isInstanceOf(IllegalPayRunTransitionException.class);
        assertThatThrownBy(() -> cancelled.lock("officer", Instant.now()))
                .isInstanceOf(IllegalPayRunTransitionException.class);
    }

    @Test
    @DisplayName("A locked run can be cancelled")
    void lockedRunCanBeCancelled() {
        PayRun run = newRun();
        run.lock("officer", Instant.now());
        run.cancel("officer", Instant.now());
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.CANCELLED);
    }

    @Test
    @DisplayName("Compute: LOCKED → COMPUTING → COMPUTED with totals; FAILED keeps the reason; DRAFT cannot compute")
    void computationTransitions() {
        PayRun draft = newRun();
        assertThatThrownBy(() -> draft.startComputing("officer")).isInstanceOf(IllegalPayRunTransitionException.class);

        PayRun run = newRun();
        run.lock("officer", Instant.now());
        run.startComputing("officer");
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTING);
        assertThatThrownBy(() -> run.startComputing("officer")).isInstanceOf(IllegalPayRunTransitionException.class);

        run.failComputation(
                "1 of 3 employees could not be computed",
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                0,
                "officer",
                Instant.now());
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.FAILED);
        assertThat(run.getFailureReason()).contains("1 of 3");

        run.startComputing("officer");
        assertThat(run.getFailureReason()).isNull();
        run.completeComputation(
                new BigDecimal("45000.0000"), BigDecimal.ZERO, new BigDecimal("47000.00"), 1, "officer", Instant.now());
        assertThat(run.getNegativeNetCount()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(run.getTotalNetPay()).isEqualByComparingTo("47000.00");
        assertThat(run.getComputedAt()).isNotNull();
        assertThatThrownBy(() -> run.cancel("officer", Instant.now()))
                .isInstanceOf(IllegalPayRunTransitionException.class);
    }

    @Test
    @DisplayName("Approve: COMPUTED → APPROVED stamps approved_at and approved_by; all other statuses throw")
    void approveTransitions() {
        for (PayRunStatus status : PayRunStatus.values()) {
            if (status != PayRunStatus.COMPUTED) {
                assertThat(status.canTransitionTo(PayRunStatus.APPROVED))
                        .as(status + " cannot transition to APPROVED")
                        .isFalse();
            }
        }

        PayRun run = newComputedRun();
        Instant at = Instant.parse("2026-04-28T12:00:00Z");
        run.approve("approver_officer", at);

        assertThat(run.getStatus()).isEqualTo(PayRunStatus.APPROVED);
        assertThat(run.getApprovedAt()).isEqualTo(at);
        assertThat(run.getApprovedBy()).isEqualTo("approver_officer");

        // Approve again throws
        assertThatThrownBy(() -> run.approve("approver_officer", at))
                .isInstanceOf(IllegalPayRunTransitionException.class);
    }

    @Test
    @DisplayName(
            "Pay: APPROVED → PAID stamps paid_at, paid_by, paid_on, payslips_released_at; all other statuses throw")
    void payTransitions() {
        for (PayRunStatus status : PayRunStatus.values()) {
            if (status != PayRunStatus.APPROVED) {
                assertThat(status.canTransitionTo(PayRunStatus.PAID))
                        .as(status + " cannot transition to PAID")
                        .isFalse();
            }
        }

        PayRun run = newComputedRun();
        Instant approvedAt = Instant.parse("2026-04-28T12:00:00Z");
        run.approve("approver", approvedAt);

        LocalDate paidOn = run.getPeriodEnd().isAfter(LocalDate.now()) ? LocalDate.now() : run.getPeriodEnd();
        Instant paidAt = Instant.now();
        run.pay(paidOn, "payer_officer", paidAt);

        assertThat(run.getStatus()).isEqualTo(PayRunStatus.PAID);
        assertThat(run.getPaidAt()).isEqualTo(paidAt);
        assertThat(run.getPaidBy()).isEqualTo("payer_officer");
        assertThat(run.getPaidOn()).isEqualTo(paidOn);
        assertThat(run.getPayslipsReleasedAt()).isEqualTo(paidAt);

        // Pay again throws
        assertThatThrownBy(() -> run.pay(paidOn, "payer_officer", paidAt))
                .isInstanceOf(IllegalPayRunTransitionException.class);

        // A paid run stays paid: the money moved (W-36.2 §10)
        assertThatThrownBy(() -> run.cancel("officer", Instant.now()))
                .isInstanceOf(IllegalPayRunTransitionException.class);
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.PAID);
    }

    @Test
    @DisplayName("Pay date validation: null, future date or date before period start throws IllegalArgumentException")
    void payDateValidation() {
        PayRun run = newComputedRun();
        run.approve("approver", Instant.now());

        // Null
        assertThatThrownBy(() -> run.pay(null, "payer", Instant.now())).isInstanceOf(NullPointerException.class);

        // Future date
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThatThrownBy(() -> run.pay(tomorrow, "payer", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");

        // Before period start
        LocalDate beforePeriod = run.getPeriodStart().minusDays(1);
        assertThatThrownBy(() -> run.pay(beforePeriod, "payer", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period start");
    }

    private static PayRun newComputedRun() {
        PayRun run = newRun();
        run.lock("officer", Instant.now());
        run.startComputing("officer");
        run.completeComputation(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), 0, "officer", Instant.now());
        return run;
    }

    private static PayRun newRun() {
        LocalDate start = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        PayRun run = new PayRun(
                UUID.randomUUID(), YearMonth.from(start), start, end, start.plusDays(24), end, 0, 0, "officer");
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.DRAFT);
        return run;
    }
}
