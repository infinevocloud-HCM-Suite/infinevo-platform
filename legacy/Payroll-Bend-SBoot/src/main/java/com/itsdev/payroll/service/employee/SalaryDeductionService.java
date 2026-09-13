package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.SalaryDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.GridDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.SalaryDeductionResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface SalaryDeductionService {

    Map<String, Object> createGridDeduction(
            String organizationId,
            GridDeductionRequestDTO request,
            String currentUser
    );

    Page<SalaryDeductionResponseDTO> getAllDeductions(
            String organizationId,
            String search,
            String month,
            String status,
            Pageable pageable
    );

    void validateEmployeeBelongsToOrg(String employeeId, String organizationId);

    SalaryDeductionResponseDTO updateDeduction(
            String organizationId,
            Long id,
            SalaryDeductionRequestDTO request,
            String currentUser
    );

    void deleteDeduction(String organizationId, Long id);

    java.util.List<SalaryDeductionResponseDTO> getMyDeductions(
            String organizationId,
            String employeeId,
            String month
    );
}
