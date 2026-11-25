package com.infinevo.hrms.exit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ExitRequest} with tenant scoping (W-103).
 */
public interface ExitRequestRepository extends JpaRepository<ExitRequest, UUID> {

    Optional<ExitRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ExitRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ExitRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, ExitStatus status);

    List<ExitRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    boolean existsByTenantIdAndEmployeeIdAndStatusNotIn(
            UUID tenantId, UUID employeeId, Collection<ExitStatus> terminalStatuses);
}
