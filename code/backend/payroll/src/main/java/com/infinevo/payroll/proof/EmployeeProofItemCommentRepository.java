package com.infinevo.payroll.proof;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link EmployeeProofItemComment} (W-34.2).
 *
 * <p>Every finder explicitly takes {@code tenantId} (DEBT-022). Comments are flat, oldest first.
 */
public interface EmployeeProofItemCommentRepository extends JpaRepository<EmployeeProofItemComment, UUID> {

    List<EmployeeProofItemComment> findByTenantIdAndItemIdOrderByCreatedAtAscIdAsc(UUID tenantId, UUID itemId);

    List<EmployeeProofItemComment> findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(
            UUID tenantId, Collection<UUID> itemIds);

    long countByTenantIdAndItemId(UUID tenantId, UUID itemId);
}
