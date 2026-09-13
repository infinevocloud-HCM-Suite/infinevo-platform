package com.itsdev.payroll.mapper.organization;

import com.itsdev.payroll.dto.organization.IncomeTaxDetailsDTO;
import com.itsdev.payroll.entity.organization.IncomeTaxDetails;

public class IncomeTaxDetailsMapper {

    public static IncomeTaxDetails toEntity(IncomeTaxDetailsDTO dto) {
        IncomeTaxDetails entity = new IncomeTaxDetails();
        entity.setTanNumber(dto.getTanNumber());
        entity.setPanNumber(dto.getPanNumber());
        entity.setTdsCircle(dto.getTdsCircle());
        entity.setAuthorizedPersonName(dto.getAuthorizedPersonName());
        entity.setAuthorizedPersonParent(dto.getAuthorizedPersonParent());
        entity.setAuthorizedPersonDesignation(dto.getAuthorizedPersonDesignation());
        entity.setDepositSchedule(dto.getDepositSchedule());
        entity.setEmployeeId(dto.getEmployeeId());
        return entity;
    }

    public static IncomeTaxDetailsDTO toDTO(IncomeTaxDetails entity) {
        IncomeTaxDetailsDTO dto = new IncomeTaxDetailsDTO();
        dto.setTanNumber(entity.getTanNumber());
        dto.setPanNumber(entity.getPanNumber());
        dto.setTdsCircle(entity.getTdsCircle());
        dto.setAuthorizedPersonName(entity.getAuthorizedPersonName());
        dto.setAuthorizedPersonParent(entity.getAuthorizedPersonParent());
        dto.setAuthorizedPersonDesignation(entity.getAuthorizedPersonDesignation());
        dto.setDepositSchedule(entity.getDepositSchedule());
        dto.setEmployeeId(entity.getEmployeeId());
        return dto;
    }
}
