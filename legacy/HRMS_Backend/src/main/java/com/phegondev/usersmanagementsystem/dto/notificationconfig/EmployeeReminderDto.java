package com.phegondev.usersmanagementsystem.dto.notificationconfig;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;

public class EmployeeReminderDto {

    private Long id;
    private boolean enabled;
    private DayOfWeek day;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime time;
    private ReminderLevel level;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    

    public EmployeeReminderDto() {
		super();
		// TODO Auto-generated constructor stub
	}

	public EmployeeReminderDto(Long id, boolean enabled, DayOfWeek day, LocalTime time,
                               ReminderLevel level, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.enabled = enabled;
        this.day = day;
        this.time = time;
        this.level = level;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
        return "EmployeeReminderDto [id=" + id + ", enabled=" + enabled + ", day=" + day + ", time=" + time +
               ", level=" + level + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
    }
}
