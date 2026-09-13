package com.itsdev.payroll.mapper.employeeTDS;

import com.itsdev.payroll.dto.employeeTDS.EmployeeTdsRequestDTO;
import com.itsdev.payroll.dto.employeeTDS.EmployeeTdsResponseDTO;
import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;

public final class EmployeeTdsMapper {

    private EmployeeTdsMapper() {
        // Utility class
    }

    // =========================================================
    // REQUEST DTO → ENTITY
    // Used while creating / recalculating TDS
    // =========================================================
    public static EmployeeTds toEntity(EmployeeTdsRequestDTO dto) {

        if (dto == null) {
            return null;
        }

        EmployeeTds entity = new EmployeeTds();

        entity.setEmployeeId(dto.getEmployeeId());
        entity.setOrganizationId(dto.getOrganizationId());
        entity.setFiscalYear(dto.getFiscalYear());

        entity.setTdsSourceType(dto.getTdsSourceType());
        entity.setPoiId(dto.getPoiId());
        entity.setTaxRegime(dto.getTaxRegime());

        entity.setAnnualGrossSalary(dto.getAnnualGrossSalary());
        entity.setAnnualTaxableIncome(dto.getAnnualTaxableIncome());
        entity.setFinalAnnualTax(dto.getFinalAnnualTax());

        entity.setEffectiveFromMonth(dto.getEffectiveFromMonth());

        // IMPORTANT:
        // isActive & createdAt are controlled by entity defaults / service layer
        entity.setIsActive(true);

        return entity;
    }

    // =========================================================
    // ENTITY → RESPONSE DTO
    // Used for UI / Admin / Payrun reads
    // =========================================================
    public static EmployeeTdsResponseDTO toResponseDTO(EmployeeTds entity) {

        if (entity == null) {
            return null;
        }

        EmployeeTdsResponseDTO dto = new EmployeeTdsResponseDTO();

        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setFiscalYear(entity.getFiscalYear());

        dto.setTdsSourceType(entity.getTdsSourceType());
        dto.setTdsSourceTypeDisplayName(entity.getTdsSourceTypeDisplayName());

        dto.setPoiId(entity.getPoiId());
        dto.setTaxRegime(entity.getTaxRegime());

        dto.setAnnualGrossSalary(entity.getAnnualGrossSalary());
        dto.setAnnualTaxableIncome(entity.getAnnualTaxableIncome());
        dto.setFinalAnnualTax(entity.getFinalAnnualTax());

        dto.setEffectiveFromMonth(entity.getEffectiveFromMonth());
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());

        return dto;
    }

    // =========================================================
    // UPDATE EXISTING ENTITY FROM REQUEST DTO
    // Used during salary revision / recalculation
    // =========================================================
    public static void updateEntity(
            EmployeeTds entity,
            EmployeeTdsRequestDTO dto
    ) {

        if (entity == null || dto == null) {
            return;
        }

        entity.setTdsSourceType(dto.getTdsSourceType());
        entity.setPoiId(dto.getPoiId());
        entity.setTaxRegime(dto.getTaxRegime());

        entity.setAnnualGrossSalary(dto.getAnnualGrossSalary());
        entity.setAnnualTaxableIncome(dto.getAnnualTaxableIncome());
        entity.setFinalAnnualTax(dto.getFinalAnnualTax());

        entity.setEffectiveFromMonth(dto.getEffectiveFromMonth());
    }
}
