package com.itsdev.payroll.repository.statutorycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.statutorycomponents.OrgPTOverride;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgPTOverrideRepository extends JpaRepository<OrgPTOverride, Long> {
    List<OrgPTOverride> findByOrganizationId(String orgId);
    
    Optional<OrgPTOverride> findByOrganizationIdAndState(String organizationId, String state);

}