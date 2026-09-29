package com.infinevo.core.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Join entity between a user invitation and a granted role (W-24.2, spec section 6).
 */
@Entity
@Table(name = "user_invitation_role", schema = "core")
public class UserInvitationRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invitation_id", nullable = false, updatable = false)
    private UUID invitationId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected UserInvitationRole() {}

    public UserInvitationRole(UUID tenantId, UUID invitationId, UUID roleId, String creator) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.invitationId = Objects.requireNonNull(invitationId, "invitationId");
        this.roleId = Objects.requireNonNull(roleId, "roleId");
        this.createdBy = creator != null ? creator : "system";
        this.updatedBy = this.createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getInvitationId() {
        return invitationId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
