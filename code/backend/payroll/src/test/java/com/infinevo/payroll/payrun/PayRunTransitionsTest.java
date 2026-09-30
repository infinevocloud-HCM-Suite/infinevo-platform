package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-29.1 §7 — the three allowed transitions; every other pair refuses; cancel twice refuses. */
class PayRunTransitionsTest {

    private static final Set<String> ALLOWED = Set.of("DRAFT->LOCKED", "DRAFT->CANCELLED", "LOCKED->CANCELLED");

    @Test
    @DisplayName("Exactly DRAFT→LOCKED, DRAFT→CANCELLED and LOCKED→CANCELLED are allowed; all 61 other pairs throw")
    void onlyTheThreeTransitionsAreAllowed() {
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
        assertThat(refused).isEqualTo(64 - 3);
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

    private static PayRun newRun() {
        PayRun run = new PayRun(
                UUID.randomUUID(),
                YearMonth.of(2026, 4),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 4, 30),
                0,
                0,
                "officer");
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.DRAFT);
        return run;
    }
}
