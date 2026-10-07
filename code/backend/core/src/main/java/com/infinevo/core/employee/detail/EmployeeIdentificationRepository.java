package com.infinevo.core.employee.detail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code core.employee_identification} (W-13.2) — {@code V017__employee_identification.sql}.
 *
 * <p>Everything else it can do is on {@link EmployeeDetailRepository}, including the warning about
 * the inherited finders that do not name a tenant.
 */
public interface EmployeeIdentificationRepository extends EmployeeDetailRepository<EmployeeIdentification> {

    /**
     * Who holds each of these PANs in this tenant, among live employees only (W-36.5, spec section 4).
     *
     * <p>One {@code SELECT ... WHERE pan_number IN (...)} over {@code idx_employee_identification_tenant_pan}
     * ({@link EmployeeIdentification}), joined to {@code core.employee} to drop soft-deleted rows. It
     * returns the PAN and the employee id only, so no identification entity — and no Aadhaar number —
     * is loaded. Both tenant columns are named: the foreign key on {@code employee_id} does not stop a
     * row pointing at another tenant's employee ({@link EmployeeDetail}).
     *
     * <p>A PAN held by two employees comes back twice; the service decides what that means.
     */
    @Query("SELECT i.panNumber AS panNumber, e.id AS employeeId"
            + " FROM EmployeeIdentification i JOIN i.employee e"
            + " WHERE i.tenantId = :tenantId AND e.tenantId = :tenantId"
            + " AND e.deleted = false AND i.panNumber IN :pans")
    List<PanHolder> findByTenantIdAndPanNumberIn(
            @Param("tenantId") UUID tenantId, @Param("pans") Collection<String> pans);

    @Query("SELECT COUNT(i) > 0 FROM EmployeeIdentification i JOIN i.employee e"
            + " WHERE i.tenantId = :tenantId AND e.tenantId = :tenantId"
            + " AND e.deleted = false AND i.panNumber = :pan AND e.id != :employeeId")
    boolean existsByTenantIdAndPanNumberAndEmployeeIdNot(
            @Param("tenantId") UUID tenantId,
            @Param("pan") String pan,
            @Param("employeeId") UUID employeeId);

    @Query("SELECT COUNT(i) > 0 FROM EmployeeIdentification i JOIN i.employee e"
            + " WHERE i.tenantId = :tenantId AND e.tenantId = :tenantId"
            + " AND e.deleted = false AND i.aadhaarNumber = :aadhaar AND e.id != :employeeId")
    boolean existsByTenantIdAndAadhaarNumberAndEmployeeIdNot(
            @Param("tenantId") UUID tenantId,
            @Param("aadhaar") String aadhaar,
            @Param("employeeId") UUID employeeId);

    /** One row of {@link #findByTenantIdAndPanNumberIn}. */
    interface PanHolder {
        String getPanNumber();

        UUID getEmployeeId();
    }
}
