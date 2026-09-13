package com.itsdev.payroll.repository.leave;

import com.itsdev.payroll.entity.leave.EmployeeLeaveBalanceConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLeaveBalanceConsumptionRepository extends JpaRepository<EmployeeLeaveBalanceConsumption, Long> {

    List<EmployeeLeaveBalanceConsumption> findByOrganizationIdAndYear(String organizationId, String year);

    List<EmployeeLeaveBalanceConsumption> findByOrganizationId(String organizationId);

    List<EmployeeLeaveBalanceConsumption> findByOrganizationIdAndEmployeeIdAndYear(String organizationId, String employeeId, String year);

    List<EmployeeLeaveBalanceConsumption> findByOrganizationIdAndEmployeeIdAndYearOrderByIdAsc(String organizationId, String employeeId, String year);

    Optional<EmployeeLeaveBalanceConsumption> findByLeaveId(String leaveId);

    Optional<EmployeeLeaveBalanceConsumption> findByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(
            String organizationId,
            String employeeId,
            String leaveType,
            String year
    );

    void deleteByLeaveId(String leaveId);

    void deleteByOrganizationIdAndEmployeeIdAndYear(String organizationId, String employeeId, String year);

    void deleteByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(
            String organizationId,
            String employeeId,
            String leaveType,
            String year
    );
}
