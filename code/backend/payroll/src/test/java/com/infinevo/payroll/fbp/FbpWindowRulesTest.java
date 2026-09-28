package com.infinevo.payroll.fbp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FBP declaration window rules and request validation (W-27.1).
 */
class FbpWindowRulesTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("isWindowOpen returns false when plan is disabled")
    void windowClosedWhenDisabled() {
        FbpPlan plan = createPlan(false, false, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 10)))
                .isFalse();
    }

    @Test
    @DisplayName("isWindowOpen returns false when plan is locked")
    void windowClosedWhenLocked() {
        FbpPlan plan = createPlan(true, true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 10)))
                .isFalse();
    }

    @Test
    @DisplayName("isWindowOpen returns false before window opens")
    void windowClosedBeforeOpen() {
        FbpPlan plan = createPlan(true, false, LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 4)))
                .isFalse();
    }

    @Test
    @DisplayName("isWindowOpen returns false after window closes")
    void windowClosedAfterClose() {
        FbpPlan plan = createPlan(true, false, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 16)))
                .isFalse();
    }

    @Test
    @DisplayName("isWindowOpen returns true on opening boundary day")
    void windowOpenOnBoundaryOpen() {
        FbpPlan plan = createPlan(true, false, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 1)))
                .isTrue();
    }

    @Test
    @DisplayName("isWindowOpen returns true on closing boundary day")
    void windowOpenOnBoundaryClose() {
        FbpPlan plan = createPlan(true, false, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 15)))
                .isTrue();
    }

    @Test
    @DisplayName("isWindowOpen returns true strictly inside the window")
    void windowOpenInsideRange() {
        FbpPlan plan = createPlan(true, false, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.of(2026, 4, 10)))
                .isTrue();
    }

    @Test
    @DisplayName("isWindowOpen returns false when dates or plan are null")
    void windowClosedOnNulls() {
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(null, LocalDate.now())).isFalse();
        FbpPlan plan = createPlan(true, false, null, null);
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, LocalDate.now())).isFalse();
        assertThat(FbpPlanServiceImpl.isPlanWindowOpen(plan, null)).isFalse();
    }

    @Test
    @DisplayName("Validation: closes-before-opens is refused")
    void closesBeforeOpensRefused() {
        FbpPlanRequest request = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 10), true, true, List.of(5, 1));

        assertThatThrownBy(() -> FbpPlanServiceImpl.validateRequest(request))
                .isInstanceOf(FbpValidationException.class)
                .hasMessageContaining("Window close date must be on or after window open date");
    }

    @Test
    @DisplayName("Validation: missing dates when enabled is refused")
    void missingDatesWhenEnabledRefused() {
        FbpPlanRequest requestWithoutOpens =
                new FbpPlanRequest(true, null, LocalDate.of(2026, 4, 15), true, true, List.of(5, 1));
        assertThatThrownBy(() -> FbpPlanServiceImpl.validateRequest(requestWithoutOpens))
                .isInstanceOf(FbpValidationException.class)
                .hasMessageContaining("Window open date is required");

        FbpPlanRequest requestWithoutCloses =
                new FbpPlanRequest(true, LocalDate.of(2026, 4, 1), null, true, true, List.of(5, 1));
        assertThatThrownBy(() -> FbpPlanServiceImpl.validateRequest(requestWithoutCloses))
                .isInstanceOf(FbpValidationException.class)
                .hasMessageContaining("Window close date is required");
    }

    @Test
    @DisplayName("Validation: reminder days out of range (< 1 or > 60) is refused")
    void reminderDaysOutOfRangeRefused() {
        FbpPlanRequest requestBelowOne = new FbpPlanRequest(false, null, null, true, true, List.of(0, 5));
        assertThatThrownBy(() -> FbpPlanServiceImpl.validateRequest(requestBelowOne))
                .isInstanceOf(FbpValidationException.class)
                .hasMessageContaining("between 1 and 60");

        FbpPlanRequest requestAboveSixty = new FbpPlanRequest(false, null, null, true, true, List.of(61, 5));
        assertThatThrownBy(() -> FbpPlanServiceImpl.validateRequest(requestAboveSixty))
                .isInstanceOf(FbpValidationException.class)
                .hasMessageContaining("between 1 and 60");
    }

    @Test
    @DisplayName("Validation: duplicate reminder days is refused")
    void duplicateReminderDaysRefused() {
        FbpPlanRequest request = new FbpPlanRequest(false, null, null, true, true, List.of(5, 5));
        assertThatThrownBy(() -> FbpPlanServiceImpl.validateRequest(request))
                .isInstanceOf(FbpValidationException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    @DisplayName("Validation: valid request passes without error")
    void validRequestPasses() {
        FbpPlanRequest request = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15), true, true, List.of(10, 5, 1));
        FbpPlanServiceImpl.validateRequest(request);
    }

    @Test
    @DisplayName("Empty reminder list passes validation and sets empty array on entity")
    void emptyReminderListTurnsOffReminders() {
        FbpPlanRequest request = new FbpPlanRequest(false, null, null, true, true, List.of());
        FbpPlanServiceImpl.validateRequest(request);

        FbpPlan plan = new FbpPlan(TENANT_ID, "test");
        assertThat(plan.getReminderDaysBeforeClose()).containsExactly(5, 1);

        plan.setReminderDaysBeforeClose(List.of());
        assertThat(plan.getReminderDaysBeforeClose()).isEmpty();
    }

    private static FbpPlan createPlan(boolean enabled, boolean locked, LocalDate opensOn, LocalDate closesOn) {
        FbpPlan plan = new FbpPlan(TENANT_ID, "test");
        plan.setEnabled(enabled);
        plan.setLocked(locked);
        plan.setWindowOpensOn(opensOn);
        plan.setWindowClosesOn(closesOn);
        return plan;
    }
}
