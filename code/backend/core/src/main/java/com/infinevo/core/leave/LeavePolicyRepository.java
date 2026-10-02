package com.infinevo.core.leave;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link LeavePolicy} (W-16.1).
 */
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

    List<LeavePolicy> findByTenantIdAndLeaveTypeIdOrderByEffectiveFromDescCreatedAtDesc(
            UUID tenantId, UUID leaveTypeId);

    Optional<LeavePolicy>
            findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                    UUID tenantId, UUID leaveTypeId, LocalDate asOf);

    List<LeavePolicy> findByTenantId(UUID tenantId);
}
