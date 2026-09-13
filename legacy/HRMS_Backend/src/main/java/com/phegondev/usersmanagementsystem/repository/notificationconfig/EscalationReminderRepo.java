package com.phegondev.usersmanagementsystem.repository.notificationconfig;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EscalationReminder;

public interface EscalationReminderRepo extends JpaRepository<EscalationReminder, Long> {
	
	List<EscalationReminder> findAllByEnabledTrue();
	EscalationReminder findFirstByEnabledTrue();
	
}
