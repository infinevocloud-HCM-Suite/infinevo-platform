package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class EscalationSweepTest {

    private ApprovalStepRepository stepRepository;
    private ApprovalInstanceRepository instanceRepository;
    private EscalationService escalationService;
    private OutcomeDispatcher outcomeDispatcher;
    private EscalationSweep sweep;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stepRepository = mock(ApprovalStepRepository.class);
        instanceRepository = mock(ApprovalInstanceRepository.class);
        escalationService = mock(EscalationService.class);
        outcomeDispatcher = mock(OutcomeDispatcher.class);

        sweep = new EscalationSweep(stepRepository, instanceRepository, escalationService, outcomeDispatcher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("retryPendingOutcomes is annotated with @Transactional to ensure tenant-bound queries succeed")
    void retryPendingOutcomesIsTransactional() throws NoSuchMethodException {
        Method method = OutcomeDispatcher.class.getMethod("retryPendingOutcomes", UUID.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("sweep job discovers tenants with pending outcomes, binds TenantContext, escalates and retries")
    void sweepRunsEscalationAndRetry() {
        when(stepRepository.findDistinctTenantsWithPendingSteps()).thenReturn(List.of());
        when(instanceRepository.findDistinctTenantsWithPendingOutcomes()).thenReturn(List.of(tenantId));

        when(outcomeDispatcher.retryPendingOutcomes(tenantId)).thenAnswer(inv -> {
            // Verify TenantContext is bound inside the tenant iteration
            assertThat(TenantContext.isBound()).isTrue();
            assertThat(TenantContext.require()).isEqualTo(tenantId);
            return 1;
        });

        sweep.sweep();

        verify(escalationService).escalateOverdueSteps(eq(tenantId), any(LocalDate.class));
        verify(outcomeDispatcher).retryPendingOutcomes(tenantId);
        // Verify TenantContext is cleared after sweep
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("sweep clears pre-existing tenant context before querying distinct tenants")
    void sweepClearsPreExistingTenantContextBeforeDiscovery() {
        UUID priorTenant = UUID.randomUUID();
        TenantContext.set(priorTenant);

        when(stepRepository.findDistinctTenantsWithPendingSteps()).thenAnswer(inv -> {
            // Must run unbound so cross-tenant discovery queries do not trigger auto-commit tenant binding
            assertThat(TenantContext.isBound()).isFalse();
            return List.of();
        });
        when(instanceRepository.findDistinctTenantsWithPendingOutcomes()).thenAnswer(inv -> {
            assertThat(TenantContext.isBound()).isFalse();
            return List.of(tenantId);
        });

        when(outcomeDispatcher.retryPendingOutcomes(tenantId)).thenAnswer(inv -> {
            assertThat(TenantContext.isBound()).isTrue();
            assertThat(TenantContext.require()).isEqualTo(tenantId);
            return 1;
        });

        sweep.sweep();

        verify(escalationService).escalateOverdueSteps(eq(tenantId), any(LocalDate.class));
        verify(outcomeDispatcher).retryPendingOutcomes(tenantId);
        assertThat(TenantContext.isBound()).isFalse();
    }
}
