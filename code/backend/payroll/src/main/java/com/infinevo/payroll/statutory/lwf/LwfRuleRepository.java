package com.infinevo.payroll.statutory.lwf;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LwfRuleRepository extends JpaRepository<LwfRule, UUID> {

    @Query(
            """
        SELECT r FROM LwfRule r
        WHERE r.stateCode = :stateCode
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY r.effectiveFrom DESC
    """)
    List<LwfRule> findInForceRules(@Param("stateCode") String stateCode, @Param("date") LocalDate date);

    @Query(
            """
        SELECT r FROM LwfRule r
        WHERE r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY r.stateCode ASC, r.effectiveFrom DESC
    """)
    List<LwfRule> findAllInForceRules(@Param("date") LocalDate date);
}
