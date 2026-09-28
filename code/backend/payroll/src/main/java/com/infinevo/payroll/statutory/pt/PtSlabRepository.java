package com.infinevo.payroll.statutory.pt;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only repository for statutory professional tax slabs (W-31.2).
 */
public interface PtSlabRepository extends JpaRepository<PtSlab, Long> {

    @Query("SELECT s FROM PtSlab s WHERE s.stateCode = :stateCode "
            + "AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo > :date) "
            + "ORDER BY s.sortOrder ASC")
    List<PtSlab> inForce(@Param("stateCode") String stateCode, @Param("date") LocalDate date);
}
