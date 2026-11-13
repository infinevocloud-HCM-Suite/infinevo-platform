package com.infinevo.core.employee.detail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes {@code core.employee_bank} (W-13.2) — {@code V019__employee_bank.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeeBankRepository extends EmployeeDetailRepository<EmployeeBank> {

    /**
     * Batch-loads bank records for the given employees within the tenant in a single query (W-88).
     */
    List<EmployeeBank> findByTenantIdAndEmployeeIdIn(UUID tenantId, Collection<UUID> employeeIds);
}
