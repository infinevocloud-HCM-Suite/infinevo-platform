package com.infinevo.core.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AiSession} (W-114.3). Every read names the tenant and the user: a chat belongs to one user,
 * and row-level security on {@code core.ai_session} limits it to the bound tenant besides.
 */
@Repository
public interface AiSessionRepository extends JpaRepository<AiSession, UUID> {

    Optional<AiSession> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    /**
     * The user's chats, newest activity first. A chat with no message yet has no {@code last_message_at} and is
     * ordered by when it was opened, so a new chat comes first rather than last.
     */
    @Query(
            value = "SELECT s FROM AiSession s WHERE s.tenantId = :tenantId AND s.userId = :userId"
                    + " ORDER BY COALESCE(s.lastMessageAt, s.createdAt) DESC, s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM AiSession s WHERE s.tenantId = :tenantId AND s.userId = :userId")
    Page<AiSession> findByTenantIdAndUserIdOrderByLastMessageAtDesc(
            @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, Pageable pageable);
}
