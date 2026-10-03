package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.notification.NotificationEvent;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * W-42.3 §7, {@code TimesheetNotificationIT}: {@code APPROVAL_PENDING} goes to each approver when a week is
 * submitted, {@code APPROVAL_DECIDED} to the employee when a line is decided, and a failing compose never undoes either.
 */
class TimesheetNotificationIT extends TimesheetApprovalSupport {

    private static final LocalDate W1 = LocalDate.of(2026, 10, 5);

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(UUID recipient, NotificationEvent event) {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications).compose(eq(event), eq(recipient), data.capture());
        return data.getValue();
    }

    @Test
    @DisplayName("Submit composes one APPROVAL_PENDING per assigned approver, titled with the project and the week")
    void submitNotifiesEachApprover() throws Exception {
        UUID sheet = draftWeek(W1);

        submitService.submit(sheet);

        verify(notifications, times(2)).compose(eq(NotificationEvent.APPROVAL_PENDING), any(), anyMap());
        Map<String, Object> toM1 = dataOf(m1, NotificationEvent.APPROVAL_PENDING);
        assertThat(toM1.get("request_title").toString())
                .startsWith("Timesheet Approval A ")
                .endsWith(", week of " + W1);
        assertThat(toM1).containsKeys("employee_name", "requester_name", "request_title");
        assertThat(dataOf(m2, NotificationEvent.APPROVAL_PENDING)
                        .get("request_title")
                        .toString())
                .startsWith("Timesheet Approval B ");
    }

    @Test
    @DisplayName("An unassigned step is nobody's to notify: no notice for it")
    void anUnassignedStepNotifiesNobody() throws Exception {
        // B's manager is gone: its project has none to resolve to.
        com.infinevo.hrms.project.HrmsProjectTestSchema.update(
                "UPDATE hrms.project SET manager_employee_id = NULL WHERE id = ?", projectB);
        UUID sheet = draftWeek(W1);

        submitService.submit(sheet);

        verify(notifications, times(1)).compose(eq(NotificationEvent.APPROVAL_PENDING), any(), anyMap());
        verify(notifications).compose(eq(NotificationEvent.APPROVAL_PENDING), eq(m1), anyMap());
    }

    @Test
    @DisplayName("A decision composes one APPROVAL_DECIDED to the employee: approved, then rejected")
    void aDecisionNotifiesTheEmployee() throws Exception {
        UUID sheet = draftWeek(W1);
        submitService.submit(sheet);

        decide(m1, newestStep(entryId(sheet, projectA)), ApprovalDecision.APPROVED, null);
        Map<String, Object> approved = dataOf(empA, NotificationEvent.APPROVAL_DECIDED);
        assertThat(approved.get("decision")).isEqualTo("approved");
        assertThat(approved.get("request_title").toString()).startsWith("Timesheet Approval A ");

        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.REJECTED, "No");
        verify(notifications, times(2)).compose(eq(NotificationEvent.APPROVAL_DECIDED), eq(empA), anyMap());
    }

    @Test
    @DisplayName("A compose that fails still leaves the week SUBMITTED, with its instances")
    void aFailingComposeDoesNotUndoTheSubmit() throws Exception {
        when(notifications.compose(any(), any(), anyMap())).thenThrow(new IllegalStateException("mail is down"));
        UUID sheet = draftWeek(W1);

        TimesheetResponse submitted = submitService.submit(sheet);

        assertThat(submitted.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(instancesOf(entryId(sheet, projectA))).hasSize(1);
        assertThat(instancesOf(entryId(sheet, projectB))).hasSize(1);
    }

    @Test
    @DisplayName("A compose that fails still leaves a decision recorded, and the week rolled up")
    void aFailingComposeDoesNotUndoADecision() throws Exception {
        UUID sheet = draftWeek(W1);
        submitService.submit(sheet);
        when(notifications.compose(any(), any(), anyMap())).thenThrow(new IllegalStateException("mail is down"));

        decide(m1, newestStep(entryId(sheet, projectA)), ApprovalDecision.APPROVED, null);
        decide(m2, newestStep(entryId(sheet, projectB)), ApprovalDecision.APPROVED, null);

        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("A submit that is refused composes nothing")
    void aRefusedSubmitNotifiesNobody() throws Exception {
        UUID sheet = draftWeek(W1);
        submitService.submit(sheet);
        org.mockito.Mockito.reset(notifications);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> submitService.submit(sheet))
                .isInstanceOf(TimesheetConflictException.class);

        verify(notifications, never()).compose(any(), any(), anyMap());
    }
}
