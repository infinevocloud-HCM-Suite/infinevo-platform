package com.infinevo.core.report;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The seam a module implements to make its data exportable without {@code core} knowing the module
 * exists (W-23.1, {@code 12-core-contracts.md:108}). A {@code payroll} source is a {@code payroll}
 * bean; {@code core} finds it by {@link #code()} and never imports it.
 *
 * <p><strong>A bean, not an enum</strong> (corrected 2026-09-25): an enum in {@code core} would have
 * to name every module's sources, which is the dependency the module graph forbids.
 *
 * <p>Rules every implementation keeps:
 *
 * <ul>
 *   <li>{@link #rows} runs with the tenant already bound and the transaction open; it names the tenant
 *       in its own query as well as relying on row-level security, as every repository here does.
 *   <li>{@link #rows} returns a lazy {@link Stream}. The writer pulls, and the source never
 *       materialises the result — the streaming guarantee starts here, not at the writer.
 *   <li>Every row map is keyed by {@link ReportColumn#name()} from {@link #columns()}.
 *   <li>A filter the source does not declare in {@link #filterNames()} is refused, not ignored, so a
 *       typo cannot quietly export everything.
 * </ul>
 */
public interface ReportSource {

    /** Stored in {@code report_definition.source}. Unique across every registered source. */
    String code();

    /** The allow-list a definition chooses its columns from, in their natural order. */
    List<ReportColumn> columns();

    /** The filters {@link #rows} understands. Empty when it takes none. */
    default Set<String> filterNames() {
        return Set.of();
    }

    /**
     * The rows, under the bound tenant. The caller closes the stream.
     *
     * @throws ReportDefinitionService.ValidationException for a filter value the source cannot read
     */
    Stream<Map<String, Object>> rows(ReportFilters filters);
}
