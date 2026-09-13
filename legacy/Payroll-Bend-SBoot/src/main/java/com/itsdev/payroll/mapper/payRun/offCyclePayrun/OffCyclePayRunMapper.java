package com.itsdev.payroll.mapper.payRun.offCyclePayrun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.payRun.offcyclepayrun.*;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payRun.offCyclePayrun.*;
import com.itsdev.payroll.entity.salarycomponents.Deduction;
import com.itsdev.payroll.entity.salarycomponents.Earning;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OffCyclePayRunMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------ Parent Mapper ------------------
    public static OffCyclePayRunDTO toDto(OffCyclePayRun entity) {
        if (entity == null) return null;

        OffCyclePayRunDTO dto = new OffCyclePayRunDTO();
        dto.setPayrollRunId(entity.getPayrollRunId());
        dto.setPayDate(entity.getPayDate());
        dto.setStatus(entity.getStatus());
        dto.setStatusFormatted(entity.getStatusFormatted());
        dto.setType(entity.getType());
        dto.setNotes(entity.getNotes());

        dto.setEmployees(
                entity.getEmployees().stream()
                        .map(OffCyclePayRunMapper::toDto)
                        .collect(Collectors.toList())
        );

        return dto;
    }

    public static OffCyclePayRun toEntity(OffCyclePayRunDTO dto) {
        if (dto == null) return null;

        OffCyclePayRun entity = new OffCyclePayRun();
        entity.setPayrollRunId(dto.getPayrollRunId());
        entity.setPayDate(dto.getPayDate());
        entity.setStatus(dto.getStatus());
        entity.setStatusFormatted(dto.getStatusFormatted());
        entity.setType(dto.getType());
        entity.setNotes(dto.getNotes());

        entity.setEmployees(new ArrayList<>()); // will be set in service

        return entity;
    }

    // ------------------ Employee Mapper ------------------
    public static OffCyclePayrunEmployeeDTO toDto(OffCyclePayrunEmployee entity) {
        if (entity == null) return null;

        OffCyclePayrunEmployeeDTO dto = new OffCyclePayrunEmployeeDTO();
        dto.setEmployeeId(entity.getEmployee() != null ? entity.getEmployee().getId() : null);

        dto.setEarnings(entity.getEarnings().stream()
                .map(OffCyclePayRunMapper::toDto)
                .collect(Collectors.toList()));

        dto.setDeductions(entity.getDeductions().stream()
                .map(OffCyclePayRunMapper::toDto)
                .collect(Collectors.toList()));

        dto.setLopAdjustmentDetails(entity.getLopAdjustmentDetails() != null ?
                readJsonList(entity.getLopAdjustmentDetails()) : new ArrayList<>());

        dto.setTaxes(entity.getTaxes() != null ?
                readJsonList(entity.getTaxes()) : new ArrayList<>());

        return dto;
    }

    public static OffCyclePayrunEmployee toEntity(OffCyclePayrunEmployeeDTO dto, OffCyclePayRun parent,
                                                  BasicDetails employee, Organization org,
                                                  List<OffCyclePayrunEmployeeEarning> managedEarnings,
                                                  List<OffCyclePayrunEmployeeDeduction> managedDeductions) {
        if (dto == null) return null;

        OffCyclePayrunEmployee entity = new OffCyclePayrunEmployee();
        entity.setPayrollRun(parent);
        entity.setOrganization(org);
        entity.setEmployee(employee);

        entity.setEarnings(managedEarnings != null ? managedEarnings : new ArrayList<>());
        entity.setDeductions(managedDeductions != null ? managedDeductions : new ArrayList<>());

        entity.setLopAdjustmentDetails(dto.getLopAdjustmentDetails() != null ?
                writeJson(dto.getLopAdjustmentDetails()) : null);

        entity.setTaxes(dto.getTaxes() != null ? writeJson(dto.getTaxes()) : null);

        return entity;
    }

    // ------------------ Earning Mapper ------------------
    public static OffCyclePayrunEmployeeEarningDTO toDto(OffCyclePayrunEmployeeEarning entity) {
        if (entity == null) return null;

        OffCyclePayrunEmployeeEarningDTO dto = new OffCyclePayrunEmployeeEarningDTO();
        dto.setEarningId(entity.getEarning().getEarningId());
        dto.setAmount(entity.getAmount());
        dto.setDays(entity.getDays() != null ? String.valueOf(entity.getDays()) : null);
        return dto;
    }

    public static OffCyclePayrunEmployeeEarning toEntity(OffCyclePayrunEmployeeEarningDTO dto,
                                                         OffCyclePayrunEmployee parent,
                                                         Earning dbEarning) {
        if (dto == null) return null;

        OffCyclePayrunEmployeeEarning entity = new OffCyclePayrunEmployeeEarning();
        entity.setOffCyclePayrunEmployee(parent);
        entity.setEarning(dbEarning); // ✅ managed entity
        entity.setAmount(dto.getAmount());
        entity.setDays(dto.getDays() != null && !dto.getDays().isEmpty() ? Integer.valueOf(dto.getDays()) : null);
        return entity;
    }

    // ------------------ Deduction Mapper ------------------
    public static OffCyclePayrunEmployeeDeductionDTO toDto(OffCyclePayrunEmployeeDeduction entity) {
        if (entity == null) return null;

        OffCyclePayrunEmployeeDeductionDTO dto = new OffCyclePayrunEmployeeDeductionDTO();
        dto.setDeductionId(entity.getDeduction().getDeductionId());
        dto.setAmount(entity.getAmount());
        return dto;
    }

    public static OffCyclePayrunEmployeeDeduction toEntity(OffCyclePayrunEmployeeDeductionDTO dto,
                                                           OffCyclePayrunEmployee parent,
                                                           Deduction dbDeduction) {
        if (dto == null) return null;

        OffCyclePayrunEmployeeDeduction entity = new OffCyclePayrunEmployeeDeduction();
        entity.setOffCyclePayrunEmployee(parent);
        entity.setDeduction(dbDeduction); // ✅ managed entity
        entity.setAmount(dto.getAmount());
        return entity;
    }

    // ------------------ JSON Utilities ------------------
    private static List<Object> readJsonList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
