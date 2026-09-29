package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for leave request status transitions and entity invariants (W-16.3, spec section 7).
 */
class LeaveRequestStatusTest {

    @Test
    @DisplayName("every allowed transition in section 4 is accepted")
    void allowedTransitionsAccepted() {
        // DRAFT -> PENDING
        assertThat(LeaveRequestStatus.DRAFT.canTransitionTo(LeaveRequestStatus.PENDING))
                .isTrue();

        // PENDING -> APPROVED, REJECTED, WITHDRAWN
        assertThat(LeaveRequestStatus.PENDING.canTransitionTo(LeaveRequestStatus.APPROVED))
                .isTrue();
        assertThat(LeaveRequestStatus.PENDING.canTransitionTo(LeaveRequestStatus.REJECTED))
                .isTrue();
        assertThat(LeaveRequestStatus.PENDING.canTransitionTo(LeaveRequestStatus.WITHDRAWN))
                .isTrue();

        // APPROVED -> CANCELLED
        assertThat(LeaveRequestStatus.APPROVED.canTransitionTo(LeaveRequestStatus.CANCELLED))
                .isTrue();
    }

    @Test
    @DisplayName("illegal status transitions are refused")
    void illegalTransitionsRefused() {
        // DRAFT cannot jump directly to terminal or decided states
        assertThat(LeaveRequestStatus.DRAFT.canTransitionTo(LeaveRequestStatus.APPROVED))
                .isFalse();
        assertThat(LeaveRequestStatus.DRAFT.canTransitionTo(LeaveRequestStatus.REJECTED))
                .isFalse();
        assertThat(LeaveRequestStatus.DRAFT.canTransitionTo(LeaveRequestStatus.WITHDRAWN))
                .isFalse();
        assertThat(LeaveRequestStatus.DRAFT.canTransitionTo(LeaveRequestStatus.CANCELLED))
                .isFalse();

        // PENDING cannot be cancelled directly (must be approved first)
        assertThat(LeaveRequestStatus.PENDING.canTransitionTo(LeaveRequestStatus.CANCELLED))
                .isFalse();
        assertThat(LeaveRequestStatus.PENDING.canTransitionTo(LeaveRequestStatus.DRAFT))
                .isFalse();

        // APPROVED cannot be withdrawn (withdraw after a decision is refused)
        assertThat(LeaveRequestStatus.APPROVED.canTransitionTo(LeaveRequestStatus.WITHDRAWN))
                .isFalse();
        assertThat(LeaveRequestStatus.APPROVED.canTransitionTo(LeaveRequestStatus.REJECTED))
                .isFalse();
        assertThat(LeaveRequestStatus.APPROVED.canTransitionTo(LeaveRequestStatus.PENDING))
                .isFalse();

        // Terminal states cannot transition to anything
        for (LeaveRequestStatus terminal : new LeaveRequestStatus[] {
            LeaveRequestStatus.REJECTED, LeaveRequestStatus.CANCELLED, LeaveRequestStatus.WITHDRAWN
        }) {
            for (LeaveRequestStatus any : LeaveRequestStatus.values()) {
                assertThat(terminal.canTransitionTo(any)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("a half-day without half_day_period is refused by entity validation")
    void halfDayWithoutPeriodRefused() {
        UUID tenantId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> new LeaveRequest(
                        tenantId,
                        empId,
                        typeId,
                        date,
                        date,
                        true,
                        null,
                        new BigDecimal("0.50"),
                        "Doctor visit",
                        LeaveRequestStatus.DRAFT,
                        null,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("halfDayPeriod is required");
    }

    @Test
    @DisplayName("a full-day with half_day_period is refused by entity validation")
    void fullDayWithPeriodRefused() {
        UUID tenantId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> new LeaveRequest(
                        tenantId,
                        empId,
                        typeId,
                        date,
                        date,
                        false,
                        HalfDayPeriod.FIRST,
                        new BigDecimal("1.00"),
                        "Personal",
                        LeaveRequestStatus.DRAFT,
                        null,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("halfDayPeriod must be null");
    }
}
