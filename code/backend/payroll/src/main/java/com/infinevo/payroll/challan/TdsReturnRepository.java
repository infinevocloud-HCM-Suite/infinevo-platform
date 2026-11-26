package com.infinevo.payroll.challan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Filed quarters (W-89). */
public interface TdsReturnRepository extends JpaRepository<TdsReturn, UUID> {

    Optional<TdsReturn> findByTenantIdAndFinancialYearAndQuarter(UUID tenantId, String financialYear, String quarter);

    boolean existsByTenantIdAndFinancialYearAndQuarter(UUID tenantId, String financialYear, String quarter);

    List<TdsReturn> findByTenantIdAndFinancialYearOrderByQuarterAsc(UUID tenantId, String financialYear);
}
