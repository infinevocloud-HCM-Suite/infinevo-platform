package com.phegondev.usersmanagementsystem.config;


import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.phegondev.usersmanagementsystem.dto.notificationconfig.ApprovalReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.EmployeeReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.EscalationReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.HrReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.NotificationSettingsDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.SupervisorReminderDto;
import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;
import com.phegondev.usersmanagementsystem.service.notificationconfig.NotificationSettingsService;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Order(2)
public class NotificationSettingsSetup implements CommandLineRunner {
	
	private final NotificationSettingsService settingsService;

    public NotificationSettingsSetup(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void run(String... args) {
        System.out.println("==> Starting NotificationSettingsSetup initialization...");

        if (settingsService.isEscalationReminderPresent()) {
            System.out.println("==> Notification Settings already exist. Skipping default setup.");
            return;
        }

        System.out.println("==> No existing settings found. Creating default notification settings...");
        NotificationSettingsDto defaultSettings = createDefaultSettings();

        settingsService.saveAll(defaultSettings);

        System.out.println("==> Default Notification Settings saved successfully!");
        System.out.println("==> Final Settings: " + defaultSettings);
    }
    
    
    private NotificationSettingsDto createDefaultSettings() {
    	
    NotificationSettingsDto settings = new NotificationSettingsDto();

    // --- Approval Reminders ---
    List<ApprovalReminderDto> approvalReminders = new ArrayList<>();

    ApprovalReminderDto a1 = new ApprovalReminderDto();
   
    a1.setEnabled(false);
    a1.setDay(DayOfWeek.MONDAY);
    a1.setTime(LocalTime.of(10, 0));
    a1.setLevel(ReminderLevel.LEVEL_1);
    a1.setRecipients(Arrays.asList("pkumar1@infinevocloud.com"));
    a1.setCreatedAt(LocalDateTime.of(2025, 6, 13, 10, 0));
    a1.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 11, 0));
    approvalReminders.add(a1);

    ApprovalReminderDto a2 = new ApprovalReminderDto();
 
    a2.setEnabled(false);
    a2.setDay(DayOfWeek.MONDAY);
    a2.setTime(LocalTime.of(10, 0));
    a2.setLevel(ReminderLevel.LEVEL_2);
    a2.setRecipients(Arrays.asList("hr@infinevocloud.com"));
    a2.setCreatedAt(LocalDateTime.of(2025, 6, 13, 10, 0));
    a2.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 11, 0));
    approvalReminders.add(a2);

    ApprovalReminderDto a3 = new ApprovalReminderDto();
  
    a3.setEnabled(false);
    a3.setDay(DayOfWeek.MONDAY);
    a3.setTime(LocalTime.of(10, 0));
    a3.setLevel(ReminderLevel.LEVEL_3);
    a3.setRecipients(Arrays.asList("admin@infinevocloud.com"));
    a3.setCreatedAt(LocalDateTime.of(2025, 6, 13, 10, 0));
    a3.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 11, 0));
    approvalReminders.add(a3);

    settings.setApprovalReminders(approvalReminders);

    // --- Employee Reminders ---
    List<EmployeeReminderDto> employeeReminders = new ArrayList<>();

    EmployeeReminderDto e1 = new EmployeeReminderDto();
  
    e1.setEnabled(false);
    e1.setDay(DayOfWeek.FRIDAY);
    e1.setTime(LocalTime.of(9, 30));
    e1.setLevel(ReminderLevel.LEVEL_1);
    e1.setCreatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    e1.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 10, 0));
    employeeReminders.add(e1);

    EmployeeReminderDto e2 = new EmployeeReminderDto();
    e2.setId(2L);
    e2.setEnabled(false);
    e2.setDay(DayOfWeek.SATURDAY);
    e2.setTime(LocalTime.of(9, 30));
    e2.setLevel(ReminderLevel.LEVEL_2);
    e2.setCreatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    e2.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 10, 0));
    employeeReminders.add(e2);

    settings.setEmployeeReminders(employeeReminders);

    // --- Escalation Settings ---
    EscalationReminderDto escalation = new EscalationReminderDto();

    escalation.setEnabled(false);
    escalation.setDay(DayOfWeek.FRIDAY);
    escalation.setTime(LocalTime.of(11, 30));
    escalation.setRecipients(Arrays.asList("kmohapatra@infinevocloud.com"));
    escalation.setCreatedAt(LocalDateTime.of(2025, 6, 13, 8, 0));
    escalation.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    settings.setEscalationSettings(escalation);

 // --- HR Reminders ---
    List<HrReminderDto> hrReminders = new ArrayList<>();

    HrReminderDto hr1 = new HrReminderDto();
    hr1.setEnabled(false);
    hr1.setDay(DayOfWeek.FRIDAY);
    hr1.setTime(LocalTime.of(10, 30));
    hr1.setLevel(ReminderLevel.LEVEL_1);
    hr1.setRecipients(Arrays.asList("syadav@infinevocloud.com"));
    hr1.setCreatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    hr1.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 9, 30));
    hrReminders.add(hr1);

    HrReminderDto hr2 = new HrReminderDto();
    hr2.setEnabled(false);
    hr2.setDay(DayOfWeek.FRIDAY);
    hr2.setTime(LocalTime.of(10, 30));
    hr2.setLevel(ReminderLevel.LEVEL_2);
    hr2.setRecipients(Arrays.asList("syadav@infinevocloud.com"));
    hr2.setCreatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    hr2.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 9, 30));
    hrReminders.add(hr2);

    settings.setHrReminders(hrReminders);


    // --- Supervisor Reminders ---

    List<SupervisorReminderDto> supervisorReminders = new ArrayList<>();

    SupervisorReminderDto s1 = new SupervisorReminderDto();
    s1.setEnabled(false);
    s1.setDay(DayOfWeek.FRIDAY);
    s1.setTime(LocalTime.of(10, 30));
    s1.setLevel(ReminderLevel.LEVEL_1);
    s1.setRecipients(Arrays.asList("sahmad@infinevocloud.com"));
    s1.setCreatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    s1.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 9, 30));
    supervisorReminders.add(s1);

    SupervisorReminderDto s2 = new SupervisorReminderDto();
    s2.setEnabled(false);
    s2.setDay(DayOfWeek.FRIDAY);
    s2.setTime(LocalTime.of(10, 30));
    s2.setLevel(ReminderLevel.LEVEL_2);
    s2.setRecipients(Arrays.asList("sahmad@infinevocloud.com"));
    s2.setCreatedAt(LocalDateTime.of(2025, 6, 13, 9, 0));
    s2.setUpdatedAt(LocalDateTime.of(2025, 6, 13, 9, 30));
    supervisorReminders.add(s2);

    settings.setSupervisorReminders(supervisorReminders);
   

    
    return settings;
 }
    
 }

