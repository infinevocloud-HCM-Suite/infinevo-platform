package com.itsdev.payroll.repository;

import com.itsdev.payroll.entity.OrganizationUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationUserMappingRepository extends JpaRepository<OrganizationUserMapping, Long> {
    List<OrganizationUserMapping> findByUserId(String userId);
    List<OrganizationUserMapping> findByOrganizationId(String organizationId);
    boolean existsByUserIdAndOrganizationId(String userId, String organizationId);
}
