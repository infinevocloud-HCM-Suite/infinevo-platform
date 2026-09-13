package com.itsdev.payroll.repository.auth;

import com.itsdev.payroll.entity.auth.OrganizationRoleAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrganizationRoleActionRepository extends JpaRepository<OrganizationRoleAction, Long> {

    List<OrganizationRoleAction> findByOrganizationRole_Id(Long organizationRoleId);

    @Query("SELECT o.action.code FROM OrganizationRoleAction o WHERE o.organizationRole.id IN :roleIds")
    List<String> findActionCodesByOrganizationRoleIds(@Param("roleIds") List<Long> roleIds);

    void deleteByOrganizationRole_IdAndAction_Id(Long organizationRoleId, Long actionId);
}
