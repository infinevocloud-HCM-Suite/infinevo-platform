package com.itsdev.payroll.service.leave;

import com.itsdev.payroll.dto.leave.EmployeeLeaveConsumptionResponseDTO;
import com.itsdev.payroll.dto.leave.LeaveAllocationItemDTO;

import java.util.List;

public interface EmployeeLeaveConsumptionService {

    EmployeeLeaveConsumptionResponseDTO updateEmployeeConsumption(
            String organizationId,
            String employeeId,
            String year,
            String leaveMonth,
            List<LeaveAllocationItemDTO> leaveTypes,
            String updatedBy
    );

    List<EmployeeLeaveConsumptionResponseDTO> getConsumptions(String organizationId, String year);

    EmployeeLeaveConsumptionResponseDTO getEmployeeConsumption(String organizationId, String employeeId, String year);

    void deleteEmployeeConsumption(String organizationId, String employeeId, String year);

    void deleteEmployeeMonthConsumption(String organizationId, String employeeId, String year, String month);

    void deleteEmployeeEntry(String organizationId, String employeeId, String year, String entryId);

    void deleteSingleLeaveTypeConsumption(String organizationId, String employeeId, String leaveType, String year);
}
