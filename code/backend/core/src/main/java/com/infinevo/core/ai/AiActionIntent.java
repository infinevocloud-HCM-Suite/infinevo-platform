package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An action the helper proposed and the user has still to confirm (W-114.3) - {@code core.ai_action_intent} (V200).
 *
 * <p>This part only maps the table. {@code W-114.8} creates intents, fills {@code messageId} with the assistant
 * message that proposed it, and moves the status under the row lock of
 * {@link AiActionIntentRepository#findByIdAndTenantIdAndUserIdForUpdate}.
 */
@Entity
@Table(
        name = "ai_action_intent",
        schema = "core",
        indexes = {@Index(name = "idx_ai_intent_tenant_session", columnList = "tenant_id, session_id")})
public class AiActionIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "action_code", nullable = false, updatable = false, length = 100)
    private String actionCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AiIntentStatus status = AiIntentStatus.PROPOSED;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private JsonNode result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiActionIntent() {}

    public AiActionIntent(
            UUID tenantId,
            UUID sessionId,
            UUID userId,
            UUID messageId,
            String actionCode,
            JsonNode payload,
            Instant expiresAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.messageId = messageId;
        this.actionCode = Objects.requireNonNull(actionCode, "actionCode must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public AiIntentStatus getStatus() {
        return status;
    }

    public void setStatus(AiIntentStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public JsonNode getResult() {
        return result;
    }

    public void setResult(JsonNode result) {
        this.result = result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
