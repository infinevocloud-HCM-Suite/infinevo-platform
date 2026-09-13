package com.phegondev.usersmanagementsystem.serviceimpl.timeshhet;

import org.springframework.stereotype.Service;

import com.phegondev.usersmanagementsystem.entity.timesheet.TimesheetsNotification;
import com.phegondev.usersmanagementsystem.repository.timesheet.TimesheetsNotificationRepo;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetsNotificationService;

import java.util.List;

@Service
public class TimesheetsNotificationServiceImpl implements TimesheetsNotificationService {
    private final TimesheetsNotificationRepo notificationRepo;

    public TimesheetsNotificationServiceImpl(TimesheetsNotificationRepo notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    @Override
    public TimesheetsNotification createNotification(String message, String recipientId, String senderId,
            String type, String timesheetId, Long projectId, String rejectionReason) {
        TimesheetsNotification notification = new TimesheetsNotification();
        notification.setMessage(message);
        notification.setRecipientId(recipientId);
        notification.setSenderId(senderId);
        notification.setType(type);
        notification.setTimesheetId(timesheetId);
        notification.setProjectId(projectId);
        notification.setRejectionReason(rejectionReason);
        return notificationRepo.save(notification);
    }

    @Override
    public List<TimesheetsNotification> getUserNotifications(String userId) {
        return notificationRepo.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<TimesheetsNotification> getUnreadNotifications(String userId) {
        return notificationRepo.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<TimesheetsNotification> getUnreadNotificationsForManager(String managerId) {
        return notificationRepo.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(managerId);
    }

    @Override
    public void markAllAsRead(String userId) {
        notificationRepo.markAllAsRead(userId);
    }

    @Override
    public void markAsRead(Long notificationId) {
        notificationRepo.markAsRead(notificationId);
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationRepo.countByRecipientIdAndIsReadFalse(userId);
    }

    @Override
    public long getUnreadCountForManager(String managerId) {
        return notificationRepo.countByRecipientIdAndIsReadFalse(managerId);
    }
}