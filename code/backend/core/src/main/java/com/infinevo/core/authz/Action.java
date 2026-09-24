package com.infinevo.core.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One thing the platform can do (W-11.1) — {@code reference.action},
 * {@code migration/src/main/resources/db/migration/reference/V020__action.sql}.
 *
 * <p><strong>Read-only, and {@link Immutable} so Hibernate agrees.</strong> An action is code, not
 * data: the catalogue is seeded by the migration and only a release changes it (spec section 4). The
 * frozen Payroll created actions at runtime with no seed
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/action/ActionServiceImpl.java:24-34});
 * nothing here can, and {@code app_user} holds only {@code SELECT} on {@code reference} besides.
 *
 * <p>No {@code tenant_id}, deliberately: {@code reference} is the {@code D-08} exemption and every
 * tenant sees the same list (spec section 13, decision 1).
 */
@Entity
@Immutable
@Table(
        name = "action",
        schema = "reference",
        indexes = {@Index(name = "idx_action_module", columnList = "module")})
public class Action {

    /** {@code <module>.<resource>.<verb>} — the check constraint in {@code V020} enforces the shape. */
    @Id
    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128, updatable = false)
    private String name;

    /** {@code core}, {@code hrms} or {@code payroll}. A label, not a module dependency (spec section 11). */
    @Column(name = "module", nullable = false, length = 16, updatable = false)
    private String module;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100, updatable = false)
    private String updatedBy;

    protected Action() {}

    /** For unit tests only — the application never builds an action, it reads them. */
    Action(String code, String name, String module, String description) {
        this.code = code;
        this.name = name;
        this.module = module;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
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
