package com.itsdev.payroll.service.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.BenefitDTO;

import java.util.List;

public interface BenefitService {
    BenefitDTO createBenefit(String orgId, BenefitDTO dto);
    BenefitDTO updateBenefit(String orgId, String benefitId, BenefitDTO dto);
    void deleteBenefit(String orgId, String benefitId);
    BenefitDTO getBenefit(String orgId, String benefitId);
    List<BenefitDTO> getAllBenefits(String orgId);
    void inactivateBenefit(String organizationId, String benefitId);
    void reactivateBenefit(String organizationId, String benefitId);
}
