package com.infinevo.core.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@code core.notification} (W-20.1). Every read names the tenant, and every read a
 * recipient makes names the recipient too — row-level security keeps tenants apart, and these
 * signatures keep colleagues apart. {@code app_user} holds no {@code DELETE} on the table ({@code V039}).
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** A recipient's in-app notifications. Served by {@code idx_notification_tenant_recipient_read}. */
    Page<Notification> findByTenantIdAndRecipientEmployeeIdAndChannel(
            UUID tenantId, UUID recipientEmployeeId, Channel channel, Pageable pageable);

    /** The same, unread only. */
    Page<Notification> findByTenantIdAndRecipientEmployeeIdAndChannelAndReadAtIsNull(
            UUID tenantId, UUID recipientEmployeeId, Channel channel, Pageable pageable);

    /** One notification, only if it is this recipient's — the check behind mark-as-read. */
    Optional<Notification> findByIdAndTenantIdAndRecipientEmployeeId(UUID id, UUID tenantId, UUID recipientEmployeeId);
}
