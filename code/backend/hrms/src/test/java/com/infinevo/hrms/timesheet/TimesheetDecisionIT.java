package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.hrms.project.ValidationException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.3 §7, {@code TimesheetDecisionIT}: each project's manager decides that project's line, and the decisions roll up
 * to the week. The decisions go through {@code core}'s decide, as in production.
 */
class TimesheetDecisionIT extends TimesheetApprovalSupport {

    private static final LocalDate W1 = LocalDate.of(2026, 10, 5);
    private static final String BASE = "/api/v1/hrms/timesheets";

    private UUID submittedWeek() throws Exception {
        UUID sheet = draftWeek(W1);
        submitService.submit(sheet);
        return sheet;
    }

    @Test
    @DisplayName(
            "M1 approves A and M2 rejects B with a comment: A is APPROVED, B REJECTED with that reason, the week REJECTED")
    void eachManagerDecidesTheirOwnProject() throws Exception {
        UUID sheet = submittedWeek();
        ApprovalStep stepA = newestStep(entryId(sheet, projectA));
        ApprovalStep stepB = newestStep(entryId(sheet, projectB));

        decide(m1, stepA, ApprovalDecision.APPROVED, "Looks right");
        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(weekStatus(sheet)).as("B is still waiting").isEqualTo(TimesheetStatus.SUBMITTED);

        decide(m2, stepB, ApprovalDecision.REJECTED, "Hours are on the wrong task");
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(lineReason(sheet, projectB)).isEqualTo("Hours are on the wrong task");
        assertThat(lineStatus(sheet, projectA))
                .as("one rejection does not touch the other project")
                .isEqualTo(TimesheetStatus.APPROVED);
        assertThat(lineReason(sheet, projectA)).isNull();
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.REJECTED);
    }

    @Test
    @DisplayName("A reason of the full 1000 characters the column holds is kept whole")
    void aMaximumLengthReasonIsKept() throws Exception {
        UUID sheet = submittedWeek();

        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.REJECTED, "x".repeat(1000));

        assertThat(lineReason(sheet, projectB)).hasSize(1000);
    }

    @Test
    @DisplayName("Both approved: the week is APPROVED")
    void bothApprovedApprovesTheWeek() throws Exception {
        UUID sheet = submittedWeek();

        decide(m1, newestStep(entryId(sheet, projectA)), ApprovalDecision.APPROVED, null);
        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.APPROVED, null);

        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("Deciding an already-decided step fails, so an approved line cannot be flipped, as legacy allowed")
    void aDecidedStepCannotBeDecidedAgain() throws Exception {
        UUID sheet = submittedWeek();
        ApprovalStep stepA = newestStep(entryId(sheet, projectA));
        decide(m1, stepA, ApprovalDecision.APPROVED, null);

        assertThatThrownBy(() -> decide(m1, stepA, ApprovalDecision.REJECTED, "Changed my mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been decided");

        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("The other project's manager cannot decide this project's step")
    void onlyTheAssigneeDecides() throws Exception {
        UUID sheet = submittedWeek();
        ApprovalStep stepA = newestStep(entryId(sheet, projectA));

        assertThatThrownBy(() -> decide(m2, stepA, ApprovalDecision.APPROVED, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("Decided through core's real endpoint: M1 with core.approval.decide approves A and the line follows")
    void decidedOverHttp() throws Exception {
        UUID sheet = submittedWeek();
        ApprovalStep stepA = newestStep(entryId(sheet, projectA));

        actAs(m1);
        mvc.perform(authed(post("/api/v1/approvals/steps/" + stepA.getId() + "/decide"), subM1)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"ok\"}"))
                .andExpect(status().isOk());

        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.APPROVED);
    }

    // ── resubmit ─────────────────────────────────────────────────────────────────────────────

    /** A approved, B rejected. */
    private UUID weekWithBRejected() throws Exception {
        UUID sheet = submittedWeek();
        decide(m1, newestStep(entryId(sheet, projectA)), ApprovalDecision.APPROVED, null);
        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.REJECTED, "Wrong task");
        actAs(empA);
        inTenant();
        return sheet;
    }

    @Test
    @DisplayName(
            "Resubmit B: only B changes, B is SUBMITTED, the week SUBMITTED, a new instance for B, the old one kept")
    void resubmitSendsOnlyTheRejectedProjectBack() throws Exception {
        UUID sheet = weekWithBRejected();
        UUID lineA = entryId(sheet, projectA);
        UUID lineB = entryId(sheet, projectB);

        TimesheetResponse resubmitted = timesheets.replace(sheet, request(W1, projectB, taskB, day(W1, "6")));

        assertThat(resubmitted.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(lineReason(sheet, projectB))
                .as("the reason goes with the rejection")
                .isNull();
        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(hoursOf(sheet, projectA)).isEqualTo("4.00");
        assertThat(hoursOf(sheet, projectB)).as("B's lines were replaced").isEqualTo("6.00");

        assertThat(instancesOf(lineB))
                .as("a new instance for B, the old one kept as history")
                .hasSize(2);
        assertThat(instancesOf(lineA)).as("A is not sent back").hasSize(1);
        assertThat(newestStep(lineB).getAssigneeEmployeeId()).isEqualTo(m2);
        assertThat(newestStep(lineB).getDecision()).isNull();

        decide(m2, newestStep(lineB), ApprovalDecision.APPROVED, null);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("Resubmit over HTTP: PUT /{id} on a rejected week is 200 and sends B back")
    void resubmitOverHttp() throws Exception {
        UUID sheet = weekWithBRejected();

        mvc.perform(authed(put(BASE + "/" + sheet), subA).content(body(request(W1, projectB, taskB, day(W1, "5")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("A body naming A, which is approved, is 409 and changes nothing")
    void aProjectThatIsNotRejectedIsRefused() throws Exception {
        UUID sheet = weekWithBRejected();

        assertThatThrownBy(() -> timesheets.replace(sheet, request(W1, projectA, taskA, day(W1, "1"))))
                .isInstanceOf(TimesheetConflictException.class);
        mvc.perform(authed(put(BASE + "/" + sheet), subA).content(body(request(W1, projectA, taskA, day(W1, "1")))))
                .andExpect(status().isConflict());

        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(hoursOf(sheet, projectA)).isEqualTo("4.00");
    }

    @Test
    @DisplayName("A rejected project missing from the body stays rejected, so the week stays REJECTED")
    void aRejectedProjectLeftOutStaysRejected() throws Exception {
        UUID sheet = submittedWeek();
        decide(m1, newestStep(entryId(sheet, projectA)), ApprovalDecision.REJECTED, "No");
        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.REJECTED, "No");
        actAs(empA);
        inTenant();

        TimesheetResponse after = timesheets.replace(sheet, request(W1, projectB, taskB, day(W1, "3")));

        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(after.status()).isEqualTo(TimesheetStatus.REJECTED);
    }

    @Test
    @DisplayName(
            "Resubmit keeps the 24 hours a day rule over the whole week: A's 4 hours on Monday count against B's 21")
    void theDailyLimitCountsTheWholeWeek() throws Exception {
        UUID sheet = weekWithBRejected();

        assertThatThrownBy(() -> timesheets.replace(sheet, request(W1, projectB, taskB, day(W1, "21"))))
                .isInstanceOf(ValidationException.class);
        timesheets.replace(sheet, request(W1, projectB, taskB, day(W1, "20")));

        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("A submitted week cannot be replaced or deleted: 409")
    void aSubmittedWeekIsFrozen() throws Exception {
        UUID sheet = submittedWeek();

        assertThatThrownBy(() -> timesheets.replace(sheet, request(W1, projectA, taskA, day(W1, "1"))))
                .isInstanceOf(TimesheetConflictException.class);
        assertThatThrownBy(() -> timesheets.delete(sheet)).isInstanceOf(TimesheetConflictException.class);
    }

    private String hoursOf(UUID sheet, UUID project) throws Exception {
        try (var conn = com.infinevo.hrms.project.HrmsProjectTestSchema.migrationConnection();
                var ps = conn.prepareStatement(
                        """
                        SELECT sum(d.hours) FROM hrms.timesheet_day_entry d
                          JOIN hrms.timesheet_task_entry t ON t.id = d.task_entry_id
                          JOIN hrms.timesheet_project_entry p ON p.id = t.project_entry_id
                         WHERE p.timesheet_id = ? AND p.project_id = ?
                        """)) {
            ps.setObject(1, sheet);
            ps.setObject(2, project);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).toPlainString();
            }
        }
    }
}
