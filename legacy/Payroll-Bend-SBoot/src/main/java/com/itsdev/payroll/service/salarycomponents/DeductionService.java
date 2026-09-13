package com.itsdev.payroll.service.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.DeductionDTO;

import java.util.List;

public interface DeductionService {

    DeductionDTO create(String organizationId, DeductionDTO dto);

    DeductionDTO update(String organizationId, String deductionId, DeductionDTO dto);

    DeductionDTO get(String organizationId, String deductionId);

    List<DeductionDTO> getAll(String organizationId);

    void delete(String organizationId, String deductionId);

    void inactivateDeduction(String organizationId, String deductionId);

    void reactivateDeduction(String organizationId, String deductionId);
}
