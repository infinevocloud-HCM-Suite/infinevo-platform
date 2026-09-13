package com.itsdev.payroll.mapper.organization;

import com.itsdev.payroll.dto.organization.OrganizationRoleDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.OrganizationRole;

public class OrganizationRoleMapper {

    public static OrganizationRole toEntity(OrganizationRoleDTO dto, Organization org) {
        OrganizationRole entity = new OrganizationRole();
        entity.setRoleName(dto.getRoleName());
        entity.setAccessType(dto.getAccessType());
        entity.setUserActionRequired(dto.getUserActionRequired());
        entity.setIsDefault(dto.getIsDefault());
        entity.setRoleDescription(dto.getRoleDescription());
        entity.setStatus(dto.getStatus());
        entity.setOrganization(org);
        return entity;
    }

    public static OrganizationRoleDTO toDto(OrganizationRole entity) {
        OrganizationRoleDTO dto = new OrganizationRoleDTO();
        dto.setRoleName(entity.getRoleName());
        dto.setAccessType(entity.getAccessType());
        dto.setUserActionRequired(entity.getUserActionRequired());
        dto.setIsDefault(entity.getIsDefault());
        dto.setRoleDescription(entity.getRoleDescription());
        dto.setStatus(entity.getStatus());
        dto.setRoleId(entity.getRoleId());
        return dto;
    }
}
