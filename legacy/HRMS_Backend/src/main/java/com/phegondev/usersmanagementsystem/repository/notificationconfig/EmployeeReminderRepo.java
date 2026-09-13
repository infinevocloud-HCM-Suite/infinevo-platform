package com.phegondev.usersmanagementsystem.repository.notificationconfig;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EmployeeReminder;

public interface EmployeeReminderRepo extends JpaRepository<EmployeeReminder, Long> {
	
	  List<EmployeeReminder> findAllByEnabledTrue();
}
