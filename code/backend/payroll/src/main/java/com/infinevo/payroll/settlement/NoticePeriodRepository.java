package com.infinevo.payroll.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link NoticePeriodSettlement} (W-90.2).
 */
@Repository
public interface NoticePeriodRepository extends JpaRepository<NoticePeriodSettlement, UUID> {

    List<NoticePeriodSettlement> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    Optional<NoticePeriodSettlement> findByTenantIdAndEmployeeIdAndStatusNot(
            UUID tenantId, UUID employeeId, String status);
}
