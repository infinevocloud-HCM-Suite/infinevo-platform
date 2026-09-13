package com.itsdev.payroll.service.leaveAndAttendance.leaveImport;



import com.itsdev.payroll.dto.leaveAndAttendance.leaveImport.EmployeeLeaveImportDTO;

import java.util.List;

public interface EmployeeLeaveImportService {

    EmployeeLeaveImportDTO createLeaveImport(String organizationId, EmployeeLeaveImportDTO dto);

    EmployeeLeaveImportDTO updateLeaveImport(String organizationId, Long id, EmployeeLeaveImportDTO dto);

    EmployeeLeaveImportDTO getLeaveImport(String organizationId, Long id);

    List<EmployeeLeaveImportDTO> getAllLeaveImports(String organizationId);

    void deleteLeaveImport(String organizationId, Long id);

    void saveAll(String organizationId, List<EmployeeLeaveImportDTO> leaveImports);
}

