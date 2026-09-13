package com.itsdev.payroll.repository;

import com.itsdev.payroll.entity.OrganizationUserRoleMapping;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationUserRoleMappingRepository extends JpaRepository<OrganizationUserRoleMapping, Long> {
    boolean existsByUserIdAndOrganizationIdAndRoleId(String userId, String organizationId, String roleId);

    List<OrganizationUserRoleMapping> findByUserId(String userId);

    List<OrganizationUserRoleMapping> findByOrganizationId(String organizationId);

    Optional<OrganizationUserRoleMapping> findByUserIdAndOrganizationId(String userId, String organizationId);

    @Modifying
    @Query("UPDATE OrganizationUserRoleMapping m SET m.roleName = :roleName WHERE m.roleId = :roleId")
    void updateRoleNameForMappings(@Param("roleId") String roleId, @Param("roleName") String roleName);

    List<OrganizationUserRoleMapping> findByUserIdAndIsEmployeePortalEnableTrue(String userId);

    List<OrganizationUserRoleMapping> findByOrganizationIdAndRoleName(
            String organizationId,
            String roleName);

    boolean existsByUserIdAndOrganizationId(String userId, String organizationId);
}
