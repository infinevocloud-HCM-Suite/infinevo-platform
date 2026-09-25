package com.infinevo.core.report.source;

import com.infinevo.core.report.ReportColumn;
import com.infinevo.core.report.ReportDefinitionService;
import com.infinevo.core.report.ReportFilters;
import com.infinevo.core.report.ReportSource;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Departments, designations and work locations, one row each (W-23.1) — the second Core consumer
 * (decision D3). HRMS never had these as records at all: department and job title were free text.
 *
 * <p>One query per kind, run one after another as the stream is read ({@code flatMap} opens each only
 * when the previous is drained, and closes it after).
 */
@Component
public class OrgMasterReportSource implements ReportSource {

    public static final String CODE = "org_master";

    static final String FILTER_KIND = "kind";
    static final String FILTER_ACTIVE_ONLY = "activeOnly";

    static final List<ReportColumn> COLUMNS = List.of(
            ReportColumn.text("kind", "Kind"),
            ReportColumn.text("code", "Code"),
            ReportColumn.text("name", "Name"),
            new ReportColumn("active", "Active", ReportColumn.Type.BOOLEAN));

    /** The filter value, and the entity it reads. Entity names are constants, never taken from input. */
    private static final Map<String, String> KINDS = orderedKinds();

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
        return Set.of(FILTER_KIND, FILTER_ACTIVE_ONLY);
    }

    @Override
    public Stream<Map<String, Object>> rows(ReportFilters filters) {
        UUID tenantId = TenantContext.require();
        List<String> kinds =
                filters.get(FILTER_KIND).map(kind -> List.of(kind(kind))).orElse(List.copyOf(KINDS.keySet()));
        boolean activeOnly =
                filters.get(FILTER_ACTIVE_ONLY).map(OrgMasterReportSource::bool).orElse(false);

        return kinds.stream().flatMap(kind -> entityManager
                .createQuery(
                        "select m.code, m.name, m.active from " + KINDS.get(kind) + " m where m.tenantId = :tenant"
                                + (activeOnly ? " and m.active = true" : "")
                                + " order by m.code",
                        Object[].class)
                .setParameter("tenant", tenantId)
                .setHint(Streaming.FETCH_SIZE, Streaming.FETCH_ROWS)
                .setHint(Streaming.READ_ONLY, true)
                .getResultStream()
                .map(r -> row(kind, r)));
    }

    private static Map<String, Object> row(String kind, Object[] r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("code", r[0]);
        row.put("name", r[1]);
        row.put("active", r[2]);
        return row;
    }

    private static String kind(String value) {
        String kind = value.toLowerCase(Locale.ROOT);
        if (!KINDS.containsKey(kind)) {
            throw new ReportDefinitionService.ValidationException(
                    Map.of(FILTER_KIND, "kind must be one of " + KINDS.keySet()));
        }
        return kind;
    }

    private static boolean bool(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new ReportDefinitionService.ValidationException(
                Map.of(FILTER_ACTIVE_ONLY, "activeOnly must be true or false"));
    }

    private static Map<String, String> orderedKinds() {
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put("department", "Department");
        kinds.put("designation", "Designation");
        kinds.put("work_location", "WorkLocation");
        return java.util.Collections.unmodifiableMap(kinds);
    }
}
