package com.phegondev.usersmanagementsystem.controller.timesheet;

import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.timesheet.TimesheetsNotification;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetsNotificationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timesheet-notifications")
public class TimesheetsNotificationController {
    private final TimesheetsNotificationService notificationService;

    public TimesheetsNotificationController(TimesheetsNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<TimesheetsNotification>> getUserNotifications() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getEmpId()));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<TimesheetsNotification>> getUnreadNotifications() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUnreadNotifications(user.getEmpId()));
    }

    @GetMapping("/manager/unread")
    public ResponseEntity<List<TimesheetsNotification>> getUnreadNotificationsForManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUnreadNotificationsForManager(user.getEmpId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUnreadCount(user.getEmpId()));
    }

    @GetMapping("/manager/unread-count")
    public ResponseEntity<Long> getUnreadCountForManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUnreadCountForManager(user.getEmpId()));
    }

    @PostMapping("/mark-as-read/{id}")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        notificationService.markAllAsRead(user.getEmpId());
        return ResponseEntity.ok().build();
    }
}
