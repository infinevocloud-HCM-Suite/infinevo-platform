package com.infinevo.core.overtime;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes {@code core.overtime_request} (W-39.2). Every read names the tenant. */
@Transactional(readOnly = true)
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, UUID> {

    /** One row in the bound tenant, for {@code cancel} and for the detail read. */
    Optional<OvertimeRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Every employee's entries in the range — the plain {@code GET} (spec §4). */
    List<OvertimeRequest> findByTenantIdAndOvertimeDateBetweenOrderByOvertimeDateDescEmployeeIdAsc(
            UUID tenantId, LocalDate from, LocalDate to);

    /** One employee's entries in the range — {@code GET ?employeeId=}. */
    List<OvertimeRequest> findByTenantIdAndEmployeeIdAndOvertimeDateBetweenOrderByOvertimeDateDescEmployeeIdAsc(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);
}
