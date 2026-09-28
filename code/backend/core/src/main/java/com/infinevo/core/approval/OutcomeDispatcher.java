package com.infinevo.core.approval;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * After-commit hook that claims an approval instance outcome atomically and invokes the consumer's
 * {@link ApprovalOutcomeHandler} exactly once (W-15.2, spec section 4 & section 6).
 */
@Component
public class OutcomeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutcomeDispatcher.class);

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalStepRepository stepRepository;
    private final List<ApprovalOutcomeHandler> handlers;
    private final TransactionTemplate requiresNewTx;

    public OutcomeDispatcher(
            ApprovalInstanceRepository instanceRepository,
            ApprovalStepRepository stepRepository,
            List<ApprovalOutcomeHandler> handlers) {
        this(instanceRepository, stepRepository, null, handlers);
    }

    @Autowired
    public OutcomeDispatcher(
            ApprovalInstanceRepository instanceRepository,
            ApprovalStepRepository stepRepository,
            @Autowired(required = false) PlatformTransactionManager transactionManager,
            @Autowired(required = false) List<ApprovalOutcomeHandler> handlers) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.handlers = handlers != null ? handlers : Collections.emptyList();
        if (transactionManager != null) {
            this.requiresNewTx = new TransactionTemplate(transactionManager);
            this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.requiresNewTx = null;
        }
    }

    /**
     * Registers the dispatch of the outcome to occur immediately after the current transaction commits.
     * If no transaction is active, dispatches synchronously.
     */
    public void dispatchAfterCommit(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        dispatch(instanceId);
                    } catch (Exception e) {
                        log.error("Failed to dispatch approval outcome for instance {}", instanceId, e);
                    }
                }
            });
        } else {
            dispatch(instanceId);
        }
    }

    /**
     * Atomically claims the outcome notification on the approval instance row and executes the matching
     * handler in a new transaction. If the handler throws, the transaction rolls back, leaving
     * outcome_notified_at null for retry.
     */
    public boolean dispatch(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        if (requiresNewTx != null) {
            return Boolean.TRUE.equals(requiresNewTx.execute(status -> doDispatch(instanceId)));
        } else {
            return doDispatch(instanceId);
        }
    }

    private boolean doDispatch(UUID instanceId) {
        int claimed = instanceRepository.claimOutcomeNotification(instanceId, Instant.now());
        if (claimed != 1) {
            log.debug("Outcome for instance {} already claimed or not eligible", instanceId);
            return false;
        }

        ApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return false;
        }

        List<ApprovalStep> steps =
                stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(instance.getTenantId(), instanceId);
        List<StepDecision> decisions = steps.stream()
                .map(s -> new StepDecision(
                        s.getId(),
                        s.getAssigneeEmployeeId(),
                        s.getDecision() != null ? s.getDecision().name() : null,
                        s.getComment(),
                        s.getApprovedAmount(),
                        s.getItemRef()))
                .toList();

        Optional<ApprovalOutcomeHandler> handler = handlers.stream()
                .filter(h -> h.flowType() == instance.getFlowType())
                .findFirst();

        if (handler.isPresent()) {
            if (instance.getStatus() == InstanceStatus.APPROVED) {
                handler.get().onApproved(instanceId, decisions);
            } else if (instance.getStatus() == InstanceStatus.REJECTED) {
                handler.get().onRejected(instanceId, decisions);
            }
        }
        return true;
    }

    /**
     * Retries any completed approval instances in the given tenant whose outcome dispatch has not yet succeeded
     * (outcome_notified_at is null).
     *
     * @param tenantId the tenant ID
     * @return the number of instances successfully dispatched
     */
    @Transactional
    public int retryPendingOutcomes(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        boolean clearTenant = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }
        try {
            List<ApprovalInstance> pending = instanceRepository.findPendingOutcomeDispatches(tenantId);
            int dispatched = 0;
            for (ApprovalInstance instance : pending) {
                try {
                    if (dispatch(instance.getId())) {
                        dispatched++;
                    }
                } catch (Exception e) {
                    log.warn(
                            "Retry outcome dispatch failed for instance {} in tenant {}",
                            instance.getId(),
                            tenantId,
                            e);
                }
            }
            return dispatched;
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }
}
