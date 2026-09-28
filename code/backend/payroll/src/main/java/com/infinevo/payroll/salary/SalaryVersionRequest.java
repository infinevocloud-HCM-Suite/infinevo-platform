package com.infinevo.payroll.salary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for creating or revising an employee's salary structure version (W-26.2).
 */
public record SalaryVersionRequest(
        BigDecimal annualCtc,
        LocalDate effectiveFrom,
        String notes,
        List<SalaryComponentItemRequest> earnings,
        List<SalaryComponentItemRequest> benefits,
        List<SalaryComponentItemRequest> reimbursements) {}
