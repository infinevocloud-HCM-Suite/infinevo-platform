package com.infinevo.core.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RFC 4180 CSV, UTF-8, one row written as it is pulled (W-23.1).
 *
 * <p><strong>Formula injection is closed.</strong> A value beginning {@code =}, {@code +},
 * {@code -}, {@code @}, a tab or a carriage return is prefixed with a single quote, so a spreadsheet
 * shows it as text instead of evaluating it. That is a real path from user-supplied text — a leave
 * reason, a department name — to code running on the laptop of whoever opens the file (spec section 7).
 *
 * <p>The file opens with a UTF-8 byte-order mark: without one, Excel reads UTF-8 as the local code
 * page and every name outside ASCII — Devanagari included — arrives garbled.
 */
public class CsvWriter implements ReportWriter {

    private static final String NEWLINE = "\r\n";
    static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    @Override
    public long write(List<ReportColumn> columns, Stream<Map<String, Object>> rows, OutputStream out)
            throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(BYTE_ORDER_MARK);
        writeLine(writer, columns.stream().map(ReportColumn::label).toList());

        long count = 0;
        Iterator<Map<String, Object>> iterator = rows.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> row = iterator.next();
            writeLine(
                    writer,
                    columns.stream()
                            .map(column -> ReportWriter.text(row.get(column.name())))
                            .toList());
            count++;
        }
        writer.flush();
        return count;
    }

    private static void writeLine(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values.get(i)));
        }
        writer.write(NEWLINE);
    }

    /** Neutralises a formula, then quotes the value if it holds a comma, a quote or a line break. */
    static String escape(String value) {
        String safe = neutraliseFormula(value);
        boolean quote =
                safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        if (!quote) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    static String neutraliseFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
