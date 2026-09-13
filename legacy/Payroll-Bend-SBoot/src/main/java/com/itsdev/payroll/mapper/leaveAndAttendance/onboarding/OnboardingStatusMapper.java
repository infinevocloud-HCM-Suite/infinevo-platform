package com.itsdev.payroll.mapper.leaveAndAttendance.onboarding;



import com.itsdev.payroll.dto.leaveAndAttendance.onboarding.OnboardingStatusDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.onboarding.OnboardingStatus;


public class OnboardingStatusMapper {

    // Convert Entity → DTO
    public static OnboardingStatusDTO toDTO(OnboardingStatus entity) {
        if (entity == null) {
            return null;
        }

        OnboardingStatusDTO dto = new OnboardingStatusDTO();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganization().getId());
        dto.setOrganizationDetailsCompleted(entity.isOrganizationDetailsCompleted());
        dto.setLeaveSetupCompleted(entity.isLeaveSetupCompleted());
        dto.setHolidaySetupCompleted(entity.isHolidaySetupCompleted());
        dto.setAttendanceSetupCompleted(entity.isAttendanceSetupCompleted());
        dto.setPreferencesSetupCompleted(entity.isPreferencesSetupCompleted());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    // Convert DTO → Entity
    public static OnboardingStatus toEntity(OnboardingStatusDTO dto) {
        if (dto == null) {
            return null;
        }

        OnboardingStatus entity = new OnboardingStatus();
        entity.setOrganizationDetailsCompleted(dto.isOrganizationDetailsCompleted());
        entity.setLeaveSetupCompleted(dto.isLeaveSetupCompleted());
        entity.setHolidaySetupCompleted(dto.isHolidaySetupCompleted());
        entity.setAttendanceSetupCompleted(dto.isAttendanceSetupCompleted());
        entity.setPreferencesSetupCompleted(dto.isPreferencesSetupCompleted());
        return entity;
    }

    // Update existing Entity from DTO (for updates)
    public static void updateEntityFromDTO(OnboardingStatusDTO dto, OnboardingStatus entity) {
        if (dto == null || entity == null) {
            return;
        }

        entity.setOrganizationDetailsCompleted(dto.isOrganizationDetailsCompleted());
        entity.setLeaveSetupCompleted(dto.isLeaveSetupCompleted());
        entity.setHolidaySetupCompleted(dto.isHolidaySetupCompleted());
        entity.setAttendanceSetupCompleted(dto.isAttendanceSetupCompleted());
        entity.setPreferencesSetupCompleted(dto.isPreferencesSetupCompleted());
    }
}

