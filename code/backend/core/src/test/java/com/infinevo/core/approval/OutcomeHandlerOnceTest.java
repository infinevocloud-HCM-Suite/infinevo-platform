package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-15.2, spec section 7 — {@code OutcomeHandlerOnceTest}.
 *
 * <p>Proves that the outcome handler fires once on completion, and not again on a repeated call;
 * and a handler that throws leaves the row eligible for retry.
 */
class OutcomeHandlerOnceTest {

    private ApprovalInstanceRepository instanceRepository;
    private ApprovalStepRepository stepRepository;
    private ApprovalOutcomeHandler outcomeHandler;
    private OutcomeDispatcher dispatcher;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private ApprovalInstance instance;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(ApprovalInstanceRepository.class);
        stepRepository = mock(ApprovalStepRepository.class);
        outcomeHandler = mock(ApprovalOutcomeHandler.class);
        when(outcomeHandler.flowType()).thenReturn(ApprovalFlowType.REGULARIZATION);

        dispatcher = new OutcomeDispatcher(instanceRepository, stepRepository, List.of(outcomeHandler));

        instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.REGULARIZATION,
                UUID.randomUUID(),
                new SubjectRef("core.test", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setId(instanceId);
        instance.setStatus(InstanceStatus.APPROVED);

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Handler fires once on successful claim, and not again on repeated call")
    void handlerFiresOnceNotRepeated() {
        AtomicBoolean claimed = new AtomicBoolean(false);
        when(instanceRepository.claimOutcomeNotification(eq(instanceId), any())).thenAnswer(inv -> {
            if (claimed.compareAndSet(false, true)) {
                return 1;
            }
            return 0;
        });

        // First call: claims and fires
        boolean first = dispatcher.dispatch(instanceId);
        assertThat(first).isTrue();
        verify(outcomeHandler, times(1)).onApproved(eq(instanceId), any());

        // Second call: already claimed, does not fire again
        boolean second = dispatcher.dispatch(instanceId);
        assertThat(second).isFalse();
        verify(outcomeHandler, times(1)).onApproved(eq(instanceId), any());
    }

    @Test
    @DisplayName("Handler that throws allows retry on subsequent attempt")
    void throwingHandlerAllowsRetry() {
        AtomicBoolean claimed = new AtomicBoolean(false);
        when(instanceRepository.claimOutcomeNotification(eq(instanceId), any())).thenAnswer(inv -> {
            if (claimed.compareAndSet(false, true)) {
                return 1;
            }
            return 0;
        });

        // Configure handler to throw on first invocation
        when(outcomeHandler.flowType()).thenReturn(ApprovalFlowType.REGULARIZATION);
        org.mockito.Mockito.doThrow(new RuntimeException("Side effect failed"))
                .doNothing()
                .when(outcomeHandler)
                .onApproved(eq(instanceId), any());

        // First call throws
        assertThatThrownBy(() -> dispatcher.dispatch(instanceId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Side effect failed");

        // Simulate transactional rollback: claimed resets to false
        claimed.set(false);

        // Second call retries and succeeds
        boolean retried = dispatcher.dispatch(instanceId);
        assertThat(retried).isTrue();
        verify(outcomeHandler, times(2)).onApproved(eq(instanceId), any());
    }
}
