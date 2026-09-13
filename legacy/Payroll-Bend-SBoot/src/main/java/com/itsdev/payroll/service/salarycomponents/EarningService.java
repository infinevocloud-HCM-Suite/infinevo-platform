package com.itsdev.payroll.service.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.EarningDTO;

import java.util.List;

public interface EarningService {
    EarningDTO create(String organizationId, EarningDTO dto);
    EarningDTO update(String organizationId, String earningId, EarningDTO dto);
    EarningDTO get(String organizationId, String earningId);
    List<EarningDTO> getAllEarnings(String organizationId);
    void delete(String organizationId, String earningId);
    void inactivateEarning(String organizationId, String earningId);
    void reactivateEarning(String organizationId, String earningId);
}
