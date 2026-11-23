package com.infinevo.core.ai;

import java.util.List;
import java.util.UUID;
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
}
