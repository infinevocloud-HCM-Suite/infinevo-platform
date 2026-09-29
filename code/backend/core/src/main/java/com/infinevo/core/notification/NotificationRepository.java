package com.infinevo.core.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.notification} (W-20.1). Every read names the tenant, and every read a
 * recipient makes names the recipient too — row-level security keeps tenants apart, and these
 * signatures keep colleagues apart. {@code app_user} holds no {@code DELETE} on the table ({@code V039}).
 */
@Transactional(readOnly = true)
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** A recipient's in-app notifications. Served by {@code idx_notification_tenant_recipient_read}. */
    Page<Notification> findByTenantIdAndRecipientEmployeeIdAndChannel(
            UUID tenantId, UUID recipientEmployeeId, Channel channel, Pageable pageable);

    /** The same, unread only. */
    Page<Notification> findByTenantIdAndRecipientEmployeeIdAndChannelAndReadAtIsNull(
            UUID tenantId, UUID recipientEmployeeId, Channel channel, Pageable pageable);

    /** One notification, only if it is this recipient's — the check behind mark-as-read. */
    Optional<Notification> findByIdAndTenantIdAndRecipientEmployeeId(UUID id, UUID tenantId, UUID recipientEmployeeId);

    /** One notification in this tenant, for delivery processing. */
    Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Claims an email for one delivery attempt (W-20.2): {@code QUEUED} to {@code SENDING}, counting the
     * attempt. {@code 1} means this caller sends it; {@code 0} means it is already sent, failed, or being
     * sent by someone else — a message the queue delivered twice at once is sent once (D-50).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE core.notification SET status = 'SENDING', attempt_count = attempt_count + 1,"
                    + " updated_at = :now, updated_by = 'worker'"
                    + " WHERE id = :id AND tenant_id = :tenantId AND channel = 'EMAIL' AND status = 'QUEUED'",
            nativeQuery = true)
    int claimForDelivery(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("now") Instant now);

    /**
     * Puts back claims a crash left behind: {@code SENDING} since before {@code staleBefore} returns to
     * {@code QUEUED}, due at once. The email may then be sent twice, never lost.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE core.notification SET status = 'QUEUED', next_attempt_at = :now, updated_at = :now,"
                    + " updated_by = 'worker'"
                    + " WHERE tenant_id = :tenantId AND status = 'SENDING' AND updated_at < :staleBefore",
            nativeQuery = true)
    int releaseStaleClaims(
            @Param("tenantId") UUID tenantId, @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    /**
     * Emails the delivery sweep should send now: queued, and either due for a retry or queued before
     * {@code queuedBefore} with no retry pending — a message that never reached the queue, or was lost
     * on it. Oldest first; served by {@code idx_notification_tenant_status_queued}.
     */
    @Query("SELECT n.id FROM Notification n WHERE n.tenantId = :tenantId AND n.channel = :channel"
            + " AND n.status = :status"
            + " AND ((n.nextAttemptAt IS NULL AND n.queuedAt <= :queuedBefore) OR n.nextAttemptAt <= :now)"
            + " ORDER BY n.queuedAt")
    List<UUID> findDueForDelivery(
            @Param("tenantId") UUID tenantId,
            @Param("channel") Channel channel,
            @Param("status") NotificationStatus status,
            @Param("queuedBefore") Instant queuedBefore,
            @Param("now") Instant now,
            Pageable pageable);
}
