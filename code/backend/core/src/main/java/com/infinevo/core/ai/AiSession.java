package com.infinevo.core.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One AI helper chat of one user (W-114.3) - {@code core.ai_session} (V200).
 *
 * <p>{@code userId} is the signed-in user's token subject; {@code employeeId} is empty for a user with no employee
 * record. {@code lastMessageAt} stays empty until the first message ({@code W-114.4}); the list orders such a session
 * by its {@code createdAt}.
 */
@Entity
@Table(
        name = "ai_session",
        schema = "core",
        indexes = {@Index(name = "idx_ai_session_tenant_user", columnList = "tenant_id, user_id, last_message_at")})
public class AiSession {

    public static final int MAX_TITLE = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "employee_id", updatable = false)
    private UUID employeeId;

    @Column(name = "title", length = MAX_TITLE)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected AiSession() {}

    public AiSession(UUID tenantId, UUID userId, UUID employeeId, String title) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.employeeId = employeeId;
        this.title = title;
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

    public UUID getUserId() {
        return userId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    /** Called when a message is stored ({@code W-114.4}), so the list puts this chat first. */
    public void touch(Instant at) {
        this.lastMessageAt = Objects.requireNonNull(at, "at must not be null");
    }
}
