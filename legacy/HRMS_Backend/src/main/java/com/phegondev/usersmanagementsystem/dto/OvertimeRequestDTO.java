package com.phegondev.usersmanagementsystem.dto;

import com.phegondev.usersmanagementsystem.enumuration.OvertimeStatus;
import jakarta.persistence.Column;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OvertimeRequestDTO {
    private Long id;
    private String employeeId;
    private String employeeName;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String project;
    private String notes;
    private OvertimeStatus managerStatus;
    private OvertimeStatus hrStatus;
    private LocalDateTime managerUpdatedAt;
    private LocalDateTime hrUpdatedAt;
    private LocalDateTime createdAt;
    private double durationHours;
    private String managerComment;
    private String hrComment;
    private String managerEmployeeId;

    // You can add a method to convert from entity to DTO
    public static OvertimeRequestDTO fromEntity(com.phegondev.usersmanagementsystem.entity.OvertimeRequest entity) {
        OvertimeRequestDTO dto = new OvertimeRequestDTO();
        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeName(entity.getEmployeeName());
        dto.setCategory(entity.getCategory());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setProject(entity.getProject());
        dto.setNotes(entity.getNotes());
        dto.setManagerStatus(entity.getManagerStatus());
        dto.setHrStatus(entity.getHrStatus());
        dto.setManagerUpdatedAt(entity.getManagerUpdatedAt());
        dto.setHrUpdatedAt(entity.getHrUpdatedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setDurationHours(entity.getDurationHours());
        return dto;
    }
}