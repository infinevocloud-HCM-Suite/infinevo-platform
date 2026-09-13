package com.itsdev.payroll.repository.organization;

import com.itsdev.payroll.entity.organization.Department;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByDepartmentId(String departmentId);
    List<Department> findByOrganization_OrganizationId(String organizationId);
    void deleteByDepartmentIdAndOrganization_OrganizationId(String departmentId, String organizationId);
    
    @Modifying
    @Query("UPDATE Department d SET d.status = false " +
           "WHERE d.departmentId = :departmentId " +
           "AND d.organization.organizationId = :organizationId")
    void softDeleteByDepartmentIdAndOrganizationId(@Param("departmentId") String departmentId,
                                                    @Param("organizationId") String organizationId);
    
    List<Department> findByOrganization_OrganizationIdAndStatusTrue(String organizationId);


}
