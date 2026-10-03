package com.infinevo.hrms.timesheet;

import static com.infinevo.hrms.timesheet.TimesheetStatus.APPROVED;
import static com.infinevo.hrms.timesheet.TimesheetStatus.DRAFT;
import static com.infinevo.hrms.timesheet.TimesheetStatus.REJECTED;
import static com.infinevo.hrms.timesheet.TimesheetStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.3 §7, {@code TimesheetRollupTest}: every row of the roll-up ported from legacy
 * ({@code TimesheetServiceImpl.java:492-515}).
 */
class TimesheetRollupTest {

    @Test
    @DisplayName("All approved gives APPROVED")
    void allApproved() {
        assertThat(TimesheetRollup.of(List.of(APPROVED, APPROVED), SUBMITTED)).isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("One rejected among approved gives REJECTED")
    void oneRejectedAmongApproved() {
        assertThat(TimesheetRollup.of(List.of(APPROVED, REJECTED, APPROVED), SUBMITTED))
                .isEqualTo(REJECTED);
    }

    @Test
    @DisplayName("One submitted, one approved gives SUBMITTED")
    void oneSubmittedOneApproved() {
        assertThat(TimesheetRollup.of(List.of(SUBMITTED, APPROVED), SUBMITTED)).isEqualTo(SUBMITTED);
    }

    @Test
    @DisplayName("Rejected wins over submitted: a rejected line keeps the week rejected while another is pending")
    void rejectedBeatsSubmitted() {
        assertThat(TimesheetRollup.of(List.of(REJECTED, SUBMITTED), SUBMITTED)).isEqualTo(REJECTED);
    }

    @Test
    @DisplayName(
            "Resubmitted after rejection: the rejected line is submitted again, the rest approved, gives SUBMITTED")
    void resubmittedAfterRejection() {
        assertThat(TimesheetRollup.of(List.of(APPROVED, SUBMITTED), REJECTED)).isEqualTo(SUBMITTED);
        assertThat(TimesheetRollup.of(List.of(APPROVED, APPROVED), REJECTED)).isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("One line alone decides its week")
    void singleLine() {
        assertThat(TimesheetRollup.of(List.of(REJECTED), SUBMITTED)).isEqualTo(REJECTED);
        assertThat(TimesheetRollup.of(List.of(APPROVED), SUBMITTED)).isEqualTo(APPROVED);
        assertThat(TimesheetRollup.of(List.of(SUBMITTED), DRAFT)).isEqualTo(SUBMITTED);
    }

    @Test
    @DisplayName("A line still in draft, or no lines, does not settle the week: its status stands")
    void draftOrEmptyLeavesTheStatusAlone() {
        assertThat(TimesheetRollup.of(List.of(DRAFT, APPROVED), SUBMITTED)).isEqualTo(SUBMITTED);
        assertThat(TimesheetRollup.of(List.of(), DRAFT)).isEqualTo(DRAFT);
    }
}
