package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.OutcomeDispatcher;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W-40.4 §7, {@code RegularizationFlowIT}: the employee clocks in and never out, submits 09:00 to 18:00, the manager's
 * step appears, and on approval the open session is voided, one {@code REGULARIZATION} session exists,
 * {@code core.attendance} is {@code PRESENT} with {@code source = CLOCK}, and the request is {@code APPROVED}. A second
 * dispatch of the same instance changes nothing.
 */
class RegularizationFlowIT extends RegularizationItSupport {

    @Autowired
    private OutcomeDispatcher outcomeDispatcher;

    @Autowired
    private RegularizationOutcomeHandler handler;

    @Test
    @DisplayName("Forgot to clock out: submit, manager approves, the day is corrected and derived again")
    void approvedRequestCorrectsTheDay() throws Exception {
        clockIn(Instant.parse("2026-10-05T03:35:00Z"));
        assertThat(HrmsAttendanceTestSchema.getAttendance(tenant, employeeId, DAY))
                .isNull();

        JsonNode submitted = submit();
        assertThat(submitted.get("status").asText()).isEqualTo("PENDING");
        UUID requestId = UUID.fromString(submitted.get("id").asText());
        UUID instanceId = UUID.fromString(submitted.get("approvalInstanceId").asText());

        // The manager's step appears.
        ApprovalStep step = onlyStep(instanceId);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(managerId);
        assertThat(step.getDecision()).isNull();

        decide(step.getId(), "APPROVED", "Seen at the desk");

        // The open session is voided; one REGULARIZATION session holds the requested times.
        List<SessionRow> rows = sessions(DAY);
        assertThat(rows).hasSize(2);
        SessionRow original = rows.stream()
                .filter(r -> "CLOCK".equals(r.origin()))
                .findFirst()
                .orElseThrow();
        assertThat(original.voidReason()).isEqualTo("REGULARIZED");
        assertThat(original.clockOutAt()).isNull();
        SessionRow corrected = rows.stream()
                .filter(r -> "REGULARIZATION".equals(r.origin()))
                .findFirst()
                .orElseThrow();
        assertThat(corrected.voidReason()).isNull();
        assertThat(corrected.clockInAt()).isEqualTo(Instant.parse("2026-10-05T03:30:00Z"));
        assertThat(corrected.clockOutAt()).isEqualTo(Instant.parse("2026-10-05T12:30:00Z"));
        assertThat(corrected.regularizationId()).isEqualTo(requestId);

        // Nine hours is a full day by the default preference.
        HrmsAttendanceTestSchema.AttendanceRow day = HrmsAttendanceTestSchema.getAttendance(tenant, employeeId, DAY);
        assertThat(day).isNotNull();
        assertThat(day.status()).isEqualTo("PRESENT");
        assertThat(day.source()).isEqualTo("CLOCK");

        RequestRow request = request(requestId);
        assertThat(request.status()).isEqualTo("APPROVED");
        assertThat(request.decidedAt()).isNotNull();
        assertThat(request.decidedBy()).isEqualTo(managerId);
        assertThat(request.decisionComment()).isEqualTo("Seen at the desk");

        // A second dispatch of the same instance changes nothing: the claim is taken, and the handler itself
        // returns early for a request that is no longer PENDING.
        TenantContext.set(tenant);
        try {
            assertThat(outcomeDispatcher.dispatch(instanceId)).isFalse();
            handler.onApproved(
                    instanceId, List.of(new StepDecision(step.getId(), managerId, "APPROVED", "Seen at the desk")));
        } finally {
            TenantContext.clear();
        }
        assertThat(sessions(DAY)).hasSize(2);
        assertThat(sessions(DAY).stream().filter(r -> "REGULARIZATION".equals(r.origin())))
                .hasSize(1);
        assertThat(request(requestId).status()).isEqualTo("APPROVED");

        // The employee reads it as theirs; HR reads it with the tenant's.
        mvc.perform(authed(
                        get("/api/v1/hrms/attendance/regularizations/mine?from=2026-10-01&to=2026-10-31"), employeeSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
        mvc.perform(authed(
                        get("/api/v1/hrms/attendance/regularizations?from=2026-10-01&to=2026-10-31&status=APPROVED"
                                + "&employeeId=" + employeeId),
                        hrSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(requestId.toString()));
    }

    @Test
    @DisplayName("A second pending request for the same date is 409, and none is saved")
    void secondPendingIsConflict() throws Exception {
        submit();
        mvc.perform(authed(post("/api/v1/hrms/attendance/regularizations"), employeeSub)
                        .content(body(DAY, IN_AT, OUT_AT, "Again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("No active REGULARIZATION definition: the submit fails and the request row is rolled back")
    void noActiveDefinitionRollsBack() throws Exception {
        com.infinevo.hrms.project.HrmsProjectTestSchema.update(
                "UPDATE core.approval_definition SET is_active = false WHERE tenant_id = ? AND flow_type = 'REGULARIZATION'",
                tenant);
        mvc.perform(authed(post("/api/v1/hrms/attendance/regularizations"), employeeSub)
                        .content(body(DAY, IN_AT, OUT_AT, "No flow")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(requestCount()).isZero();
    }

    @Test
    @DisplayName("A rule refusal is 400 VALIDATION_FAILED and nothing is saved")
    void ruleRefusalIsValidationFailed() throws Exception {
        mvc.perform(authed(post("/api/v1/hrms/attendance/regularizations"), employeeSub)
                        .content(body(DAY, OUT_AT, IN_AT, "Backwards")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(requestCount()).isZero();
    }
}
