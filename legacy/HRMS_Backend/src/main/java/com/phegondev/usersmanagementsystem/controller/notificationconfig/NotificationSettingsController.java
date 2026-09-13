package com.phegondev.usersmanagementsystem.controller.notificationconfig;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phegondev.usersmanagementsystem.dto.notificationconfig.NotificationSettingsDto;
import com.phegondev.usersmanagementsystem.service.notificationconfig.NotificationSettingsService;

@RestController
@RequestMapping("/api/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;

    public NotificationSettingsController(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }
    
    @PostMapping
    public ResponseEntity<String> saveSettings(@RequestBody NotificationSettingsDto dto) {
        System.out.println("Received request to save notification settings...");

        System.out.println("Received DTO: " + dto);

        try {
            // Call the service to save all settings
            settingsService.saveAll(dto);
            System.out.println("All notification settings saved successfully.");

            return ResponseEntity.ok("All notification settings saved successfully.");
        } catch (Exception e) {
            // Log the exception if anything goes wrong
            System.out.println("Error occurred while saving notification settings: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while saving notification settings.");
        }
    }
    
    @GetMapping
    public ResponseEntity<NotificationSettingsDto> getNotificationSettings() {
        System.out.println("==> Request received to fetch notification settings...");

        NotificationSettingsDto responseDto = settingsService.getAllSettings();

        if (responseDto != null) {
            System.out.println("✅ Notification settings fetched successfully.");
            System.out.println("HR Reminders: " + responseDto.getHrReminders());
            System.out.println("Supervisor Reminders: " + responseDto.getSupervisorReminders());
            System.out.println("Employee Reminders: " + responseDto.getEmployeeReminders());
            System.out.println("Approval Reminders: " + responseDto.getApprovalReminders());
            System.out.println("Escalation Settings: " + responseDto.getEscalationSettings());

            return ResponseEntity.ok(responseDto);
        } else {
            System.out.println("⚠️ No notification settings found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }



    
}
