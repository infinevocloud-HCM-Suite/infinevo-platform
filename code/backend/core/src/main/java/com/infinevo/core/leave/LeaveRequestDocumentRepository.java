package com.infinevo.core.leave;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LeaveRequestDocument} (W-16.3).
 */
@Repository
public interface LeaveRequestDocumentRepository extends JpaRepository<LeaveRequestDocument, UUID> {

    List<LeaveRequestDocument> findByTenantIdAndLeaveRequestId(UUID tenantId, UUID leaveRequestId);

    void deleteByTenantIdAndLeaveRequestId(UUID tenantId, UUID leaveRequestId);
}
