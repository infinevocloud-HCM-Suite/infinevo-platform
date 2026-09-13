package com.itsdev.payroll.service.leave;

import com.itsdev.payroll.dto.leave.BulkLeaveAllocationRequestDTO;
import com.itsdev.payroll.dto.leave.EmployeeLeaveAllocationResponseDTO;
import com.itsdev.payroll.dto.leave.LeaveAllocationItemDTO;
import com.itsdev.payroll.dto.leave.LeaveAllocationImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface EmployeeLeaveAllocationService {

    List<EmployeeLeaveAllocationResponseDTO> saveBulkAllocations(String organizationId, BulkLeaveAllocationRequestDTO request, String createdBy);

    EmployeeLeaveAllocationResponseDTO updateEmployeeAllocation(String organizationId, String employeeId, String year, List<LeaveAllocationItemDTO> leaveTypes, String updatedBy);

    List<EmployeeLeaveAllocationResponseDTO> getAllocations(String organizationId, String year);

    Map<String, Object> getAllocationsPaginated(String organizationId, String year, String search, int page, int size);

    EmployeeLeaveAllocationResponseDTO getEmployeeAllocation(String organizationId, String employeeId, String year);

    void deleteEmployeeAllocation(String organizationId, String employeeId, String year);

    void deleteSingleLeaveTypeAllocation(String organizationId, String employeeId, String leaveType, String year);

    LeaveAllocationImportResultDTO importNewAllocations(String organizationId, MultipartFile file, String createdBy);
}
