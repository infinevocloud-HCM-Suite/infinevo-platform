package com.itsdev.payroll.mapper.employee;

import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;
import com.itsdev.payroll.entity.employee.EmployeeInvitation;

public class EmployeeInvitationMapper {

    public static EmployeeInvitationDTO toDto(EmployeeInvitation entity) {
        EmployeeInvitationDTO dto = new EmployeeInvitationDTO();
        dto.setInvitationId(entity.getInvitationId());
        dto.setEmail(entity.getEmail());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setIsPortalEnabled(entity.getIsPortalEnabled());
        dto.setOrganizationId(entity.getOrganization().getOrganizationId());
        dto.setIsInvitationAccepted(entity.getIsInvitationAccepted());
        dto.setAcceptanceToken(entity.getAcceptanceToken());
        dto.setExpiryDate(entity.getExpiryDate());
        return dto;
    }

    public static EmployeeInvitation toEntity(EmployeeInvitationDTO dto) {
        EmployeeInvitation entity = new EmployeeInvitation();
        entity.setInvitationId(dto.getInvitationId());
        entity.setEmail(dto.getEmail());
        entity.setEmployeeId(dto.getEmployeeId());
        entity.setIsPortalEnabled(dto.getIsPortalEnabled());
        entity.setIsInvitationAccepted(dto.getIsInvitationAccepted() != null ? dto.getIsInvitationAccepted() : false);
        entity.setAcceptanceToken(dto.getAcceptanceToken());
        entity.setExpiryDate(dto.getExpiryDate());
        return entity;
    }
}