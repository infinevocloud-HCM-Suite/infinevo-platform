package com.itsdev.payroll.repository.leave;

import com.itsdev.payroll.entity.leave.EmployeeLeaveAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLeaveAllocationRepository extends JpaRepository<EmployeeLeaveAllocation, Long> {

    List<EmployeeLeaveAllocation> findByOrganizationIdAndYear(String organizationId, String year);

    List<EmployeeLeaveAllocation> findByOrganizationId(String organizationId);

    List<EmployeeLeaveAllocation> findByOrganizationIdAndEmployeeIdAndYear(String organizationId, String employeeId, String year);

    Optional<EmployeeLeaveAllocation> findByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(
            String organizationId,
            String employeeId,
            String leaveType,
            String year
    );

    void deleteByOrganizationIdAndEmployeeIdAndYear(String organizationId, String employeeId, String year);

    void deleteByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(
            String organizationId,
            String employeeId,
            String leaveType,
            String year
    );
}
