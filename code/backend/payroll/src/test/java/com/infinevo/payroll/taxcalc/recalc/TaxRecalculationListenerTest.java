package com.infinevo.payroll.taxcalc.recalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infinevo.payroll.taxcalc.recalc.event.DeclarationSubmittedEvent;
import com.infinevo.payroll.taxcalc.recalc.event.ProofVerifiedEvent;
import com.infinevo.payroll.taxcalc.recalc.event.SalaryVersionChangedEvent;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TaxRecalculationListener} (W-33.3).
 */
@ExtendWith(MockitoExtension.class)
class TaxRecalculationListenerTest {

    @Mock
    private TaxRecalculationService service;

    @Mock
    private TaxRecalcFailureService failureService;

    private TaxRecalculationListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new TaxRecalculationListener(service, failureService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("onDeclarationSubmitted binds tenant and triggers recalculation with DECLARATION_SUBMITTED")
    void onDeclarationSubmittedInvokesRecalculate() {
        DeclarationSubmittedEvent event =
                new DeclarationSubmittedEvent(tenantId, employeeId, declarationId, "2025-2026");

        listener.onDeclarationSubmitted(event);

        verify(service).recalculate(employeeId, "2025-2026", TaxTrigger.DECLARATION_SUBMITTED);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("onSalaryVersionChanged recalculates all impacted FYs and clears tenant context")
    void onSalaryVersionChangedRecalculatesFys() {
        LocalDate effectiveFrom = LocalDate.now();
        SalaryVersionChangedEvent event = new SalaryVersionChangedEvent(tenantId, employeeId, effectiveFrom);

        listener.onSalaryVersionChanged(event);

        verify(service)
                .recalculate(
                        employeeId,
                        com.infinevo.payroll.taxdeclaration.FinancialYear.of(effectiveFrom)
                                .label(),
                        TaxTrigger.SALARY_REVISION);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("onSalaryVersionChanged swallows exceptions and does not propagate failure")
    void onSalaryVersionChangedSwallowsException() {
        LocalDate effectiveFrom = LocalDate.now();
        SalaryVersionChangedEvent event = new SalaryVersionChangedEvent(tenantId, employeeId, effectiveFrom);

        doThrow(new RuntimeException("Recalc engine boom"))
                .when(service)
                .recalculate(
                        employeeId,
                        com.infinevo.payroll.taxdeclaration.FinancialYear.of(effectiveFrom)
                                .label(),
                        TaxTrigger.SALARY_REVISION);

        // Should not throw
        listener.onSalaryVersionChanged(event);

        assertThat(TenantContext.isBound()).isFalse();
        // D-147: the failure is recorded for the officer, not only logged.
        verify(failureService)
                .recordFailure(
                        eq(tenantId),
                        eq(employeeId),
                        eq(com.infinevo.payroll.taxdeclaration.FinancialYear.of(effectiveFrom)
                                .label()),
                        eq(TaxTrigger.SALARY_REVISION),
                        any(RuntimeException.class));
    }

    @Test
    @DisplayName("D-147: a failed DECLARATION_SUBMITTED recalculation is recorded and the tenant is restored")
    void onDeclarationSubmittedRecordsFailure() {
        DeclarationSubmittedEvent event =
                new DeclarationSubmittedEvent(tenantId, employeeId, declarationId, "2025-2026");
        RuntimeException boom = new RuntimeException("Annual taxable income cannot exceed annual gross");
        doThrow(boom).when(service).recalculate(employeeId, "2025-2026", TaxTrigger.DECLARATION_SUBMITTED);

        listener.onDeclarationSubmitted(event);

        verify(failureService).recordFailure(tenantId, employeeId, "2025-2026", TaxTrigger.DECLARATION_SUBMITTED, boom);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("D-147: a successful recalculation records no failure")
    void successRecordsNoFailure() {
        listener.onProofVerified(new ProofVerifiedEvent(tenantId, employeeId, declarationId, "2025-2026"));

        verify(failureService, never()).recordFailure(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onProofVerified binds tenant and triggers recalculation with PROOF_VERIFIED")
    void onProofVerifiedInvokesRecalculate() {
        ProofVerifiedEvent event = new ProofVerifiedEvent(tenantId, employeeId, declarationId, "2025-2026");

        listener.onProofVerified(event);

        verify(service).recalculate(employeeId, "2025-2026", TaxTrigger.PROOF_VERIFIED);
        assertThat(TenantContext.isBound()).isFalse();
    }
}
