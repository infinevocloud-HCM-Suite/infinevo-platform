package com.infinevo.payroll.proof;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes the tenant id (DEBT-022). */
public interface EmployeeProofOfInvestmentRepository extends JpaRepository<EmployeeProofOfInvestment, UUID> {

    Optional<EmployeeProofOfInvestment> findByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);

    Optional<EmployeeProofOfInvestment> findByTenantIdAndEmployeeIdAndFinancialYear(
            UUID tenantId, UUID employeeId, String financialYear);

    Optional<EmployeeProofOfInvestment> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * The id only. Finding a proof to lock must not load its entity first: Hibernate keeps the state it
     * loaded first even when a later {@code FOR UPDATE} query waited for another transaction's change,
     * so a second submit would still see {@code DRAFT} and go through. Look the id up here, then load
     * the entity with {@link #lockByTenantIdAndId}.
     */
    @Query("SELECT p.id FROM EmployeeProofOfInvestment p"
            + " WHERE p.tenantId = :tenantId AND p.employeeId = :employeeId AND p.financialYear = :financialYear")
    Optional<UUID> findIdByTenantIdAndEmployeeIdAndFinancialYear(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("financialYear") String financialYear);

    @Query("SELECT p.id FROM EmployeeProofOfInvestment p"
            + " WHERE p.tenantId = :tenantId AND p.declarationId = :declarationId")
    Optional<UUID> findIdByTenantIdAndDeclarationId(
            @Param("tenantId") UUID tenantId, @Param("declarationId") UUID declarationId);

    /**
     * The proof row taken for update. Submit and every change to the items go through this, so two
     * concurrent requests on one proof queue behind each other rather than both seeing {@code DRAFT}.
     * Call it on an entity that has not been loaded yet in this transaction (see above).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EmployeeProofOfInvestment p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<EmployeeProofOfInvestment> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
