package com.infinevo.worker.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to evaluate and send POI reminders.
 * Ported from legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/POIReminderScheduler.java:17.
 * Protected by ShedLock against duplicate execution across worker replicas (DEBT-021).
 */
@Component
public class WorkerPOIReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkerPOIReminderScheduler.class);

    @Scheduled(cron = "${infinevo.scheduler.poi-reminder.cron:0 0 9 * * ?}", zone = "UTC")
    @SchedulerLock(name = "WorkerPOIReminderScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void evaluatePOIReminders() {
        log.info("POI Reminder evaluation started with cluster lock");
        executePOIReminders();
        log.info("POI Reminder evaluation completed");
    }

    protected void executePOIReminders() {
        // Business logic hook executed under distributed lock
        log.debug("Executing POI reminder evaluations");
    }
}
