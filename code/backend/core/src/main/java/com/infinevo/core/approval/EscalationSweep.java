package com.infinevo.core.approval;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to sweep and escalate overdue approval steps and retry failed outcome notifications
 * across tenants (W-15.2, W-15.3, spec section 4).
 * Protected by ShedLock against duplicate execution across worker replicas (DEBT-021, W-52).
 */
@Component
public class EscalationSweep {

    private static final Logger log = LoggerFactory.getLogger(EscalationSweep.class);

    private final ApprovalStepRepository stepRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final EscalationService escalationService;
    private final OutcomeDispatcher outcomeDispatcher;

    public EscalationSweep(ApprovalStepRepository stepRepository, EscalationService escalationService) {
        this(stepRepository, null, escalationService, null);
    }

    @Autowired
    public EscalationSweep(
            ApprovalStepRepository stepRepository,
            @Autowired(required = false) ApprovalInstanceRepository instanceRepository,
            EscalationService escalationService,
            @Autowired(required = false) OutcomeDispatcher outcomeDispatcher) {
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.instanceRepository = instanceRepository;
        this.escalationService = Objects.requireNonNull(escalationService, "escalationService must not be null");
        this.outcomeDispatcher = outcomeDispatcher;
    }

    @Scheduled(cron = "${infinevo.scheduler.approval-escalation.cron:0 */15 * * * *}", zone = "UTC")
    @SchedulerLock(name = "ApprovalEscalationSweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void sweep() {
        log.info("Starting approval escalation sweep");
        executeSweep();
        log.info("Finished approval escalation sweep");
    }

    protected void executeSweep() {
        TenantContext.clear();
        Set<UUID> tenants = new LinkedHashSet<>(stepRepository.findDistinctTenantsWithPendingSteps());
        if (instanceRepository != null) {
            tenants.addAll(instanceRepository.findDistinctTenantsWithPendingOutcomes());
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (UUID tenantId : tenants) {
            try {
                TenantContext.set(tenantId);
                int escalated = escalationService.escalateOverdueSteps(tenantId, today);
                if (escalated > 0) {
                    log.info("Escalated {} overdue approval steps for tenant {}", escalated, tenantId);
                }
                if (outcomeDispatcher != null) {
                    int retried = outcomeDispatcher.retryPendingOutcomes(tenantId);
                    if (retried > 0) {
                        log.info("Retried {} pending approval outcomes for tenant {}", retried, tenantId);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process sweep for tenant {}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
