package com.infinevo.core.report;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The filters an export runs with — a definition's defaults overlaid by the request's (W-23.1).
 *
 * <p>Plain strings, deliberately: each {@link ReportSource} parses the few it declares and refuses
 * the rest, so a filter is never interpolated into a query as anything but a bound parameter.
 */
public record ReportFilters(Map<String, String> values) {

    public static final ReportFilters NONE = new ReportFilters(Map.of());

    public ReportFilters {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    /** {@code defaults} with {@code overrides} laid over them; either may be null. */
    public static ReportFilters of(Map<String, String> defaults, Map<String, String> overrides) {
        Map<String, String> merged = new HashMap<>();
        if (defaults != null) {
            defaults.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    merged.put(k, v.trim());
                }
            });
        }
        if (overrides != null) {
            overrides.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    merged.put(k, v.trim());
                }
            });
        }
        return new ReportFilters(merged);
    }

    /** The value of one filter, if set and not blank. */
    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }
}
