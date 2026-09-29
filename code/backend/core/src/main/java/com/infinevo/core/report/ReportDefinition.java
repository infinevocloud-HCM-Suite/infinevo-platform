package com.infinevo.core.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A named export over one {@link ReportSource} (W-23.1) — {@code core.report_definition},
 * {@code migration/src/main/resources/db/migration/core/V040__report_definition.sql}.
 *
 * <p><strong>There is no legacy table behind this.</strong> {@code 02-data-model.md} mapped it to HRMS
 * {@code report}, which is the employee reporting hierarchy — {@code reportingManagerId}, three approver
 * levels — and has nothing to do with reports (spec section 1). Neither frozen product let anyone define
 * a report: every export's columns were code.
 *
 * <p>{@link #isSystem()} marks the three definitions {@code core.seed_report_definitions} creates for
 * every tenant; the service refuses to change them, as {@code RoleServiceImpl} refuses a system role.
 */
@Entity
@Table(
        name = "report_definition",
        schema = "core",
        indexes = {@Index(name = "uk_report_definition_tenant_code", columnList = "tenant_id, code", unique = true)})
public class ReportDefinition {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Fixed once created, like a role's code. */
    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns", nullable = false)
    private List<String> columns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_filters")
    private Map<String, String> defaultFilters;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 8)
    private ExportFormat format;

    @Column(name = "required_action", nullable = false, length = 64)
    private String requiredAction;

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected ReportDefinition() {}

    /** A tenant's own definition. Never a system one — those come only from the migration's seed. */
    ReportDefinition(UUID tenantId, String code, String actor) {
        this.tenantId = tenantId;
        this.code = code;
        this.system = false;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Package-private: only {@link ReportDefinitionServiceImpl} calls it, after validation. */
    void apply(
            String name,
            String source,
            List<String> columns,
            Map<String, String> defaultFilters,
            ExportFormat format,
            String requiredAction,
            String actor) {
        this.name = name;
        this.source = source;
        this.columns = List.copyOf(columns);
        this.defaultFilters = defaultFilters == null || defaultFilters.isEmpty() ? null : Map.copyOf(defaultFilters);
        this.format = format;
        this.requiredAction = requiredAction;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public List<String> getColumns() {
        return columns == null ? List.of() : List.copyOf(columns);
    }

    public Map<String, String> getDefaultFilters() {
        return defaultFilters == null ? Map.of() : Map.copyOf(defaultFilters);
    }

    public ExportFormat getFormat() {
        return format;
    }

    public String getRequiredAction() {
        return requiredAction;
    }

    public boolean isSystem() {
        return system;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
