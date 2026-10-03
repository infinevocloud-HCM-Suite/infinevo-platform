package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.StepDecision;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.3 §7, {@code TimesheetStaleOutcomeIT}: an outcome from an instance that a resubmit has replaced changes nothing,
 * and neither does an outcome delivered twice.
 */
class TimesheetStaleOutcomeIT extends TimesheetApprovalSupport {

    private static final LocalDate W1 = LocalDate.of(2026, 10, 5);

    /** B rejected, resubmitted: B's line has an old (rejected) instance and a newer pending one. */
    private UUID weekWithBResubmitted() throws Exception {
        UUID sheet = draftWeek(W1);
        submitService.submit(sheet);
        decide(m1, newestStep(entryId(sheet, projectA)), ApprovalDecision.APPROVED, null);
        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.REJECTED, "Wrong task");
        actAs(empA);
        inTenant();
        timesheets.replace(sheet, request(W1, projectB, taskB, day(W1, "3")));
        return sheet;
    }

    private static List<StepDecision> decisions(ApprovalDecision decision, String comment) {
        return List.of(new StepDecision(UUID.randomUUID(), UUID.randomUUID(), decision.name(), comment));
    }

    @Test
    @DisplayName("A late outcome from B's first instance, after the resubmit, changes nothing: approved or rejected")
    void anOlderInstancesOutcomeIsIgnored() throws Exception {
        UUID sheet = weekWithBResubmitted();
        UUID lineB = entryId(sheet, projectB);
        List<ApprovalInstance> all = instancesOf(lineB);
        assertThat(all).hasSize(2);
        UUID oldInstance = all.get(0).getId();
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);

        outcomeHandler.onApproved(oldInstance, decisions(ApprovalDecision.APPROVED, "late"));
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.SUBMITTED);

        outcomeHandler.onRejected(oldInstance, decisions(ApprovalDecision.REJECTED, "late too"));
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(lineReason(sheet, projectB)).isNull();
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("The same outcome delivered twice is applied once: the second changes nothing")
    void aRepeatedOutcomeIsIgnored() throws Exception {
        UUID sheet = weekWithBResubmitted();
        UUID lineB = entryId(sheet, projectB);
        ApprovalStep step = newestStep(lineB);
        UUID newest = instancesOf(lineB).get(1).getId();
        decide(m2, step, ApprovalDecision.APPROVED, null);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.APPROVED);

        outcomeHandler.onRejected(newest, decisions(ApprovalDecision.REJECTED, "again"));

        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(lineReason(sheet, projectB)).isNull();
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.APPROVED);
    }
}
