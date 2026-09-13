package com.phegondev.usersmanagementsystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.phegondev.usersmanagementsystem.entity.RoleReminderConfig;

import java.util.List;
import java.util.Optional;

public interface RoleReminderConfigRepo extends JpaRepository<RoleReminderConfig, Long> {
  //  Optional<RoleReminderConfig> findByReminderTypeAndLevel(String reminderType, int level);
}

