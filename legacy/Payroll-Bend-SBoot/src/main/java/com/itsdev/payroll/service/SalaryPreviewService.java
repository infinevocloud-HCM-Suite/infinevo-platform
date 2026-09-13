package com.itsdev.payroll.service;


import com.itsdev.payroll.dto.employee.preview.EmployeePreviewDTO;

public interface SalaryPreviewService {

    /**
     * Build salary preview for an employee based on employeeId + orgId.
     */
    EmployeePreviewDTO getEmployeePreview(String organizationId, String employeeId);
}


