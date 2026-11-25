package com.infinevo.core.ai;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The chat message being answered on this thread (W-114.8): which session it is, and the action a tool proposed while
 * answering it. {@link AiChatServiceImpl} opens it around the tool loop, so an action can be proposed only by a tool
 * run inside a chat message, and the answer can carry the card and link the intent to its assistant message.
 *
 * <p>Only this package can open a turn. At most one action is proposed per message.
 */
public final class AiChatTurn implements AutoCloseable {

    private static final ThreadLocal<AiChatTurn> CURRENT = new ThreadLocal<>();

    private final UUID tenantId;
    private final UUID userId;
    private final UUID sessionId;
    private AiProposedAction proposed;

    private AiChatTurn(UUID tenantId, UUID userId, UUID sessionId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    }

    /** Opens the turn of one chat message on this thread; close it when the answer is stored. */
    static AiChatTurn open(UUID tenantId, UUID userId, UUID sessionId) {
        AiChatTurn turn = new AiChatTurn(tenantId, userId, sessionId);
        CURRENT.set(turn);
        return turn;
    }

    /** The chat message being answered on this thread, if any. */
    public static Optional<AiChatTurn> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    /** The action proposed while answering, or {@code null}. */
    public AiProposedAction proposed() {
        return proposed;
    }

    /** Records the action proposed while answering. A second proposal in one message is refused. */
    public void recordProposal(AiProposedAction action) {
        Objects.requireNonNull(action, "action must not be null");
        if (proposed != null) {
            throw new IllegalStateException("An action was already proposed in this message");
        }
        proposed = action;
    }

    @Override
    public void close() {
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
    }
}
