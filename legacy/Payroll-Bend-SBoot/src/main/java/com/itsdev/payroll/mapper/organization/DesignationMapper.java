package com.itsdev.payroll.mapper.organization;

import com.itsdev.payroll.dto.organization.DesignationDTO;
import com.itsdev.payroll.entity.organization.Designation;

public class DesignationMapper {

    public static DesignationDTO toDTO(Designation designation) {
        DesignationDTO dto = new DesignationDTO();
        dto.setDesignationId(designation.getDesignationId());
        dto.setName(designation.getName());
        return dto;
    }

    public static Designation toEntity(DesignationDTO dto) {
        Designation designation = new Designation();
        designation.setDesignationId(dto.getDesignationId());
        designation.setName(dto.getName());
        return designation;
    }
}
