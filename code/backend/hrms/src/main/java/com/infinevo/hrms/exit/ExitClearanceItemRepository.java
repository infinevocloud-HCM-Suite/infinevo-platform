package com.infinevo.hrms.exit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ExitClearanceItem} with tenant scoping (W-103).
 */
public interface ExitClearanceItemRepository extends JpaRepository<ExitClearanceItem, UUID> {

    List<ExitClearanceItem> findByExitRequestIdAndTenantIdOrderByDepartmentAscItemNameAsc(
            UUID exitRequestId, UUID tenantId);

    List<ExitClearanceItem> findByExitRequestIdAndTenantIdAndDepartment(
            UUID exitRequestId, UUID tenantId, ClearanceDepartment department);

    Optional<ExitClearanceItem> findByIdAndExitRequestIdAndTenantId(UUID id, UUID exitRequestId, UUID tenantId);

    boolean existsByExitRequestIdAndTenantIdAndStatus(UUID exitRequestId, UUID tenantId, ClearanceItemStatus status);
}
