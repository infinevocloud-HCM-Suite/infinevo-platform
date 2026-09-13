package com.itsdev.payroll.mapper.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.EsiDTO;
import com.itsdev.payroll.entity.statutorycomponents.Esi;

public class EsiMapper {

    public static Esi toEntity(EsiDTO dto) {
        Esi entity = new Esi();
        entity.setIsActive(dto.getIsActive());
        entity.setEmployeeContribution(dto.getEmployeeContribution());
        entity.setEmployerContribution(dto.getEmployerContribution());
        entity.setRegistrationNumber(dto.getRegistrationNumber());
        entity.setRegistrationDate(dto.getRegistrationDate());
        entity.setCanEnableEmployerEsiInCtc(dto.getCanEnableEmployerEsiInCtc());
        entity.setIsIncludedInSalaryStructure(dto.getIsIncludedInSalaryStructure());
        entity.setDeductionCycle(dto.getDeductionCycle());
        entity.setDeductionCycleFormatted(dto.getDeductionCycleFormatted());
        entity.setRegistrationDateFormatted(dto.getRegistrationDateFormatted());
        entity.setIsIncludedInCtc(dto.getIsIncludedInCtc());
        entity.setName(dto.getName());
        entity.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        return entity;
    }

    public static EsiDTO toDto(Esi entity) {
        EsiDTO dto = new EsiDTO();
        dto.setIsActive(entity.getIsActive());
        dto.setEmployeeContribution(entity.getEmployeeContribution());
        dto.setEmployerContribution(entity.getEmployerContribution());
        dto.setRegistrationNumber(entity.getRegistrationNumber());
        dto.setRegistrationDate(entity.getRegistrationDate());
        dto.setCanEnableEmployerEsiInCtc(entity.getCanEnableEmployerEsiInCtc());
        dto.setIsIncludedInSalaryStructure(entity.getIsIncludedInSalaryStructure());
        dto.setDeductionCycle(entity.getDeductionCycle());
        dto.setDeductionCycleFormatted(entity.getDeductionCycleFormatted());
        dto.setRegistrationDateFormatted(entity.getRegistrationDateFormatted());
        dto.setIsIncludedInCtc(entity.getIsIncludedInCtc());
        dto.setName(entity.getName());
        dto.setIsAssociatedWithEmployee(entity.getIsAssociatedWithEmployee());
        return dto;
    }
}
