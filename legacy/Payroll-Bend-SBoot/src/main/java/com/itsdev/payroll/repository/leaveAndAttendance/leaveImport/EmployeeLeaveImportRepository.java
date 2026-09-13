package com.itsdev.payroll.repository.leaveAndAttendance.leaveImport;


import com.itsdev.payroll.entity.leaveAndAttedance.leaveImport.EmployeeLeaveImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface EmployeeLeaveImportRepository extends JpaRepository<EmployeeLeaveImport, Long> {

    Optional<EmployeeLeaveImport> findByEmployeeNumberAndOrganization_OrganizationId(String employeeNumber, String organizationId);

    List<EmployeeLeaveImport> findByOrganization_OrganizationId(String organizationId);

    void deleteByEmployeeNumberAndOrganization_OrganizationId(String employeeNumber, String organizationId);
}

