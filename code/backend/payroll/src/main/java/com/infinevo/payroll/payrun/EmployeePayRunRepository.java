package com.infinevo.payroll.payrun;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder takes {@code tenantId} (DEBT-022) — the legacy {@code findByPayrunId} did not. */
public interface EmployeePayRunRepository extends JpaRepository<EmployeePayRun, UUID> {

    Page<EmployeePayRun> findByTenantIdAndPayrunId(UUID tenantId, UUID payrunId, Pageable pageable);

    Page<EmployeePayRun> findByTenantIdAndPayrunIdAndInclusionStatus(
            UUID tenantId, UUID payrunId, InclusionStatus inclusionStatus, Pageable pageable);
}
