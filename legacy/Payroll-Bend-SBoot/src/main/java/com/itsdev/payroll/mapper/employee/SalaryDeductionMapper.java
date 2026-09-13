package com.itsdev.payroll.mapper.employee;

import com.itsdev.payroll.dto.employee.SalaryDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.SalaryDeductionResponseDTO;
import com.itsdev.payroll.entity.SalaryDeduction;
import com.itsdev.payroll.entity.employee.BasicDetails;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SalaryDeductionMapper {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public static SalaryDeductionResponseDTO toResponseDTO(SalaryDeduction entity) {
        if (entity == null) {
            return null;
        }

        SalaryDeductionResponseDTO dto = new SalaryDeductionResponseDTO();
        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setDeductionAmount(entity.getDeductionAmount());

        if (entity.getDeductionMonth() != null) {
            dto.setDeductionMonth(entity.getDeductionMonth().format(MONTH_FORMATTER));
        }

        dto.setDeductionType(entity.getDeductionType());
        dto.setReason(entity.getReason());
        dto.setRemarks(entity.getRemarks());
        dto.setStatus(entity.getStatus());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setProofUrl(entity.getProofUrl());

        // Resolve details from basicDetails if available
        BasicDetails employee = entity.getEmployee();
        if (employee != null) {
            dto.setEmployeeNumber(employee.getEmployeeNumber());
            dto.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
        }

        return dto;
    }

    public static SalaryDeduction toEntity(SalaryDeductionRequestDTO request) {
        if (request == null) {
            return null;
        }

        SalaryDeduction entity = new SalaryDeduction();
        entity.setEmployeeId(request.getEmployeeId());
        entity.setDeductionAmount(request.getDeductionAmount());

        if (request.getDeductionMonth() != null) {
            // "2026-10" -> LocalDate "2026-10-01"
            LocalDate date = LocalDate.parse(request.getDeductionMonth() + "-01");
            entity.setDeductionMonth(date);
        }

        entity.setDeductionType(request.getDeductionType());
        entity.setReason(request.getReason());
        entity.setRemarks(request.getRemarks());
        entity.setProofUrl(request.getProofUrl());
        entity.setProofPublicId(request.getProofPublicId());

        return entity;
    }
}
