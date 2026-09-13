package com.itsdev.payroll.service.organization;

import com.itsdev.payroll.dto.organization.DepartmentDTO;

import java.util.List;

public interface DepartmentService {
    DepartmentDTO createDepartment(String organizationId, DepartmentDTO dto);
    DepartmentDTO updateDepartment(String organizationId, String departmentId, DepartmentDTO dto);
    DepartmentDTO getDepartment(String organizationId, String departmentId);
    List<DepartmentDTO> getAllDepartments(String organizationId);
    void deleteDepartment(String organizationId, String departmentId);
    void saveAll(String organizationId, List<DepartmentDTO> departments);
}
