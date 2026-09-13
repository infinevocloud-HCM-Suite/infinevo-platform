package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.EmployeeBankDetailDTO;

import java.util.List;

public interface EmployeeBankDetailService {

    // Create
    EmployeeBankDetailDTO create(String organizationId, EmployeeBankDetailDTO dto);

    // Update
    EmployeeBankDetailDTO update(String organizationId, String employeeId, EmployeeBankDetailDTO dto);

    // Get by employeeId
    EmployeeBankDetailDTO getByEmployeeId(String organizationId, String employeeId);

    // List all bank details in org (if needed)
    List<EmployeeBankDetailDTO> getAll(String organizationId);

    // Delete by employeeId
    void deleteByEmployeeId(String organizationId, Long employeeId);
    
    public List<EmployeeBankDetailDTO> createBulk(String organizationId, List<EmployeeBankDetailDTO> dtoList);
}

