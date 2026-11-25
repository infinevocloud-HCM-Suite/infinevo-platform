package com.infinevo.core.ai;

import java.util.UUID;

/** Opens an {@link AiChatTurn} for tests outside this package, as {@link AiChatServiceImpl} does around a message. */
public final class AiChatTurns {

    private AiChatTurns() {}

    public static AiChatTurn open(UUID tenantId, UUID userId, UUID sessionId) {
        return AiChatTurn.open(tenantId, userId, sessionId);
    }
}
