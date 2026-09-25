package com.infinevo.core.report;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes rows into one file format, pulling them one at a time (W-23.1).
 *
 * <p>A writer never collects the stream. {@link CsvWriter} writes each row as it arrives;
 * {@link XlsxStreamingWriter} keeps a fixed window of rows in memory and flushes the rest to disk.
 * Memory therefore does not grow with the row count — the difference from every frozen export, all of
 * which build the whole file in memory (W-23.1 spec section 1).
 */
public interface ReportWriter {

    /**
     * Writes a header row from {@code columns}, then every row in order, to {@code out}.
     *
     * @return how many data rows were written, header excluded
     */
    long write(List<ReportColumn> columns, Stream<Map<String, Object>> rows, OutputStream out) throws IOException;

    /** The writer for a format. */
    static ReportWriter of(ExportFormat format) {
        return switch (format) {
            case CSV -> new CsvWriter();
            case XLSX -> new XlsxStreamingWriter();
        };
    }

    /**
     * A value as text, the same way in both formats.
     *
     * <p>A {@link BigDecimal} goes through {@code toPlainString}: never a double, and never
     * {@code 1E+3}. Dates and instants are ISO-8601. An array — {@code changed_columns} — is joined.
     */
    static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof LocalDate || value instanceof Instant) {
            return value.toString();
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(ReportWriter::text).collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }
}
