package com.infinevo.core.ai;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for {@link AiUsage} (W-114.1). Row-level security on {@code core.ai_usage} limits every read and write
 * to the bound tenant.
 */
@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

    Optional<AiUsage> findByTenantIdAndUsageMonth(UUID tenantId, LocalDate usageMonth);

    /**
     * Counts one AI request for the tenant in the month, unless the month's count has reached {@code cap}. The whole
     * cap check is this one statement: the first request of a month inserts {@code 1} (a cap is always at least 1),
     * a later one adds 1 only while the stored count is below the cap, so two requests at the same moment cannot
     * both take the last place.
     *
     * @param month the first day of the calendar month, UTC
     * @param cap the monthly request cap, at least 1
     * @return the month's count after this request; empty when the cap was already reached and nothing was counted
     */
    @Transactional
    @Query(
            value = "INSERT INTO core.ai_usage (tenant_id, usage_month, request_count, updated_at)"
                    + " VALUES (:tenantId, :month, 1, now())"
                    + " ON CONFLICT (tenant_id, usage_month) DO UPDATE"
                    + " SET request_count = core.ai_usage.request_count + 1, updated_at = now()"
                    + " WHERE core.ai_usage.request_count < :cap"
                    + " RETURNING request_count",
            nativeQuery = true)
    Optional<Integer> countOne(
            @Param("tenantId") UUID tenantId, @Param("month") LocalDate month, @Param("cap") int cap);
}
