package com.phegondev.usersmanagementsystem.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.phegondev.usersmanagementsystem.entity.notificationconfig.ApprovalReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EmployeeReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EscalationReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.HrReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.SupervisorReminder;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.ApprovalReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.EmployeeReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.EscalationReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.HrReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.SupervisorReminderRepo;
import com.phegondev.usersmanagementsystem.service.schedular.NotificationSchedularService;

@Component
public class NotificationSchedular {
	
	
	    private final ApprovalReminderRepo approvalReminderRepo;
	    private final EmployeeReminderRepo employeeReminderRepo;
	    private final EscalationReminderRepo escalationReminderRepo;
	    private final HrReminderRepo hrReminderRepo;
	    private final SupervisorReminderRepo supervisorReminderRepo;
	    private final NotificationSchedularService notificationSchedularService;
	   
	    public NotificationSchedular(
	        ApprovalReminderRepo approvalReminderRepo,
	        EmployeeReminderRepo employeeReminderRepo,
	        EscalationReminderRepo escalationReminderRepo,
	        HrReminderRepo hrReminderRepo,
	        SupervisorReminderRepo supervisorReminderRepo,
	        NotificationSchedularService notificationSchedularService
	    ) {
	        this.approvalReminderRepo = approvalReminderRepo;
	        this.employeeReminderRepo = employeeReminderRepo;
	        this.escalationReminderRepo = escalationReminderRepo;
	        this.hrReminderRepo = hrReminderRepo;
	        this.supervisorReminderRepo = supervisorReminderRepo;
	        this.notificationSchedularService = notificationSchedularService;
	    }
	
	
	
	    @Scheduled(fixedRate = 60000) // Every 60 seconds
	    public void processAllReminders() {
	        System.out.println("=== Reminder Scheduler Tick ===");

	        processEmployeeReminders();
	        processApprovalReminders();
	        processSupervisorReminders();
	        processHrReminders();
	        processEscalationReminders();

	        System.out.println("=== Reminder Scheduler Tick Completed ===");
	    }

	    private void processEmployeeReminders() {
	        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
	        DayOfWeek today = now.getDayOfWeek();
	        LocalTime time = now.toLocalTime();

	        for (EmployeeReminder reminder : employeeReminderRepo.findAllByEnabledTrue()) {
	            if (shouldRunReminder(reminder.getDay(), reminder.getTime(), reminder.getLastExecutedAt(), today, time)) {
	            	notificationSchedularService.runEmployeeReminder(reminder);
	              //  updateLastExecuted(reminder, now);
	            }
	        }
	    }

	    private void processApprovalReminders() {
	        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
	        DayOfWeek today = now.getDayOfWeek();
	        LocalTime time = now.toLocalTime();

	        for (ApprovalReminder reminder : approvalReminderRepo.findAllByEnabledTrue()) {
	            if (shouldRunReminder(reminder.getDay(), reminder.getTime(), reminder.getLastExecutedAt(), today, time)) {
	            	notificationSchedularService.runApprovalReminder(reminder);
	               // updateLastExecuted(reminder, now);
	            }
	        }
	    }

	    private void processSupervisorReminders() {
	        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
	        DayOfWeek today = now.getDayOfWeek();
	        LocalTime time = now.toLocalTime();

	        for (SupervisorReminder reminder : supervisorReminderRepo.findAllByEnabledTrue()) {
            if (shouldRunReminder(reminder.getDay(), reminder.getTime(), reminder.getLastExecutedAt(), today, time)) {
	            	notificationSchedularService.runSupervisorReminder(reminder);
	             //   updateLastExecuted(reminder, now);
	            }
	        }
	    }

	    private void processHrReminders() {
	        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
	        DayOfWeek today = now.getDayOfWeek();
	        LocalTime time = now.toLocalTime();

	        for (HrReminder reminder : hrReminderRepo.findAllByEnabledTrue()) {
	            if (shouldRunReminder(reminder.getDay(), reminder.getTime(), reminder.getLastExecutedAt(), today, time)) {
	            	notificationSchedularService.runHrReminder(reminder);
	             //   updateLastExecuted(reminder, now);
	            }
	        }
	    }

	    private void processEscalationReminders() {
	        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
	        DayOfWeek today = now.getDayOfWeek();
	        LocalTime time = now.toLocalTime();

	        EscalationReminder reminder = escalationReminderRepo.findFirstByEnabledTrue();

	        if (reminder != null) {
	            if (shouldRunReminder(reminder.getDay(), reminder.getTime(), reminder.getLastExecutedAt(), today, time)) {
	                notificationSchedularService.runEscalationReminder(reminder);
	                // updateLastExecuted(reminder, now);
	            }
	        } else {
	            System.out.println("No enabled EscalationReminder found.");
	        }
	        
	   }
	        	        	    
	    
	    
	    private boolean shouldRunReminder(DayOfWeek scheduledDay, LocalTime scheduledTime,
                LocalDateTime lastExecutedAt, DayOfWeek today, LocalTime nowTime) {
                boolean dayMatch = scheduledDay == today;
                boolean timeMatch = scheduledTime.truncatedTo(ChronoUnit.MINUTES).equals(nowTime.truncatedTo(ChronoUnit.MINUTES));
             boolean notAlreadyRun = lastExecutedAt == null ||
              !lastExecutedAt.truncatedTo(ChronoUnit.MINUTES).equals(LocalDateTime.of(LocalDate.now(), scheduledTime));
             return dayMatch && timeMatch && notAlreadyRun;
                   
	    }



}
