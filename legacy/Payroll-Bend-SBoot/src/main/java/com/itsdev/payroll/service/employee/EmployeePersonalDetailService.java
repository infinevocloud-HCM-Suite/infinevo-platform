package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.EmployeePersonalDetailDTO;
import java.util.List;

public interface EmployeePersonalDetailService {

    // ✅ Create personal details for an employee
    EmployeePersonalDetailDTO create(String organizationId, EmployeePersonalDetailDTO dto);

    // ✅ Update personal details by employeeId
    EmployeePersonalDetailDTO update(String organizationId, String employeeId, EmployeePersonalDetailDTO dto);

    // ✅ Get by employeeId (instead of detail id)
    EmployeePersonalDetailDTO getByEmployeeId(String organizationId, String String);

    // ✅ Get all personal details for an organization
    List<EmployeePersonalDetailDTO> getAll(String organizationId);

    // ✅ Delete personal detail by employeeId
    void deleteByEmployeeId(String organizationId, Long employeeId);
    
    public List<EmployeePersonalDetailDTO> createBulk(String organizationId, List<EmployeePersonalDetailDTO> dtoList);
}
