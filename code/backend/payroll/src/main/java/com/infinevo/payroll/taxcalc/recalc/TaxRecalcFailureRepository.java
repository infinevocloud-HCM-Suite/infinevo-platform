package com.infinevo.payroll.taxcalc.recalc;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop (D-147). */
@Repository
public interface TaxRecalcFailureRepository extends JpaRepository<TaxRecalcFailure, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM TaxRecalcFailure f WHERE f.tenantId = :tenantId AND f.employeeId = :employeeId "
            + "AND f.financialYear = :financialYear AND f.resolvedAt IS NULL")
    Optional<TaxRecalcFailure> findOpenForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    @Query("SELECT f FROM TaxRecalcFailure f WHERE f.id = :id AND f.tenantId = :tenantId AND f.resolvedAt IS NULL")
    Optional<TaxRecalcFailure> findOpenById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT f FROM TaxRecalcFailure f WHERE f.tenantId = :tenantId AND f.resolvedAt IS NULL "
            + "ORDER BY f.lastFailedAt DESC")
    List<TaxRecalcFailure> findOpen(@Param("tenantId") UUID tenantId);

    @Query("SELECT f FROM TaxRecalcFailure f WHERE f.tenantId = :tenantId AND f.resolvedAt IS NULL "
            + "AND f.financialYear = :financialYear ORDER BY f.lastFailedAt DESC")
    List<TaxRecalcFailure> findOpenForYear(
            @Param("tenantId") UUID tenantId, @Param("financialYear") String financialYear);

    /** Closes the open failure, if any, for one employee and year. Runs inside the successful recalculation. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TaxRecalcFailure f SET f.resolvedAt = :now, f.updatedAt = :now WHERE f.tenantId = :tenantId "
            + "AND f.employeeId = :employeeId AND f.financialYear = :financialYear AND f.resolvedAt IS NULL")
    int resolveOpen(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear,
            @Param("now") Instant now);
}
