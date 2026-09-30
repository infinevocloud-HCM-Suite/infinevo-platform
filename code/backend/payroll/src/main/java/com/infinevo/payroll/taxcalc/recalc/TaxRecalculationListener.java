package com.infinevo.payroll.taxcalc.recalc;

import com.infinevo.payroll.taxcalc.recalc.event.DeclarationSubmittedEvent;
import com.infinevo.payroll.taxcalc.recalc.event.ProofVerifiedEvent;
import com.infinevo.payroll.taxcalc.recalc.event.SalaryVersionChangedEvent;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asynchronous after-commit listener triggering tax recalculations on declaration and salary events (W-33.3).
 *
 * <p>Each handler saves and restores the previous {@link TenantContext} so the listener is safe
 * to run on either a dedicated async thread (production) or the caller's thread
 * ({@code SyncTaskExecutor} in integration tests).
 */
@Component
public class TaxRecalculationListener {

    private static final Logger log = LoggerFactory.getLogger(TaxRecalculationListener.class);

    private final TaxRecalculationService service;

    public TaxRecalculationListener(TaxRecalculationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taxRecalc")
    public void onDeclarationSubmitted(DeclarationSubmittedEvent event) {
        if (event == null) {
            return;
        }
        UUID previous = TenantContext.current().orElse(null);
        TenantContext.set(event.tenantId());
        try {
            service.recalculate(event.employeeId(), event.financialYear(), TaxTrigger.DECLARATION_SUBMITTED);
        } catch (Exception e) {
            log.error(
                    "recalc failed [DECLARATION_SUBMITTED] tenant={} employee={} fy={}: {}",
                    event.tenantId(),
                    event.employeeId(),
                    event.financialYear(),
                    e.getMessage(),
                    e);
        } finally {
            restoreTenant(previous);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taxRecalc")
    public void onSalaryVersionChanged(SalaryVersionChangedEvent event) {
        if (event == null) {
            return;
        }
        UUID previous = TenantContext.current().orElse(null);
        for (String fy : FinancialYear.allFrom(event.effectiveFrom())) {
            TenantContext.set(event.tenantId());
            try {
                service.recalculate(event.employeeId(), fy, TaxTrigger.SALARY_REVISION);
            } catch (Exception e) {
                log.error(
                        "recalc failed [SALARY_REVISION] tenant={} employee={} fy={}: {}",
                        event.tenantId(),
                        event.employeeId(),
                        fy,
                        e.getMessage(),
                        e);
            }
        }
        restoreTenant(previous);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taxRecalc")
    public void onProofVerified(ProofVerifiedEvent event) {
        if (event == null) {
            return;
        }
        UUID previous = TenantContext.current().orElse(null);
        TenantContext.set(event.tenantId());
        try {
            service.recalculate(event.employeeId(), event.financialYear(), TaxTrigger.PROOF_VERIFIED);
        } catch (Exception e) {
            log.error(
                    "recalc failed [PROOF_VERIFIED] tenant={} employee={} fy={}: {}",
                    event.tenantId(),
                    event.employeeId(),
                    event.financialYear(),
                    e.getMessage(),
                    e);
        } finally {
            restoreTenant(previous);
        }
    }

    private static void restoreTenant(UUID previous) {
        if (previous != null) {
            TenantContext.set(previous);
        } else {
            TenantContext.clear();
        }
    }
}
