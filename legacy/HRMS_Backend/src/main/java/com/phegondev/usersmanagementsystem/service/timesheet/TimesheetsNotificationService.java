package com.phegondev.usersmanagementsystem.service.timesheet;

import java.util.List;
import com.phegondev.usersmanagementsystem.entity.timesheet.TimesheetsNotification;

public interface TimesheetsNotificationService {
    TimesheetsNotification createNotification(String message, String recipientId, String senderId,
            String type, String timesheetId, Long projectId, String rejectionReason);

    List<TimesheetsNotification> getUserNotifications(String userId);

    List<TimesheetsNotification> getUnreadNotifications(String userId);

    void markAllAsRead(String userId);

    void markAsRead(Long notificationId);

    long getUnreadCount(String userId);

    List<TimesheetsNotification> getUnreadNotificationsForManager(String managerId);

    long getUnreadCountForManager(String managerId);
}
