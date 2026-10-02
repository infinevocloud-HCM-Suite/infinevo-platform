package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Takes the declaration row for update, for one purpose: creating the proof.
 *
 * <p>The proof is unique per declaration. Two first reads that both find none and both insert would
 * make one of them fail on the unique index, and in PostgreSQL a failed statement aborts the whole
 * transaction, so it could not simply look again. Serialising on the declaration row avoids that.
 */
public interface ProofDeclarationLockRepository extends Repository<EmployeeInvestmentDeclaration, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM EmployeeInvestmentDeclaration d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<EmployeeInvestmentDeclaration> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
