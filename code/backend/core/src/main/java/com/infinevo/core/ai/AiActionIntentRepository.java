package com.infinevo.core.ai;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for {@link AiActionIntent} (W-114.3). */
@Repository
public interface AiActionIntentRepository extends JpaRepository<AiActionIntent, UUID> {

    /**
     * The caller's intent, row-locked ({@code SELECT ... FOR UPDATE}) for the confirm step of {@code W-114.8}, so two
     * confirms of one intent cannot both run it. Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AiActionIntent i WHERE i.id = :id AND i.tenantId = :tenantId AND i.userId = :userId")
    Optional<AiActionIntent> findByIdAndTenantIdAndUserIdForUpdate(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** The intents proposed by these messages - the {@code proposedAction} of the messages list. */
    List<AiActionIntent> findByTenantIdAndMessageIdIn(UUID tenantId, Collection<UUID> messageIds);
}
