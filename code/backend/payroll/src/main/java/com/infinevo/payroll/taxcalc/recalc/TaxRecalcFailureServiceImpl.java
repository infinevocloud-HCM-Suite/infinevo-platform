package com.infinevo.payroll.taxcalc.recalc;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation of {@link TaxRecalcFailureService} (D-147).
 *
 * <p>Transactions are opened here with {@link TransactionTemplate}, not {@code @Transactional}: the failure
 * row must be written in a new transaction after the recalculation's own has rolled back, and
 * {@link #retry} calls {@link #recordFailure} on {@code this}, where a proxy annotation would not apply.
 * {@link #retry} itself runs outside any transaction so the recalculation it starts commits or rolls back
 * on its own.
 */
@Service
public class TaxRecalcFailureServiceImpl implements TaxRecalcFailureService {

    private static final Logger log = LoggerFactory.getLogger(TaxRecalcFailureServiceImpl.class);

    private final TaxRecalcFailureRepository repository;
    private final TaxRecalculationService recalculationService;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readTransaction;

    public TaxRecalcFailureServiceImpl(
            TaxRecalcFailureRepository repository,
            TaxRecalculationService recalculationService,
            PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.recalculationService =
                Objects.requireNonNull(recalculationService, "recalculationService must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public void recordFailure(
            UUID tenantId, UUID employeeId, String financialYear, TaxTrigger trigger, Throwable error) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        String message = describe(error);
        UUID previous = TenantContext.current().orElse(null);
        TenantContext.set(tenantId);
        try {
            newTransaction.executeWithoutResult(status -> {
                Instant now = Instant.now();
                Optional<TaxRecalcFailure> open = repository.findOpenForUpdate(tenantId, employeeId, financialYear);
                if (open.isPresent()) {
                    open.get().failedAgain(trigger, message, now);
                    repository.saveAndFlush(open.get());
                } else {
                    repository.saveAndFlush(
                            new TaxRecalcFailure(tenantId, employeeId, financialYear, trigger, message, now));
                }
            });
        } catch (DataIntegrityViolationException race) {
            // Two failures for the same employee and year at once: the other one's row stands.
            log.warn(
                    "tax recalc failure already recorded tenant={} employee={} fy={}",
                    tenantId,
                    employeeId,
                    financialYear);
        } catch (RuntimeException e) {
            log.error(
                    "could not record tax recalc failure tenant={} employee={} fy={}: {}",
                    tenantId,
                    employeeId,
                    financialYear,
                    e.getMessage(),
                    e);
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    @Override
    public List<TaxRecalcFailureResponse> open(String financialYear) {
        UUID tenantId = TenantContext.require();
        String fy = financialYear == null || financialYear.isBlank()
                ? null
                : FinancialYear.parse(financialYear).label();
        List<TaxRecalcFailure> rows = readTransaction.execute(
                status -> fy == null ? repository.findOpen(tenantId) : repository.findOpenForYear(tenantId, fy));
        return rows == null
                ? List.of()
                : rows.stream().map(TaxRecalcFailureResponse::of).toList();
    }

    @Override
    public TaxComputationRecord retry(UUID failureId, UUID officerId) {
        Objects.requireNonNull(failureId, "failureId must not be null");
        UUID tenantId = TenantContext.require();
        TaxRecalcFailure failure = readTransaction
                .execute(status -> repository.findOpenById(tenantId, failureId))
                .orElseThrow(() -> new TaxRecalcFailureNotFoundException(failureId));
        try {
            // Closes the failure in the same transaction when it succeeds (TaxRecalculationServiceImpl).
            return recalculationService.recalculate(
                    failure.getEmployeeId(), failure.getFinancialYear(), failure.getTrigger(), officerId);
        } catch (RuntimeException e) {
            recordFailure(tenantId, failure.getEmployeeId(), failure.getFinancialYear(), failure.getTrigger(), e);
            throw new TaxRecalcRetryFailedException("Tax recalculation failed again: " + describe(e), e);
        }
    }

    /** The exception and its causes, one message each, so the officer sees the root reason. */
    static String describe(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        StringBuilder sb = new StringBuilder();
        Throwable t = error;
        int depth = 0;
        while (t != null && depth < 5) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                sb.append(": ").append(t.getMessage());
            }
            t = t.getCause() == t ? null : t.getCause();
            depth++;
        }
        return TaxRecalcFailure.trim(sb.toString());
    }
}
