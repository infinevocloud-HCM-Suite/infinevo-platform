package com.itsdev.payroll.mapper.leaveAndAttendance.preferences;

import com.itsdev.payroll.dto.leaveAndAttendance.preferences.PreferencesDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.preferences.Preferences;

public class PreferencesMapper {

    // Convert DTO → Entity
    public static Preferences toEntity(PreferencesDTO dto) {
        if (dto == null) {
            return null;
        }

        Preferences entity = new Preferences();
        // 🚨 Do NOT set ID here → JPA generates UUID automatically
        entity.setEndDay(dto.getEndDay());
        entity.setPayrollReportDay(dto.getPayrollReportDay());
        entity.setLeaveEncashmentEnabled(dto.getLeaveEncashmentEnabled());
        return entity;
    }

    // Convert Entity → DTO
    public static PreferencesDTO toDTO(Preferences entity) {
        if (entity == null) {
            return null;
        }

        PreferencesDTO dto = new PreferencesDTO();
        dto.setId(entity.getId()); // <-- FIX: map generated ID
        dto.setEndDay(entity.getEndDay());
        dto.setPayrollReportDay(entity.getPayrollReportDay());
        dto.setLeaveEncashmentEnabled(entity.getLeaveEncashmentEnabled());
        return dto;
    }

    // Update existing Entity from DTO (for update use case)
    public static void updateEntityFromDTO(PreferencesDTO dto, Preferences entity) {
        if (dto == null || entity == null) {
            return;
        }

        // 🚨 Do NOT update ID here
        entity.setEndDay(dto.getEndDay());
        entity.setPayrollReportDay(dto.getPayrollReportDay());
        entity.setLeaveEncashmentEnabled(dto.getLeaveEncashmentEnabled());
    }
}
