package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.leave.HalfDayPeriod;
import com.infinevo.core.leave.LeaveApplyCheck;
import com.infinevo.core.leave.LeaveApplyRequest;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import com.infinevo.core.leave.LeaveUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** W-114.8 spec, Tests (C1, C2): {@link LeaveApplyExecutor} - the card from the stored payload, and what confirm sends. */
class LeaveApplyExecutorTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 12, 4);

    private LeaveRequestService leaveRequestService;
    private LeaveTypeRepository leaveTypeRepository;
    private LeaveApplyExecutor executor;
    private AiToolContext ctx;

    @BeforeEach
    void setUp() {
        leaveRequestService = mock(LeaveRequestService.class);
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        LeaveType type = new LeaveType(
                tenantId, "CL", "Casual Leave", true, LeaveUnit.DAYS, true, LocalDate.of(2025, 1, 1), null, true);
        type.setId(leaveTypeId);
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        executor = new LeaveApplyExecutor(leaveRequestService, leaveTypeRepository);
        ctx = new AiToolContext(tenantId, userId, employeeId);
    }

    private ObjectNode payload(LocalDate from, LocalDate to, boolean halfDay, HalfDayPeriod period, String reason) {
        LeaveApplyRequest request = new LeaveApplyRequest(leaveTypeId, from, to, halfDay, period, reason, null, true);
        return LeaveApplyExecutor.payload(
                request, new LeaveApplyCheck(leaveTypeId, "Casual Leave", halfDay, new BigDecimal("1.00")));
    }

    private static String value(AiActionDescription d, String label) {
        return d.fields().stream()
                .filter(f -> f.label().equals(label))
                .map(AiActionField::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + label));
    }

    @Test
    @DisplayName("C2: every payload value the action sends appears as a field")
    void everyPayloadValueIsAField() {
        ObjectNode payload = payload(day, day, true, HalfDayPeriod.SECOND, "Doctor's appointment");

        AiActionDescription d = executor.describe(ctx, payload);

        assertThat(d.fields())
                .extracting(AiActionField::label)
                .containsExactly("Leave type", "From", "To", "Half day", "Days", "Reason", "Send for approval");
        assertThat(value(d, "Leave type")).isEqualTo("Casual Leave");
        assertThat(value(d, "From")).isEqualTo("2026-12-04");
        assertThat(value(d, "To")).isEqualTo("2026-12-04");
        assertThat(value(d, "Half day")).isEqualTo("Yes, second half");
        assertThat(value(d, "Days")).isEqualTo("1.00");
        assertThat(value(d, "Reason")).isEqualTo("Doctor's appointment");
        assertThat(value(d, "Send for approval")).isEqualTo("Yes");
        assertThat(d.summary()).contains("Casual Leave").contains("2026-12-04").contains("half day");

        // Each payload key is shown by one of the fields above.
        Iterator<String> keys = payload.fieldNames();
        List<String> shown = List.of(
                "leaveTypeId", "fromDate", "toDate", "isHalfDay", "halfDayPeriod", "reason", "submit", "workingDays");
        while (keys.hasNext()) {
            assertThat(shown).contains(keys.next());
        }
    }

    @Test
    @DisplayName("C2: a 500-character reason comes back whole")
    void longReasonWhole() {
        String reason = "r".repeat(499) + "!";
        AiActionDescription d = executor.describe(ctx, payload(day, day.plusDays(2), false, null, reason));

        assertThat(value(d, "Reason")).hasSize(500).isEqualTo(reason);
        assertThat(value(d, "Half day")).isEqualTo("No");
    }

    @Test
    @DisplayName(
            "C1: the fields are the same before and after Confirm; describe reads only the payload and the type name")
    void sameBeforeAndAfterConfirm() {
        ObjectNode payload = payload(day, day, false, null, "Family");
        LeaveRequestResponse created = mock(LeaveRequestResponse.class);
        UUID requestId = UUID.randomUUID();
        when(created.id()).thenReturn(requestId);
        when(created.status()).thenReturn(LeaveRequestStatus.PENDING);
        when(leaveRequestService.createRequest(eq(tenantId), eq(employeeId), any()))
                .thenReturn(created);

        AiActionDescription before = executor.describe(ctx, payload);
        verifyNoInteractions(leaveRequestService);
        JsonNode result = executor.execute(ctx, payload);
        AiActionDescription after = executor.describe(ctx, payload);

        assertThat(after).isEqualTo(before);
        assertThat(result.path("leaveRequestId").asText()).isEqualTo(requestId.toString());
        assertThat(result.path("status").asText()).isEqualTo("PENDING");
        verify(leaveTypeRepository, org.mockito.Mockito.times(2)).findByTenantIdAndId(tenantId, leaveTypeId);
        verifyNoMoreInteractions(leaveTypeRepository);
    }

    @Test
    @DisplayName("execute sends the stored payload: the caller's own leave, submitted, no documents")
    void executeSendsStoredPayload() {
        ObjectNode payload = payload(day, day, true, HalfDayPeriod.FIRST, "Errand");
        LeaveRequestResponse created = mock(LeaveRequestResponse.class);
        when(leaveRequestService.createRequest(any(), any(), any())).thenReturn(created);

        executor.execute(ctx, payload);

        ArgumentCaptor<LeaveApplyRequest> sent = ArgumentCaptor.forClass(LeaveApplyRequest.class);
        verify(leaveRequestService).createRequest(eq(tenantId), eq(employeeId), sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(
                        new LeaveApplyRequest(leaveTypeId, day, day, true, HalfDayPeriod.FIRST, "Errand", null, true));
    }

    @Test
    @DisplayName("a leave type that no longer exists is named as such, not left blank")
    void unknownType() {
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.empty());

        AiActionDescription d = executor.describe(ctx, payload(day, day, false, null, null));

        assertThat(value(d, "Leave type")).isEqualTo(LeaveApplyExecutor.UNKNOWN_TYPE);
        assertThat(value(d, "Reason")).isEqualTo(LeaveApplyExecutor.NO_REASON);
    }
}
