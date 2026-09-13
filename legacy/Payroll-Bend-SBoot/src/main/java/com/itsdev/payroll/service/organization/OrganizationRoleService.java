package com.itsdev.payroll.service.organization;

import com.itsdev.payroll.dto.organization.OrganizationRoleDTO;

import java.util.List;

public interface OrganizationRoleService {
    OrganizationRoleDTO createRole(String organizationId, OrganizationRoleDTO dto);
    OrganizationRoleDTO updateRole(String organizationId, String roleId, OrganizationRoleDTO dto);
    OrganizationRoleDTO getRole(String organizationId, String roleId);
    List<OrganizationRoleDTO> getAllRoles(String organizationId);
    void deleteRole(String organizationId, String roleId);
}
