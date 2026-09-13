package com.phegondev.usersmanagementsystem.repository.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import com.phegondev.usersmanagementsystem.entity.timesheet.TimesheetsNotification;

import java.util.List;

public interface TimesheetsNotificationRepo extends JpaRepository<TimesheetsNotification, Long> {
    List<TimesheetsNotification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<TimesheetsNotification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(String recipientId);

    @Modifying
    @Transactional
    @Query("UPDATE TimesheetsNotification n SET n.isRead = true WHERE n.recipientId = ?1")
    void markAllAsRead(String recipientId);

    @Modifying
    @Transactional
    @Query("UPDATE TimesheetsNotification n SET n.isRead = true WHERE n.id = ?1")
    void markAsRead(Long notificationId);

    long countByRecipientIdAndIsReadFalse(String recipientId);
}