package com.infinevo.core.employee.detail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code core.employee_identification} (W-13.2) — {@code V017__employee_identification.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeeIdentificationRepository extends EmployeeDetailRepository<EmployeeIdentification> {

    /**
     * Batch lookup of identity records by PAN numbers in a tenant (W-36.5 §4), joining employee to filter active ones.
     */
    @Query(
            """
        SELECT ei FROM EmployeeIdentification ei
        JOIN FETCH ei.employee e
        WHERE ei.tenantId = :tenantId
          AND ei.panNumber IN :pans
          AND e.deleted = false
    """)
    List<EmployeeIdentification> findByTenantIdAndPanNumberIn(
            @Param("tenantId") UUID tenantId, @Param("pans") Collection<String> pans);
}
