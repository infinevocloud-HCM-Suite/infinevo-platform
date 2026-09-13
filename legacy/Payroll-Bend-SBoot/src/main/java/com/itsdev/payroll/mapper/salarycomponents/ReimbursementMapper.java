package com.itsdev.payroll.mapper.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.ReimbursementDTO;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;

public class ReimbursementMapper {

    public static ReimbursementDTO toDTO(Reimbursement entity) {
        ReimbursementDTO dto = new ReimbursementDTO();
        dto.setReimbursementId(entity.getReimbursementId());
        dto.setReimbursementName(entity.getReimbursementName());
        dto.setReimbursementType(entity.getReimbursementType());
        dto.setReimbursementTypeFormatted(entity.getReimbursementTypeFormatted());
        dto.setDisplayName(entity.getDisplayName());
        dto.setMaxLimit(entity.getMaxLimit());
        dto.setIsIncludedInCtc(entity.getIsIncludedInCtc());
        dto.setIsIncludedInSalaryStructure(entity.getIsIncludedInSalaryStructure());
        dto.setStatus(entity.getStatus());
        dto.setStatusFormatted(entity.getStatusFormatted());
        dto.setIsFbpComponent(entity.getIsFbpComponent());
        dto.setIsOptIn(entity.getIsOptIn());
        dto.setCarryForwardOption(entity.getCarryForwardOption());
        dto.setIsAssociatedWithEmployee(entity.getIsAssociatedWithEmployee());
        return dto;
    }

    public static Reimbursement toEntity(ReimbursementDTO dto) {
        Reimbursement entity = new Reimbursement();
        entity.setReimbursementName(dto.getReimbursementName());
        entity.setReimbursementType(dto.getReimbursementType());
        entity.setReimbursementTypeFormatted(dto.getReimbursementTypeFormatted());
        entity.setDisplayName(dto.getDisplayName());
        entity.setMaxLimit(dto.getMaxLimit());
        entity.setIsIncludedInCtc(dto.getIsIncludedInCtc());
        entity.setIsIncludedInSalaryStructure(dto.getIsIncludedInSalaryStructure());
        entity.setStatus(dto.getStatus());
        entity.setStatusFormatted(dto.getStatusFormatted());
        entity.setIsFbpComponent(dto.getIsFbpComponent());
        entity.setIsOptIn(dto.getIsOptIn());
        entity.setCarryForwardOption(dto.getCarryForwardOption());
        entity.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        return entity;
    }
}
