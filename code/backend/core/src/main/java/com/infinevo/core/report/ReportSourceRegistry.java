package com.infinevo.core.report;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Every {@link ReportSource} bean in the context, by {@link ReportSource#code()} (W-23.1).
 *
 * <p>Built once at startup from whatever sources the running modules contribute. Two sources claiming
 * one code <strong>fail startup</strong>: a definition names its source by code, and a code that meant
 * one query yesterday and another today would export the wrong data with nothing to say so.
 */
@Component
public class ReportSourceRegistry {

    private final Map<String, ReportSource> byCode;

    public ReportSourceRegistry(List<ReportSource> sources) {
        Map<String, ReportSource> map = new TreeMap<>();
        for (ReportSource source : sources == null ? List.<ReportSource>of() : sources) {
            ReportSource previous = map.putIfAbsent(source.code(), source);
            if (previous != null) {
                throw new IllegalStateException("Two report sources claim the code '" + source.code() + "': "
                        + previous.getClass().getName() + " and "
                        + source.getClass().getName());
            }
        }
        this.byCode = Collections.unmodifiableMap(map);
    }

    public Optional<ReportSource> find(String code) {
        return Optional.ofNullable(code == null ? null : byCode.get(code));
    }

    /** Every registered code, sorted. */
    public List<String> codes() {
        return List.copyOf(byCode.keySet());
    }
}
