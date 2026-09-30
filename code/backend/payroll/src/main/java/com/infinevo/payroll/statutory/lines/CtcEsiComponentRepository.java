package com.infinevo.payroll.statutory.lines;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link CtcEsiComponent} (W-31.3).
 */
public interface CtcEsiComponentRepository extends JpaRepository<CtcEsiComponent, UUID> {

    List<CtcEsiComponent> findByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);

    @Modifying
    @Query("DELETE FROM CtcEsiComponent c WHERE c.tenantId = :tenantId AND c.ctcStructureId = :ctcStructureId")
    void deleteByTenantIdAndCtcStructureId(
            @Param("tenantId") UUID tenantId, @Param("ctcStructureId") UUID ctcStructureId);
}
