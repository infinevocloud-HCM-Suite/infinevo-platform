package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-40.4 §7, {@code RegularizationRejectIT}: on reject the request is {@code REJECTED} with the manager's comment; the
 * sessions and {@code core.attendance} are unchanged.
 */
class RegularizationRejectIT extends RegularizationItSupport {

    @Test
    @DisplayName("Rejected: the request keeps the comment, the sessions and the day are untouched")
    void rejectedRequestChangesNothingElse() throws Exception {
        // A short clocked day: 09:00 to 11:00 IST, derived ABSENT.
        clockIn(Instant.parse("2026-10-05T03:30:00Z"));
        clockOut(Instant.parse("2026-10-05T05:30:00Z"));
        List<SessionRow> before = sessions(DAY);
        assertThat(before).hasSize(1);
        HrmsAttendanceTestSchema.AttendanceRow dayBefore =
                HrmsAttendanceTestSchema.getAttendance(tenant, employeeId, DAY);
        assertThat(dayBefore.status()).isEqualTo("ABSENT");
        assertThat(dayBefore.source()).isEqualTo("CLOCK");

        JsonNode submitted = submit();
        UUID requestId = UUID.fromString(submitted.get("id").asText());
        UUID instanceId = UUID.fromString(submitted.get("approvalInstanceId").asText());

        decide(onlyStep(instanceId).getId(), "REJECTED", "No record of this");

        RequestRow request = request(requestId);
        assertThat(request.status()).isEqualTo("REJECTED");
        assertThat(request.decisionComment()).isEqualTo("No record of this");
        assertThat(request.decidedBy()).isEqualTo(managerId);
        assertThat(request.decidedAt()).isNotNull();

        assertThat(sessions(DAY)).isEqualTo(before);
        assertThat(HrmsAttendanceTestSchema.getAttendance(tenant, employeeId, DAY))
                .isEqualTo(dayBefore);
    }
}
