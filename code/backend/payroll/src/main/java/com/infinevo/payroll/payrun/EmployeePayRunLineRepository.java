package com.infinevo.payroll.payrun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022). */
public interface EmployeePayRunLineRepository extends JpaRepository<EmployeePayRunLine, UUID> {

    /**
     * One employee's lines, in one statement, inside that employee's transaction (W-29.4 §4): a
     * recomputed row starts clean, and a row a resumed attempt skips keeps its lines.
     */
    @Modifying
    @Query("DELETE FROM EmployeePayRunLine l WHERE l.tenantId = :tenantId AND l.employeePayrunId = :employeePayrunId")
    int deleteByTenantIdAndEmployeePayrunId(
            @Param("tenantId") UUID tenantId, @Param("employeePayrunId") UUID employeePayrunId);

    List<EmployeePayRunLine> findByTenantIdAndEmployeePayrunIdOrderBySortOrderAsc(UUID tenantId, UUID employeePayrunId);

    long countByTenantIdAndPayrunId(UUID tenantId, UUID payrunId);
}
