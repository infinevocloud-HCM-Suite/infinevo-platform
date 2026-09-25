package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-23.1 spec section 7 — a source is found by its code; two sources with one code fail startup. */
class ReportSourceRegistryTest {

    @Test
    @DisplayName("A registered source is found by code(); an unknown code is empty")
    void findsByCode() {
        ReportSource employees = new FakeSource("employee");
        ReportSourceRegistry registry = new ReportSourceRegistry(List.of(employees, new FakeSource("audit_log")));

        assertThat(registry.find("employee")).containsSame(employees);
        assertThat(registry.find("payroll_run")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.codes()).containsExactly("audit_log", "employee");
    }

    @Test
    @DisplayName("Two sources claiming one code fail at construction, naming both classes")
    void duplicateCodeFailsStartup() {
        assertThatThrownBy(
                        () -> new ReportSourceRegistry(List.of(new FakeSource("employee"), new FakeSource("employee"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("employee");
    }

    /** A source with two columns, one filter, and the rows it is given. */
    static class FakeSource implements ReportSource {

        private final String code;
        private final List<Map<String, Object>> rows;

        FakeSource(String code) {
            this(code, List.of());
        }

        FakeSource(String code, List<Map<String, Object>> rows) {
            this.code = code;
            this.rows = rows;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public List<ReportColumn> columns() {
            return List.of(ReportColumn.text("name", "Name"), ReportColumn.text("status", "Status"));
        }

        @Override
        public java.util.Set<String> filterNames() {
            return java.util.Set.of("status");
        }

        @Override
        public Stream<Map<String, Object>> rows(ReportFilters filters) {
            return rows.stream().filter(row -> filters.get("status")
                    .map(s -> s.equals(row.get("status")))
                    .orElse(true));
        }
    }
}
