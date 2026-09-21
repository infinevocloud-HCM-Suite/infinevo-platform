package com.infinevo.worker.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to auto-lock expired IT declarations.
 * Ported from legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/ITDeclarationAutoLockScheduler.java:24.
 * Protected by ShedLock against duplicate execution across worker replicas (DEBT-021).
 */
@Component
public class WorkerAutoLockScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkerAutoLockScheduler.class);

    @Scheduled(cron = "${infinevo.scheduler.it-declaration-autolock.cron:0 0 0 * * *}", zone = "UTC")
    @SchedulerLock(name = "WorkerAutoLockScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void autoLockExpiredDeclarations() {
        log.info("IT Declaration auto-lock job started with cluster lock");
        executeAutoLock();
        log.info("IT Declaration auto-lock job finished");
    }

    protected void executeAutoLock() {
        // Business logic hook executed under distributed lock
        log.debug("Executing declaration auto-lock evaluation");
    }
}
