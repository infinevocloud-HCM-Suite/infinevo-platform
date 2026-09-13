// ReminderScheduler.java
package com.phegondev.usersmanagementsystem.scheduler;

import com.phegondev.usersmanagementsystem.entity.Timesheet;
import com.phegondev.usersmanagementsystem.repository.TimesheetRepo;
import com.phegondev.usersmanagementsystem.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

import java.util.List;

@Component
public class ReminderScheduler {
    private final TimesheetRepo timesheetRepo;
    private final NotificationService notificationService;

    public ReminderScheduler(TimesheetRepo timesheetRepo, NotificationService notificationService) {
        this.timesheetRepo = timesheetRepo;
        this.notificationService = notificationService;
    }

    // Saturday EOD (11:59 PM)
    // @Scheduled(cron = "0 59 23 ? * SAT")
    public void sendFirstReminder() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
        
        List<Timesheet> unsubmitted = timesheetRepo.findByWeekStartAndStatusNot(weekStart, "SUBMITTED");
        
        unsubmitted.forEach(timesheet -> {
            String message = "First reminder: Please submit your timesheet for the week of " + 
                            weekStart + " to " + weekEnd;
            notificationService.createNotification(
                message,
                timesheet.getEmpId(),
                "system",
                "REMINDER",
                timesheet.getTimesheetId()
            );
        });
    }

    // Sunday EOD (11:59 PM)
    // @Scheduled(cron = "0 59 23 ? * SUN")
    public void sendSecondReminder() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
        
        List<Timesheet> unsubmitted = timesheetRepo.findByWeekStartAndStatusNot(weekStart, "SUBMITTED");
        
        unsubmitted.forEach(timesheet -> {
            String message = "Second reminder: Please submit your timesheet for the week of " + 
                            weekStart + " to " + weekEnd;
            notificationService.createNotification(
                message,
                timesheet.getEmpId(),
                "system",
                "REMINDER",
                timesheet.getTimesheetId()
            );
        });
    }

    // Monday 2 PM
    // @Scheduled(cron = "0 0 14 ? * MON")
    public void sendSupervisorAlert() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        
        List<Timesheet> unsubmitted = timesheetRepo.findByWeekStartAndStatusNot(weekStart, "SUBMITTED");
        
        unsubmitted.forEach(timesheet -> {
            String message = "Urgent: " + timesheet.getEmpName() + " has not submitted timesheet for week of " + 
                            weekStart;
            // Assuming supervisor ID is stored somewhere
            String supervisorId = getSupervisorId(timesheet.getEmpId());
            notificationService.createNotification(
                message,
                supervisorId,
                "system",
                "ALERT",
                timesheet.getTimesheetId()
            );
        });
    }

    // Tuesday 2 PM
    // @Scheduled(cron = "0 0 14 ? * TUE")
    public void sendAdminFirstAlert() {
        sendAdminAlert("First alert: ");
    }

    // Wednesday 2 PM
    // @Scheduled(cron = "0 0 14 ? * WED")
    public void sendAdminSecondAlert() {
        sendAdminAlert("Second alert: ");
    }

    private void sendAdminAlert(String prefix) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        
        List<Timesheet> unsubmitted = timesheetRepo.findByWeekStartAndStatusNot(weekStart, "SUBMITTED");
        
        unsubmitted.forEach(timesheet -> {
            String message = prefix + timesheet.getEmpName() + " has not submitted timesheet for week of " + 
                            weekStart;
            notificationService.createNotification(
                message,
                "admin", // or fetch all admin IDs
                "system",
                "ALERT",
                timesheet.getTimesheetId()
            );
        });
    }

    private String getSupervisorId(String empId) {
        // Implement logic to get supervisor ID
        return "supervisor1"; // Placeholder
    }
}