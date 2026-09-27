package com.infinevo.payroll.salary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing employee salary structure versions (W-26.2).
 */
public interface EmployeeSalaryService {

    SalaryVersionResponse create(UUID employeeId, SalaryVersionRequest request);

    SalaryVersionResponse revise(UUID employeeId, SalaryVersionRequest request);

    SalaryVersionResponse getAsOf(UUID employeeId, LocalDate asOf);

    List<SalaryVersionResponse> listVersions(UUID employeeId);

    SalaryVersionResponse update(UUID employeeId, UUID versionId, SalaryVersionRequest request);

    void cancel(UUID employeeId, UUID versionId);

    SalaryVersionResponse versionInForce(UUID tenantId, UUID employeeId, LocalDate date);
}
