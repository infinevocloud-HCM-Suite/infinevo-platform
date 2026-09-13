package com.phegondev.usersmanagementsystem.service.notificationconfig;

import com.phegondev.usersmanagementsystem.dto.notificationconfig.NotificationSettingsDto;

public interface NotificationSettingsService {
	
	  public void saveAll(NotificationSettingsDto dto);
	  
	  public NotificationSettingsDto getAllSettings();
	  
	  boolean isEscalationReminderPresent();

}
