package com.infinevo.payroll.challan;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    boolean existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDateAndIdNot(
            UUID tenantId, String bsrCode, String challanSerialNo, LocalDate tenderDate, UUID id);

    /** The challan, row-locked, so two allocations to it cannot both pass the cap check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM TdsChallan c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<TdsChallan> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** A challan's quarter, read without loading or locking the challan. */
    interface QuarterKey {
        String getFinancialYear();

        String getQuarter();
    }

    @Query("SELECT c.financialYear AS financialYear, c.quarter AS quarter FROM TdsChallan c"
            + " WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<QuarterKey> findQuarterKey(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** All the quarter's challans, row-locked in id order, to serialise allocations within a quarter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM TdsChallan c WHERE c.tenantId = :tenantId AND c.financialYear = :fy AND c.quarter = :q"
            + " ORDER BY c.id")
    List<TdsChallan> lockQuarter(@Param("tenantId") UUID tenantId, @Param("fy") String fy, @Param("q") String q);
}
