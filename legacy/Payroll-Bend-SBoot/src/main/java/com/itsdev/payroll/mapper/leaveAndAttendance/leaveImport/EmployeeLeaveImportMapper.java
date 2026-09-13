package com.itsdev.payroll.mapper.leaveAndAttendance.leaveImport;



import com.itsdev.payroll.dto.leaveAndAttendance.leaveImport.EmployeeLeaveImportDTO;
import com.itsdev.payroll.entity.leaveAndAttedance.leaveImport.EmployeeLeaveImport;
import com.itsdev.payroll.entity.organization.Organization;

public class EmployeeLeaveImportMapper {

    public static EmployeeLeaveImportDTO toDTO(EmployeeLeaveImport entity) {
        EmployeeLeaveImportDTO dto = new EmployeeLeaveImportDTO();
        dto.setCount(entity.getCount());
        dto.setDate(entity.getDate());
        dto.setEmployeeNumber(entity.getEmployeeNumber());
        dto.setLeaveType(entity.getLeaveType());
        return dto;
    }

    public static EmployeeLeaveImport toEntity(EmployeeLeaveImportDTO dto, Organization organization) {
        EmployeeLeaveImport entity = new EmployeeLeaveImport();
        entity.setCount(dto.getCount());
        entity.setDate(dto.getDate());
        entity.setEmployeeNumber(dto.getEmployeeNumber());
        entity.setLeaveType(dto.getLeaveType());
        entity.setOrganization(organization);
        return entity;
    }
}
