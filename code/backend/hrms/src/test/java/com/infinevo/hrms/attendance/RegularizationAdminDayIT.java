package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-40.4 §7, {@code RegularizationAdminDayIT}: a submit for a day an administrator set is {@code 409}; a day turned
 * {@code ADMIN} after submit stays {@code ADMIN} after approval, while the sessions are still corrected for the record.
 */
class RegularizationAdminDayIT extends RegularizationItSupport {

    @Test
    @DisplayName("Submit for an ADMIN day is 409 CONFLICT and nothing is saved")
    void adminDayRefusedAtSubmit() throws Exception {
        HrmsAttendanceTestSchema.insertAdminAttendance(tenant, employeeId, DAY, "ABSENT");

        mvc.perform(authed(post("/api/v1/hrms/attendance/regularizations"), employeeSub)
                        .content(body(DAY, IN_AT, OUT_AT, "Forgot to clock out")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrator")));
        assertThat(requestCount()).isZero();
    }

    @Test
    @DisplayName("A day turned ADMIN after submit stays ADMIN after approval; the sessions are corrected anyway")
    void adminAfterSubmitStandsAtApproval() throws Exception {
        clockIn(Instant.parse("2026-10-05T03:30:00Z"));
        JsonNode submitted = submit();
        UUID requestId = UUID.fromString(submitted.get("id").asText());
        UUID instanceId = UUID.fromString(submitted.get("approvalInstanceId").asText());

        HrmsAttendanceTestSchema.insertAdminAttendance(tenant, employeeId, DAY, "ABSENT");

        decide(onlyStep(instanceId).getId(), "APPROVED", null);

        HrmsAttendanceTestSchema.AttendanceRow day = HrmsAttendanceTestSchema.getAttendance(tenant, employeeId, DAY);
        assertThat(day.status()).isEqualTo("ABSENT");
        assertThat(day.source()).isEqualTo("ADMIN");

        assertThat(sessions(DAY))
                .anySatisfy(r -> {
                    assertThat(r.origin()).isEqualTo("CLOCK");
                    assertThat(r.voidReason()).isEqualTo("REGULARIZED");
                })
                .anySatisfy(r -> {
                    assertThat(r.origin()).isEqualTo("REGULARIZATION");
                    assertThat(r.voidReason()).isNull();
                    assertThat(r.regularizationId()).isEqualTo(requestId);
                });
        assertThat(request(requestId).status()).isEqualTo("APPROVED");
    }
}
