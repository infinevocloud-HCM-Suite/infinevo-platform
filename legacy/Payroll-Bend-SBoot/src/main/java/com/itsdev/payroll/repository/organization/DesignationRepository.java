package com.itsdev.payroll.repository.organization;

import com.itsdev.payroll.entity.organization.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    Optional<Designation> findByDesignationId(String designationId);
    List<Designation> findByOrganization_OrganizationId(String organizationId);
    void deleteByDesignationIdAndOrganization_OrganizationId(String designationId, String organizationId);
    
    
    
    @Modifying
    @Query("UPDATE Designation d SET d.status = false " +
           "WHERE d.designationId = :designationId AND d.organization.organizationId = :organizationId")
    void softDeleteByDesignationIdAndOrganizationId(@Param("designationId") String designationId,
                                                     @Param("organizationId") String organizationId);
    
    List<Designation> findByOrganization_OrganizationIdAndStatusTrue(String organizationId);


}