package com.phegondev.usersmanagementsystem.dto;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;
import com.phegondev.usersmanagementsystem.enumuration.ReminderType;

public class RoleReminderConfigDTO {
	
	private Long id;
    private boolean enabled;
    private ReminderType reminderType;
    private ReminderLevel level;
    private DayOfWeek dayOfWeek;
    private LocalTime time;
    private String timezone;
    private List<String> recipients;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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
	public ReminderType getReminderType() {
		return reminderType;
	}
	public void setReminderType(ReminderType reminderType) {
		this.reminderType = reminderType;
	}
	public ReminderLevel getLevel() {
		return level;
	}
	public void setLevel(ReminderLevel level) {
		this.level = level;
	}
	public DayOfWeek getDayOfWeek() {
		return dayOfWeek;
	}
	public void setDayOfWeek(DayOfWeek dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}
	public LocalTime getTime() {
		return time;
	}
	public void setTime(LocalTime time) {
		this.time = time;
	}
	public String getTimezone() {
		return timezone;
	}
	public void setTimezone(String timezone) {
		this.timezone = timezone;
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
	
	
	public RoleReminderConfigDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	
	public RoleReminderConfigDTO(Long id, boolean enabled, ReminderType reminderType, ReminderLevel level,
			DayOfWeek dayOfWeek, LocalTime time, String timezone, List<String> recipients, LocalDateTime createdAt,
			LocalDateTime updatedAt) {
		super();
		this.id = id;
		this.enabled = enabled;
		this.reminderType = reminderType;
		this.level = level;
		this.dayOfWeek = dayOfWeek;
		this.time = time;
		this.timezone = timezone;
		this.recipients = recipients;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	
	@Override
	public String toString() {
		return "RoleReminderConfigDTO [id=" + id + ", enabled=" + enabled + ", reminderType=" + reminderType
				+ ", level=" + level + ", dayOfWeek=" + dayOfWeek + ", time=" + time + ", timezone=" + timezone
				+ ", recipients=" + recipients + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
	}
    
    
	    
	    

}
