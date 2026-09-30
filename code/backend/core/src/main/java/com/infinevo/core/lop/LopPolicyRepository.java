package com.infinevo.core.lop;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LopPolicy} (W-18.1).
 */
@Repository
public interface LopPolicyRepository extends JpaRepository<LopPolicy, UUID> {

    /**
     * Resolves the policy in force on the given date: the latest row with {@code effective_from <= asOfDate}.
     */
    Optional<LopPolicy> findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID tenantId, LocalDate asOfDate);

    /**
     * Finds all policy versions for a tenant ordered newest first.
     */
    List<LopPolicy> findByTenantIdOrderByEffectiveFromDesc(UUID tenantId);

    /**
     * Finds an existing policy version by exact effective date.
     */
    Optional<LopPolicy> findByTenantIdAndEffectiveFrom(UUID tenantId, LocalDate effectiveFrom);
}
