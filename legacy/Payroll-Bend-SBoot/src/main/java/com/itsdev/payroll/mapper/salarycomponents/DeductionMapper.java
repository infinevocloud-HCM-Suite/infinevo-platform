package com.itsdev.payroll.mapper.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.DeductionDTO;
import com.itsdev.payroll.entity.salarycomponents.Deduction;

public class DeductionMapper {

    public static DeductionDTO toDTO(Deduction deduction) {
        DeductionDTO dto = new DeductionDTO();
        dto.setDeductionId(deduction.getDeductionId());
        dto.setDeductionName(deduction.getDeductionName());
        dto.setDeductionType(deduction.getDeductionType());
        dto.setDeductionTypeFormatted(deduction.getDeductionTypeFormatted());
        dto.setStatus(deduction.getStatus());
        dto.setStatusFormatted(deduction.getStatusFormatted());
        dto.setIsRecurring(deduction.getIsRecurring());
        dto.setCreatedTime(deduction.getCreatedTime());
        dto.setIsUserConfigurable(deduction.getIsUserConfigurable());
        dto.setIsAssociatedWithEmployee(deduction.getIsAssociatedWithEmployee());
        dto.setPerquisiteInterestRate(deduction.getPerquisiteInterestRate());
        dto.setEmiInterestRate(deduction.getEmiInterestRate());
        dto.setEmiType(deduction.getEmiType());
        return dto;
    }

    public static Deduction toEntity(DeductionDTO dto) {
        Deduction deduction = new Deduction();
        deduction.setDeductionId(dto.getDeductionId());
        deduction.setDeductionName(dto.getDeductionName());
        deduction.setDeductionType(dto.getDeductionType());
        deduction.setDeductionTypeFormatted(dto.getDeductionTypeFormatted());
        deduction.setStatus(dto.getStatus());
        deduction.setStatusFormatted(dto.getStatusFormatted());
        deduction.setIsRecurring(dto.getIsRecurring());
        deduction.setCreatedTime(dto.getCreatedTime());
        deduction.setIsUserConfigurable(dto.getIsUserConfigurable());
        deduction.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        deduction.setPerquisiteInterestRate(dto.getPerquisiteInterestRate());
        deduction.setEmiInterestRate(dto.getEmiInterestRate());
        deduction.setEmiType(dto.getEmiType());
        return deduction;
    }
}
