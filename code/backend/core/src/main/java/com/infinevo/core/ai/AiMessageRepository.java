package com.infinevo.core.ai;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AiMessage} (W-114.3). Callers load the session by id, tenant and user first; this reads the
 * messages of a session already proven to be the caller's.
 */
@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    /** Every message of the session, {@code TOOL} rows included, oldest first. */
    List<AiMessage> findByTenantIdAndSessionIdOrderByCreatedAtAsc(UUID tenantId, UUID sessionId);

    /**
     * The newest messages of the session in these roles, newest first (W-114.4): the history sent to the model is the
     * last 20 {@code USER} and {@code ASSISTANT} rows.
     */
    List<AiMessage> findByTenantIdAndSessionIdAndRoleInOrderByCreatedAtDesc(
            UUID tenantId, UUID sessionId, Collection<AiMessageRole> roles, Pageable pageable);
}
