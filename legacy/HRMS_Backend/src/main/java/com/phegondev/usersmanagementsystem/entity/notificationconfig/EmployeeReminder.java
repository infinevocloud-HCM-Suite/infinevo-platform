package com.phegondev.usersmanagementsystem.entity.notificationconfig;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;

import jakarta.persistence.*;

@Entity
@Table(name = "employee_reminders")
public class EmployeeReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean enabled;

    @Enumerated(EnumType.STRING)
    private DayOfWeek day;

    
    private LocalTime time;

    @Enumerated(EnumType.STRING)
    private ReminderLevel level;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;


  

    public EmployeeReminder() {
		super();
		// TODO Auto-generated constructor stub
	}

	public EmployeeReminder(Long id, boolean enabled, DayOfWeek day, LocalTime time, ReminderLevel level,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.enabled = enabled;
        this.day = day;
        this.time = time;
        this.level = level;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
	
	 public LocalDateTime getLastExecutedAt() {
			return lastExecutedAt;
		}

		public void setLastExecutedAt(LocalDateTime lastExecutedAt) {
			this.lastExecutedAt = lastExecutedAt;
		}

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

    @Override
    public String toString() {
        return "EmployeeReminder [id=" + id + ", enabled=" + enabled + ", day=" + day + ", time=" + time +
               ", level=" + level + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
    }
}
