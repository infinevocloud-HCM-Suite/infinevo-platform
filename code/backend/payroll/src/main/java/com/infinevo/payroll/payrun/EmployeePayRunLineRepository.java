package com.infinevo.payroll.payrun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022). */
public interface EmployeePayRunLineRepository extends JpaRepository<EmployeePayRunLine, UUID> {

    /** One statement, not a load-then-delete per row: a recompute starts clean (W-29.2 §3). */
    @Modifying
    @Query("DELETE FROM EmployeePayRunLine l WHERE l.tenantId = :tenantId AND l.payrunId = :payrunId")
    int deleteByTenantIdAndPayrunId(@Param("tenantId") UUID tenantId, @Param("payrunId") UUID payrunId);

    List<EmployeePayRunLine> findByTenantIdAndEmployeePayrunIdOrderBySortOrderAsc(UUID tenantId, UUID employeePayrunId);

    long countByTenantIdAndPayrunId(UUID tenantId, UUID payrunId);
}
