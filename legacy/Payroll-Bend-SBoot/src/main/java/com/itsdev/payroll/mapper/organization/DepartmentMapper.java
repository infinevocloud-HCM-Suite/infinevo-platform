package com.itsdev.payroll.mapper.organization;

import com.itsdev.payroll.dto.organization.DepartmentDTO;
import com.itsdev.payroll.entity.organization.Department;

public class DepartmentMapper {

    public static DepartmentDTO toDTO(Department department) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setDepartmentId(department.getDepartmentId());
        dto.setName(department.getName());
        dto.setDescription(department.getDescription());
        dto.setDepartmentCode(department.getDepartmentCode());
        return dto;
    }

    public static Department toEntity(DepartmentDTO dto) {
        Department department = new Department();
        department.setDepartmentId(dto.getDepartmentId());
        department.setName(dto.getName());
        department.setDescription(dto.getDescription());
        department.setDepartmentCode(dto.getDepartmentCode());
        return department;
    }
}
