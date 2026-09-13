package com.itsdev.payroll.scheduler;

import com.itsdev.payroll.service.claimsanddeclarations.POIReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class POIReminderScheduler {

    private final POIReminderService poiReminderService;

    // Runs once daily at 9 AM server time
    @Scheduled(cron = "0 0 9 * * ?", zone = "UTC")
    public void evaluatePOIReminders() {
        log.info("POI Reminder evaluation started");
        poiReminderService.processReminders();
        log.info("POI Reminder evaluation completed");
    }
}
