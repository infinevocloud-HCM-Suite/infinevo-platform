package com.infinevo.core.subscription;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Subscription} (W-12.1).
 *
 * <p>Tenant isolation is enforced by PostgreSQL row-level security policy
 * on {@code core.subscription} for standard {@code app_user} connections.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);
}
