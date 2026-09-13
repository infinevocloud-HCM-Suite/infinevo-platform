// NotificationController.java
package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.entity.Notification;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final UsersRepo userRepository;

    public NotificationController(NotificationService notificationService, UsersRepo userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getUserNotifications() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getEmpId()));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUnreadNotifications(user.getEmpId()));
    }

    @GetMapping("/admin/unread")
    public ResponseEntity<List<Notification>> getUnreadNotificationsForAdmin() {
        System.out.println("GET /admin/unread called");
        List<Notification> unreadNotifications = notificationService.getUnreadNotificationsForAdmin();
        System.out.println("Unread notifications retrieved: " + unreadNotifications.size());
        return ResponseEntity.ok(unreadNotifications);
    }
    
    @GetMapping("/admin/unread-count")
    public ResponseEntity<Long> getUnreadCountForAdmin() {
        System.out.println("GET /admin/unread-count called");
        long count = notificationService.getUnreadCountForAdmin();
        System.out.println("Unread notifications count: " + count);
        return ResponseEntity.ok(count);
    }
    

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getUnreadCount(user.getEmpId()));
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