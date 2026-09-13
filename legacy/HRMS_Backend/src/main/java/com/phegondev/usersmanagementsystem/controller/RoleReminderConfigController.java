package com.phegondev.usersmanagementsystem.controller;

import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phegondev.usersmanagementsystem.dto.RoleReminderConfigDTO;
import com.phegondev.usersmanagementsystem.service.RoleReminderConfigService;

@RestController
@RequestMapping("/rolereminder")
public class RoleReminderConfigController {
	
	

    private final RoleReminderConfigService service;

   
    public RoleReminderConfigController(RoleReminderConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RoleReminderConfigDTO> createReminder(@RequestBody RoleReminderConfigDTO dto) {
        RoleReminderConfigDTO createdDto = service.createReminder(dto);
      //  return new ResponseEntity<>(createdDto, HttpStatus.CREATED);
        return ResponseEntity.status(HttpStatus.SC_CREATED).body(createdDto);
    }

    // Update Reminder
    @PutMapping("/{id}")
    public ResponseEntity<RoleReminderConfigDTO> updateReminder(@PathVariable Long id,
                                                                 @RequestBody RoleReminderConfigDTO dto) {
        RoleReminderConfigDTO updatedDto = service.updateReminder(id, dto);
        return ResponseEntity.ok(updatedDto);
    }
}
