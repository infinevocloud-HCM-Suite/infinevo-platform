package com.infinevo.core.employeeimport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the bulk employee import file (W-73.7 §2): the template's header, then one employee per line.
 *
 * <p>Pure: no Spring, no database. It checks the shape of the file — the header, the row count, the
 * quoting — and offers the field readers the validator uses (dates, Y/N, roles). Whether a row's values
 * make an employee is {@link EmployeeImportServiceImpl}'s question.
 *
 * <p>Its own reader rather than the leave import's: that one is a private {@code split(",")}
 * ({@code LeaveImportServiceImpl.parseCsvLine}), which breaks on a quoted name such as
 * {@code "Rao, Jr."}. This one honours RFC 4180 quotes.
 */
public final class EmployeeImportParser {

    /** The template's columns, in order (spec §2). */
    public static final List<String> COLUMNS = List.of(
            "employee_number",
            "first_name",
            "last_name",
            "work_email",
            "mobile",
            "date_of_joining",
            "department",
            "designation",
            "location",
            "give_access",
            "roles");

    /** Out of scope beyond this (spec §2). */
    public static final int MAX_ROWS = 1000;

    /** The template file: the header and one example row. */
    public static final String TEMPLATE = String.join(",", COLUMNS) + "\n"
            + "EMP-1001,Asha,Rao,asha.rao@example.com,9876543210,2026-04-01,ENG,SE,BLR,Y,hr;manager\n";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private EmployeeImportParser() {}

    /**
     * The data rows of {@code csv}. Blank lines are skipped; row numbers count from the first data line
     * as 1, the way a spreadsheet user reads "row 3" — the header is not a row.
     *
     * @throws EmployeeImportFileException when the file is empty, its header is not the template's, a
     *     row has the wrong number of columns or an unclosed quote, or it holds more than
     *     {@value #MAX_ROWS} rows
     */
    public static List<EmployeeImportRow> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new EmployeeImportFileException("The file is empty");
        }
        String text = csv.startsWith("﻿") ? csv.substring(1) : csv;
        List<List<String>> records = records(text);
        if (records.isEmpty()) {
            throw new EmployeeImportFileException("The file is empty");
        }
        List<String> header = records.get(0).stream()
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!header.equals(COLUMNS)) {
            throw new EmployeeImportFileException(
                    "The header must be exactly: " + String.join(",", COLUMNS) + " — download the template");
        }
        List<EmployeeImportRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> fields = records.get(i);
            if (fields.size() == 1 && fields.get(0).isBlank()) {
                continue;
            }
            int rowNumber = rows.size() + 1;
            if (fields.size() != COLUMNS.size()) {
                throw new EmployeeImportFileException(
                        "Row " + rowNumber + " has " + fields.size() + " columns; the template has " + COLUMNS.size());
            }
            if (rows.size() == MAX_ROWS) {
                throw new EmployeeImportFileException(
                        "The file holds more than " + MAX_ROWS + " rows; split it and import each part");
            }
            rows.add(new EmployeeImportRow(
                    rowNumber,
                    blankToNull(fields.get(0)),
                    blankToNull(fields.get(1)),
                    blankToNull(fields.get(2)),
                    blankToNull(fields.get(3)),
                    blankToNull(fields.get(4)),
                    blankToNull(fields.get(5)),
                    blankToNull(fields.get(6)),
                    blankToNull(fields.get(7)),
                    blankToNull(fields.get(8)),
                    blankToNull(fields.get(9)),
                    blankToNull(fields.get(10))));
        }
        if (rows.isEmpty()) {
            throw new EmployeeImportFileException("The file has a header and no rows");
        }
        return rows;
    }

    /** {@code 2026-04-01}, {@code 01/04/2026} or {@code 01-04-2026}; empty for anything else. */
    public static Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value.trim(), format));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    /** {@code Y}/{@code Yes} true, {@code N}/{@code No} or blank false, any case; empty for anything else. */
    public static Optional<Boolean> parseYesNo(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(false);
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "Y", "YES" -> Optional.of(true);
            case "N", "NO" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    /** {@code hr;manager} as {@code [hr, manager]}: lower-cased, trimmed, blanks and repeats dropped. */
    public static List<String> splitRoles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String part : value.split(";")) {
            String code = part.trim().toLowerCase(Locale.ROOT);
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return List.copyOf(codes);
    }

    /** RFC 4180 records: commas split, double quotes enclose, {@code ""} is a quote, CRLF or LF ends a line. */
    static List<List<String>> records(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\r') {
                // dropped; the \n that follows ends the line
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                records.add(fields);
                fields = new ArrayList<>();
                line++;
            } else {
                field.append(c);
            }
        }
        if (quoted) {
            throw new EmployeeImportFileException("A quote opened on line " + line + " is never closed");
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
