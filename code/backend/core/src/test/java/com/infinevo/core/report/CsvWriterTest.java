package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-23.1 spec section 7 — commas, quotes and newlines in values are escaped; a value beginning
 * {@code =} is not written as a formula. Plus what a spreadsheet user meets first: the header, the
 * encoding, and an empty result being a file rather than an error.
 */
class CsvWriterTest {

    private static final List<ReportColumn> COLUMNS =
            List.of(ReportColumn.text("name", "Name"), ReportColumn.text("note", "Note"));

    @Test
    @DisplayName("Commas, quotes and line breaks are quoted per RFC 4180")
    void specialCharactersAreQuoted() {
        assertThat(CsvWriter.escape("plain")).isEqualTo("plain");
        assertThat(CsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.escape("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    @DisplayName("A value that a spreadsheet would run as a formula is written as text")
    void formulasAreNeutralised() {
        assertThat(CsvWriter.escape("=HYPERLINK(\"http://evil\")")).startsWith("\"'=");
        assertThat(CsvWriter.escape("+1+1")).isEqualTo("'+1+1");
        assertThat(CsvWriter.escape("-2+3")).isEqualTo("'-2+3");
        assertThat(CsvWriter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvWriter.escape("\tcmd")).isEqualTo("'\tcmd");
        assertThat(CsvWriter.escape("")).isEmpty();
        assertThat(CsvWriter.escape("a=b"))
                .as("only a leading sign is a formula")
                .isEqualTo("a=b");
    }

    @Test
    @DisplayName("Header, then one line per row, CRLF, UTF-8 with a byte-order mark; the count is the data rows")
    void writesHeaderAndRows() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = new CsvWriter().write(COLUMNS, Stream.of(row("Asha", "leave, approved"), row("अनीता", null)), out);

        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(count).isEqualTo(2);
        assertThat(text).startsWith(String.valueOf(CsvWriter.BYTE_ORDER_MARK));
        assertThat(text.substring(1)).isEqualTo("Name,Note\r\nAsha,\"leave, approved\"\r\nअनीता,\r\n");
    }

    @Test
    @DisplayName("An empty result is a header-only file, not an error")
    void emptyResultIsHeaderOnly() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = new CsvWriter().write(COLUMNS, Stream.empty(), out);

        assertThat(count).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(CsvWriter.BYTE_ORDER_MARK + "Name,Note\r\n");
    }

    @Test
    @DisplayName("Money is written from its BigDecimal as plain text, dates as ISO-8601")
    void valuesAreFormattedWithoutLoss() {
        assertThat(ReportWriter.text(new BigDecimal("12345678901234.5678"))).isEqualTo("12345678901234.5678");
        assertThat(ReportWriter.text(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(ReportWriter.text(LocalDate.of(2026, 4, 1))).isEqualTo("2026-04-01");
        assertThat(ReportWriter.text(new String[] {"first_name", "status"})).isEqualTo("first_name, status");
        assertThat(ReportWriter.text(null)).isEmpty();
    }

    private static Map<String, Object> row(String name, String note) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("note", note);
        return row;
    }
}
