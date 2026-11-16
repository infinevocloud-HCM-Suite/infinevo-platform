package com.infinevo.payroll.challan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TdsChallan} (W-89.1).
 */
@Repository
public interface TdsChallanRepository extends JpaRepository<TdsChallan, UUID> {

    Optional<TdsChallan> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TdsChallan> findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(
            UUID tenantId, String financialYear, String quarter);

    List<TdsChallan> findByTenantIdAndFinancialYearOrderByTenderDateAsc(UUID tenantId, String financialYear);

    boolean existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDate(
            UUID tenantId, String bsrCode, String challanSerialNo, LocalDate tenderDate);
}
