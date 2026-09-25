package com.infinevo.core.report.source;

import com.infinevo.core.report.ReportColumn;
import com.infinevo.core.report.ReportDefinitionService;
import com.infinevo.core.report.ReportFilters;
import com.infinevo.core.report.ReportSource;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The audit trail, one row per captured change (W-23.1) — the third Core consumer (decision D3).
 *
 * <p><strong>Which columns changed, never the values.</strong> {@code old_values} and
 * {@code new_values} are not exported: they already withhold identity numbers and bank details
 * ({@code AuditWriter}), but a spreadsheet is copied, mailed and forgotten in ways the API never is,
 * and "who changed what, when" is what an auditor asks for.
 *
 * <p>The definition {@code V040} seeds over this source requires {@code core.audit.read}, the same action as
 * {@code GET /api/v1/audit}, so the export is no wider than the screen.
 */
@Component
public class AuditLogReportSource implements ReportSource {

    public static final String CODE = "audit_log";

    static final String FILTER_FROM = "from";
    static final String FILTER_TO = "to";
    static final String FILTER_ENTITY = "entity";

    static final List<ReportColumn> COLUMNS = List.of(
            new ReportColumn("occurred_at", "Occurred at", ReportColumn.Type.TIMESTAMP),
            ReportColumn.text("actor", "Actor"),
            ReportColumn.text("operation", "Operation"),
            ReportColumn.text("entity_schema", "Schema"),
            ReportColumn.text("entity_table", "Table"),
            ReportColumn.text("entity_id", "Record id"),
            ReportColumn.text("changed_columns", "Changed columns"));

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<ReportColumn> columns() {
        return COLUMNS;
    }

    @Override
    public Set<String> filterNames() {
        return Set.of(FILTER_FROM, FILTER_TO, FILTER_ENTITY);
    }

    @Override
    public Stream<Map<String, Object>> rows(ReportFilters filters) {
        UUID tenantId = TenantContext.require();
        Instant from =
                filters.get(FILTER_FROM).map(v -> instant(FILTER_FROM, v)).orElse(null);
        Instant to = filters.get(FILTER_TO).map(v -> instant(FILTER_TO, v)).orElse(null);
        String entity = filters.get(FILTER_ENTITY).orElse(null);

        String jpql = "select a.occurredAt, a.actorLabel, a.operation, a.entitySchema, a.entityTable,"
                + " a.entityId, a.changedColumns from AuditLog a where a.tenantId = :tenant"
                + (from == null ? "" : " and a.occurredAt >= :from")
                + (to == null ? "" : " and a.occurredAt <= :to")
                + (entity == null ? "" : " and a.entityTable = :entity")
                + " order by a.occurredAt desc, a.id desc";
        TypedQuery<Object[]> query = entityManager
                .createQuery(jpql, Object[].class)
                .setParameter("tenant", tenantId)
                .setHint(Streaming.FETCH_SIZE, Streaming.FETCH_ROWS)
                .setHint(Streaming.READ_ONLY, true);
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        if (entity != null) {
            query.setParameter("entity", entity);
        }
        return query.getResultStream().map(AuditLogReportSource::row);
    }

    private static Map<String, Object> row(Object[] r) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            row.put(COLUMNS.get(i).name(), r[i]);
        }
        return row;
    }

    /** {@code 2026-09-01} (the start of that day, UTC) or a full ISO-8601 instant. */
    static Instant instant(String filter, String value) {
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ReportDefinitionService.ValidationException(
                    Map.of(filter, filter + " must be a date such as 2026-09-01 or an ISO-8601 instant"));
        }
    }
}
