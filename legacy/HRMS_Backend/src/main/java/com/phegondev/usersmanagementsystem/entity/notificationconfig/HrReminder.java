package com.phegondev.usersmanagementsystem.entity.notificationconfig;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;

import jakarta.persistence.*;

@Entity
@Table(name = "hr_reminders")
public class HrReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean enabled;

    @Enumerated(EnumType.STRING)
    private DayOfWeek day;

    
    private LocalTime time;

    @Enumerated(EnumType.STRING)
    private ReminderLevel level;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hr_reminder_recipients", joinColumns = @JoinColumn(name = "reminder_id"))
    @Column(name = "email")
    private List<String> recipients = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    public LocalDateTime getLastExecutedAt() {
		return lastExecutedAt;
	}

	public void setLastExecutedAt(LocalDateTime lastExecutedAt) {
		this.lastExecutedAt = lastExecutedAt;
	}


    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public void setDay(DayOfWeek day) {
        this.day = day;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public ReminderLevel getLevel() {
        return level;
    }

    public void setLevel(ReminderLevel level) {
        this.level = level;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Constructors

    public HrReminder() {
        super();
    }

    public HrReminder(Long id, boolean enabled, DayOfWeek day, LocalTime time, ReminderLevel level,
                      List<String> recipients, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.enabled = enabled;
        this.day = day;
        this.time = time;
        this.level = level;
        this.recipients = recipients;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "HrReminder [id=" + id + ", enabled=" + enabled + ", day=" + day + ", time=" + time +
                ", level=" + level + ", recipients=" + recipients +
                ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
    }
}
