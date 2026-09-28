package com.infinevo.payroll.statutory.pt;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for professional tax override change history (W-31.2).
 */
public interface PtHistoryRepository extends JpaRepository<PtHistory, UUID> {

    List<PtHistory> findByTenantIdAndStateCodeOrderByChangedAtDesc(UUID tenantId, String stateCode);
}
