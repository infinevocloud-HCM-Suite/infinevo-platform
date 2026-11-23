package com.infinevo.core.ai;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * The user's own {@code USER} messages stored at or after {@code since}, across all their chats (W-114.4, spec
     * "Per-user rate limit"). {@code core.ai_message} has no user column, so the user is found through the session.
     */
    @Query("SELECT COUNT(m) FROM AiMessage m WHERE m.tenantId = :tenantId"
            + " AND m.role = com.infinevo.core.ai.AiMessageRole.USER AND m.createdAt >= :since"
            + " AND m.sessionId IN (SELECT s.id FROM AiSession s WHERE s.tenantId = :tenantId AND s.userId = :userId)")
    long countUserMessagesSince(
            @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("since") Instant since);

    /**
     * When the oldest of those messages was stored, or empty when there is none: the minute limit's {@code Retry-After}
     * runs until it is 60 seconds old. Read only when a message is refused.
     */
    @Query("SELECT MIN(m.createdAt) FROM AiMessage m WHERE m.tenantId = :tenantId"
            + " AND m.role = com.infinevo.core.ai.AiMessageRole.USER AND m.createdAt >= :since"
            + " AND m.sessionId IN (SELECT s.id FROM AiSession s WHERE s.tenantId = :tenantId AND s.userId = :userId)")
    Optional<Instant> findOldestUserMessageSince(
            @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("since") Instant since);
}
