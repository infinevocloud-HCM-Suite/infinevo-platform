package com.infinevo.payroll.form16;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link Form16PartA} (W-36.5 §4).
 *
 * <p>Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop, not the filter.
 */
public interface Form16PartARepository extends JpaRepository<Form16PartA, UUID> {

    /** The active certificate of one employee for one year. */
    @Query(
            """
            SELECT p FROM Form16PartA p
            WHERE p.tenantId = :tenantId
              AND p.employeeId = :employeeId
              AND p.financialYear = :financialYear
              AND p.isActive = true
            """)
    Optional<Form16PartA> findActive(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    /** As {@link #findActive}, holding a row lock so two uploads supersede one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT p FROM Form16PartA p
            WHERE p.tenantId = :tenantId
              AND p.employeeId = :employeeId
              AND p.financialYear = :financialYear
              AND p.isActive = true
            """)
    Optional<Form16PartA> findActiveForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    /** The officer list for a year, over {@code idx_form16_part_a_tenant_fy}. */
    @Query(
            """
            SELECT p FROM Form16PartA p
            WHERE p.tenantId = :tenantId
              AND p.financialYear = :financialYear
              AND p.isActive = true
            ORDER BY p.createdAt DESC, p.id
            """)
    List<Form16PartA> findActiveByTenantIdAndFinancialYear(
            @Param("tenantId") UUID tenantId, @Param("financialYear") String financialYear);
}
