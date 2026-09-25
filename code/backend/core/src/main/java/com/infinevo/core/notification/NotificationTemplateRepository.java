package com.infinevo.core.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@code core.notification_template} (W-20.1). Every read names the tenant, the rule
 * {@code EmployeeRepository} follows.
 */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /**
     * The template in force for an event and channel on a date: active, the latest
     * {@code effective_from} not after it. Served by {@code idx_notification_template_tenant_event_channel}.
     */
    Optional<NotificationTemplate>
            findFirstByTenantIdAndEventAndChannelAndLocaleAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    UUID tenantId, NotificationEvent event, Channel channel, String locale, LocalDate onDate);

    /** One version, for an edit on a day that already has one. */
    Optional<NotificationTemplate> findByTenantIdAndEventAndChannelAndLocaleAndEffectiveFrom(
            UUID tenantId, NotificationEvent event, Channel channel, String locale, LocalDate effectiveFrom);

    List<NotificationTemplate> findByTenantIdOrderByEventAscChannelAscEffectiveFromDesc(UUID tenantId);

    List<NotificationTemplate> findByTenantIdAndEventOrderByChannelAscEffectiveFromDesc(
            UUID tenantId, NotificationEvent event);
}
