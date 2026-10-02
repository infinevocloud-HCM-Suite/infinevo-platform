package com.infinevo.core.leave;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link LeaveType} (W-16.1).
 */
public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<LeaveType> findByTenantIdAndCode(UUID tenantId, String code);

    List<LeaveType> findByTenantIdOrderByCodeAsc(UUID tenantId);

    List<LeaveType> findByTenantIdAndIsActiveTrueOrderByCodeAsc(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
