package com.phegondev.usersmanagementsystem.service;

import org.springframework.stereotype.Service;

import com.phegondev.usersmanagementsystem.dto.RoleReminderConfigDTO;
import com.phegondev.usersmanagementsystem.entity.RoleReminderConfig;
import com.phegondev.usersmanagementsystem.repository.RoleReminderConfigRepo;

@Service
public class RoleReminderConfigService {

    private final RoleReminderConfigRepo repository;

    // Constructor Injection
    public RoleReminderConfigService(RoleReminderConfigRepo repository) {
        this.repository = repository;
    }

    public RoleReminderConfigDTO createReminder(RoleReminderConfigDTO dto) {
        System.out.println("Creating new reminder: " + dto);

        RoleReminderConfig reminder = new RoleReminderConfig();
        reminder.setReminderType(dto.getReminderType());
        reminder.setEnabled(dto.isEnabled());
        reminder.setLevel(dto.getLevel());
      //  reminder.setDay(dto.getDay());
        reminder.setTime(dto.getTime());
        reminder.setTimezone(dto.getTimezone());
        reminder.setRecipients(dto.getRecipients());

        RoleReminderConfig saved = repository.save(reminder);

        System.out.println("Saved reminder with ID: " + saved.getId());
        return convertToDto(saved);
    }

    public RoleReminderConfigDTO updateReminder(Long id, RoleReminderConfigDTO dto) {
        System.out.println("Updating reminder with ID: " + id);

        RoleReminderConfig reminder = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reminder not found with ID: " + id));

        copyFromDto(reminder, dto);

        RoleReminderConfig updated = repository.save(reminder);

        System.out.println("Updated reminder with ID: " + updated.getId());
        return convertToDto(updated);
    }

    private void copyFromDto(RoleReminderConfig entity, RoleReminderConfigDTO dto) {
        entity.setReminderType(dto.getReminderType());
        entity.setEnabled(dto.isEnabled());
        entity.setLevel(dto.getLevel());
     //   entity.setDay(dto.getDay());
        entity.setTime(dto.getTime());
        entity.setTimezone(dto.getTimezone());
        entity.setRecipients(dto.getRecipients());

        System.out.println("Data mapped from DTO to entity: " + entity);
    }
    
    public RoleReminderConfigDTO convertToDto(RoleReminderConfig reminderConfig) {
        if (reminderConfig == null) {
            return null;
        }

        RoleReminderConfigDTO dto = new RoleReminderConfigDTO();
        dto.setId(reminderConfig.getId());
        dto.setReminderType(reminderConfig.getReminderType());
        dto.setEnabled(reminderConfig.isEnabled());
        dto.setLevel(reminderConfig.getLevel());
     //   dto.setDay(reminderConfig.getDay());
        dto.setTime(reminderConfig.getTime());
        dto.setTimezone(reminderConfig.getTimezone());
        dto.setRecipients(reminderConfig.getRecipients());
        dto.setCreatedAt(reminderConfig.getCreatedAt());
        dto.setUpdatedAt(reminderConfig.getUpdatedAt());

        return dto;
    }

}


