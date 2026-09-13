package com.itsdev.payroll.repository.organization;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.OrganizationRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRoleRepository extends JpaRepository<OrganizationRole, Long> {
    Optional<OrganizationRole> findByRoleIdAndIsDeletedFalse(String roleId);
    List<OrganizationRole> findAllByOrganizationAndIsDeletedFalse(Organization organization);
    boolean existsByRoleId(String roleId);
    boolean existsByRoleNameAndOrganizationAndIsDeletedFalse(String roleName, Organization organization);
    Optional<OrganizationRole> findByRoleIdAndOrganizationAndIsDeletedFalse(String roleId, Organization organization);

}