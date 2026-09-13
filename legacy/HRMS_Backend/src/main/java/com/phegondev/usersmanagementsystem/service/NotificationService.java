// NotificationService.java
package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.entity.Notification;
import com.phegondev.usersmanagementsystem.repository.NotificationRepo;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepo notificationRepo;
    private final UsersRepo userRepository;

    public NotificationService(NotificationRepo notificationRepo, UsersRepo userRepository) {
        this.notificationRepo = notificationRepo;
        this.userRepository = userRepository;
    }

    public Notification createNotification(String message, String recipientId, String senderId, 
                                         String type, String timesheetId) {
        Notification notification = new Notification();
        notification.setMessage(message);
        notification.setRecipientId(recipientId);
        notification.setSenderId(senderId);
        notification.setType(type);
        notification.setTimesheetId(timesheetId);
        return notificationRepo.save(notification);
    }

    public List<Notification> getUserNotifications(String userId) {
        return notificationRepo.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepo.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotificationsForAdmin() {
        System.out.println("Fetching unread notifications for admin...");
        List<Notification> result = notificationRepo.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc("admin");
        System.out.println("Found " + result.size() + " unread notifications");
        return result;
    }

    public void markAllAsRead(String userId) {
        notificationRepo.markAllAsRead(userId);
    }

    public void markAsRead(Long notificationId) {
        notificationRepo.markAsRead(notificationId);
    }

    public long getUnreadCount(String userId) {
        return notificationRepo.countByRecipientIdAndIsReadFalse(userId);
    }

   
public long getUnreadCountForAdmin() {
    System.out.println("Fetching unread notification count for admin...");
    long count = notificationRepo.countByRecipientIdAndIsReadFalse("admin");
    System.out.println("Count retrieved: " + count);
    return count;
}
}