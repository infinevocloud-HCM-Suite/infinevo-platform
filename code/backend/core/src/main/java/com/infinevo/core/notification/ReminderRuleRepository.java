package com.infinevo.core.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.reminder_rule} (W-20.2). Every query names the tenant for
 * index alignment, and row-level security isolates tenants in PostgreSQL.
 */
@Transactional(readOnly = true)
public interface ReminderRuleRepository extends JpaRepository<ReminderRule, UUID> {

    List<ReminderRule> findByTenantId(UUID tenantId);

    List<ReminderRule> findByTenantIdAndIsActive(UUID tenantId, boolean isActive);

    List<ReminderRule> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<ReminderRule> findByTenantIdAndEvent(UUID tenantId, NotificationEvent event);

    List<ReminderRule> findByTenantIdAndEventAndIsActive(UUID tenantId, NotificationEvent event, boolean isActive);

    Optional<ReminderRule> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Claims a due rule before anything is sent (W-20.2): stamps {@code last_executed_at} and counts the
     * run, only if {@code last_executed_at} is still what the evaluator read. {@code 1} means this sweep
     * sends it; {@code 0} means another already did. A crash part-way through is then a missed reminder,
     * never a second one to everyone. {@code newCycle} restarts the count for a new deadline.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE core.reminder_rule SET last_executed_at = :now,"
                    + " repeat_count = CASE WHEN :newCycle THEN 1 ELSE repeat_count + 1 END,"
                    + " updated_at = :now, updated_by = 'worker'"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND last_executed_at IS NOT DISTINCT FROM CAST(:previous AS timestamptz)",
            nativeQuery = true)
    int claimRun(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("previous") Instant previous,
            @Param("now") Instant now,
            @Param("newCycle") boolean newCycle);
}
