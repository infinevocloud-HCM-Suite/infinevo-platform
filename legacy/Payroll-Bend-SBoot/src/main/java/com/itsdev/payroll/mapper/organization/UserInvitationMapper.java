package com.itsdev.payroll.mapper.organization;

import com.itsdev.payroll.dto.organization.UserInvitationDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.UserInvitation;

public class UserInvitationMapper {

    public static UserInvitation toEntity(UserInvitationDTO dto, Organization org) {
        UserInvitation entity = new UserInvitation();
        entity.setRoleId(dto.getRoleId());
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        entity.setMobile(dto.getMobile());
        entity.setInvitationType(dto.getInvitationType());
        entity.setIsSuperAdmin(dto.getIsSuperAdmin());
        entity.setStatus(dto.getStatus());
        entity.setUserRole(dto.getUserRole());
        entity.setOrganization(org);
        entity.setIsEditable(dto.getIsEditable());
        entity.setIsInvitationAccepted(dto.getIsInvitationAccepted());
        return entity;
    }

    public static UserInvitationDTO toDto(UserInvitation entity) {
        UserInvitationDTO dto = new UserInvitationDTO();
        dto.setUserId(entity.getUserId());
        dto.setRoleId(entity.getRoleId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setMobile(entity.getMobile());
        dto.setInvitationType(entity.getInvitationType());
        dto.setIsSuperAdmin(entity.getIsSuperAdmin());
        dto.setStatus(entity.getStatus());
        dto.setUserRole(entity.getUserRole());

        // FIX: Get organizationId from entity (handle null check)
        if (entity.getOrganization() != null) {
            dto.setOrganizationId(entity.getOrganization().getOrganizationId());
        }

        // FIX: Get values FROM entity, don't set them back!
        dto.setIsEditable(entity.getIsEditable());
        dto.setIsInvitationAccepted(entity.getIsInvitationAccepted());

        return dto;
    }
}