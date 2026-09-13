package com.phegondev.usersmanagementsystem.entity;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;
import com.phegondev.usersmanagementsystem.enumuration.ReminderType;

import java.util.List;

@Entity
@Table(name = "role_reminder_config")
public class RoleReminderConfig {
	
	  @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    @Column(name = "enabled", nullable = false)
	    private boolean enabled;

	    @Enumerated(EnumType.STRING)
	    @Column(name = "reminder_type", nullable = false)
	    private ReminderType reminderType;

	    @Enumerated(EnumType.STRING)
	    @Column(name = "level", nullable = false)
	    private ReminderLevel level;

	    
	    @Enumerated(EnumType.STRING)
	    @Column(name = "day_of_week", nullable = false, length = 15)
	    private DayOfWeek dayOfWeek;

	    @Column(name = "time", nullable = false)
	    private LocalTime time;

	    @Column(name = "timezone")
	    private String timezone;
	    
	    

	    @ElementCollection
	    @CollectionTable(
	        name = "reminder_recipients",
	        joinColumns = @JoinColumn(name = "reminder_id")
	    )
	    @Column(name = "recipient")
	    private List<String> recipients;
	    
	    @CreationTimestamp
	    private LocalDateTime createdAt;

	    @UpdateTimestamp
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

		@Override
		public String toString() {
			return "RoleReminderConfig [id=" + id + ", enabled=" + enabled + ", reminderType=" + reminderType
					+ ", level=" + level + ", dayOfWeek=" + dayOfWeek + ", time=" + time + ", timezone=" + timezone
					+ ", recipients=" + recipients + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
		}


	     
	    

}
