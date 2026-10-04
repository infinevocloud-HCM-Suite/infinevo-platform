package com.infinevo.hrms.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.overtime.OvertimeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** W-40.6 §7, {@code OvertimeRequestOutcomeHandlerTest}. */
class OvertimeRequestOutcomeHandlerTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    private ApprovalInstanceRepository instances;
    private OvertimeService overtimeService;
    private OvertimeRequestOutcomeHandler handler;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        instances = mock(ApprovalInstanceRepository.class);
        overtimeService = mock(OvertimeService.class);
        handler = new OvertimeRequestOutcomeHandler(instances, overtimeService);

        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getTenantId()).thenReturn(tenantId);
        when(instance.getSubjectId()).thenReturn(requestId);
        when(instances.findById(instanceId)).thenReturn(Optional.of(instance));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("flowType is OVERTIME")
    void flowType() {
        assertThat(handler.flowType()).isEqualTo(ApprovalFlowType.OVERTIME);
    }

    @Test
    @DisplayName("Approved calls approve with the subject id, never reject")
    void approvedCallsApprove() {
        handler.onApproved(instanceId, List.of());

        verify(overtimeService).approve(requestId);
        verify(overtimeService, never()).reject(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Rejected calls reject with the subject id, never approve")
    void rejectedCallsReject() {
        handler.onRejected(instanceId, List.of());

        verify(overtimeService).reject(requestId);
        verify(overtimeService, never()).approve(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("With no tenant bound, the instance's tenant is bound for the call and cleared after")
    void bindsTenantFromInstanceAndClears() {
        AtomicReference<UUID> seen = new AtomicReference<>();
        when(overtimeService.approve(requestId)).thenAnswer(inv -> {
            seen.set(TenantContext.require());
            return null;
        });

        handler.onApproved(instanceId, List.of());

        assertThat(seen.get()).isEqualTo(tenantId);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("The tenant is cleared after even when approve throws")
    void clearsTenantWhenApproveThrows() {
        when(overtimeService.approve(requestId)).thenThrow(new IllegalStateException("cancelled"));

        assertThatThrownBy(() -> handler.onApproved(instanceId, List.of())).isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("A tenant already bound is used and left bound")
    void leavesBoundTenantAlone() {
        UUID bound = UUID.randomUUID();
        TenantContext.set(bound);
        AtomicReference<UUID> seen = new AtomicReference<>();
        when(overtimeService.reject(requestId)).thenAnswer(inv -> {
            seen.set(TenantContext.require());
            return null;
        });

        handler.onRejected(instanceId, List.of());

        assertThat(seen.get()).isEqualTo(bound);
        assertThat(TenantContext.require()).isEqualTo(bound);
    }

    @Test
    @DisplayName("An unknown instance is refused before anything is called")
    void unknownInstance() {
        UUID unknown = UUID.randomUUID();
        when(instances.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.onApproved(unknown, List.of())).isInstanceOf(NoSuchElementException.class);
        verify(overtimeService, never()).approve(ArgumentMatchers.any());
    }
}
