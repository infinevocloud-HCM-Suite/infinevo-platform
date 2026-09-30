package com.infinevo.payroll.taxdeclaration;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value type for an Indian financial year (April 1 to March 31).
 *
 * <p>Canonical format is {@code "YYYY-YYYY"} (e.g. {@code "2025-2026"}), matching
 * {@code reference.tax_slab_master.financial_year}. Rejects non-consecutive or malformed inputs.
 */
public record FinancialYear(int startYear, int endYear) implements Comparable<FinancialYear> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");

    public FinancialYear {
        if (endYear != startYear + 1) {
            throw new IllegalArgumentException(
                    "Financial year must span consecutive years: " + startYear + "-" + endYear);
        }
    }

    /**
     * Parses a string of format "YYYY-YYYY" (e.g. "2025-2026").
     */
    public static FinancialYear parse(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Financial year label must not be null or blank");
        }
        Matcher matcher = PATTERN.matcher(label.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid financial year format: " + label + " (expected YYYY-YYYY)");
        }
        int start = Integer.parseInt(matcher.group(1));
        int end = Integer.parseInt(matcher.group(2));
        return new FinancialYear(start, end);
    }

    /**
     * Factory method for creating FinancialYear from consecutive start and end years.
     */
    public static FinancialYear of(int startYear, int endYear) {
        return new FinancialYear(startYear, endYear);
    }

    /**
     * Resolves the financial year containing the given date.
     * In India: April 1 onwards is current year to next year; January to March is previous year to current year.
     */
    public static FinancialYear of(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        int year = date.getYear();
        if (date.getMonthValue() >= Month.APRIL.getValue()) {
            return new FinancialYear(year, year + 1);
        } else {
            return new FinancialYear(year - 1, year);
        }
    }

    /**
     * April 1 of the start year.
     */
    public LocalDate start() {
        return LocalDate.of(startYear, Month.APRIL, 1);
    }

    /**
     * March 31 of the end year.
     */
    public LocalDate end() {
        return LocalDate.of(endYear, Month.MARCH, 31);
    }

    /**
     * Checks if a date falls strictly within this financial year (April 1 to March 31, inclusive).
     */
    public boolean contains(LocalDate date) {
        if (date == null) {
            return false;
        }
        return !date.isBefore(start()) && !date.isAfter(end());
    }

    /**
     * Formatted label, e.g. "2025-2026".
     */
    public String label() {
        return startYear + "-" + endYear;
    }

    @Override
    public String toString() {
        return label();
    }

    /**
     * Resolves all financial years from the year containing effectiveFrom up to the current financial year.
     * Required for salary version events spanning past financial years (W-33.3 § 13 decision 5).
     */
    public static java.util.List<String> allFrom(LocalDate effectiveFrom) {
        return allFrom(effectiveFrom, LocalDate.now());
    }

    public static java.util.List<String> allFrom(LocalDate effectiveFrom, LocalDate today) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        Objects.requireNonNull(today, "today must not be null");
        FinancialYear startFy = FinancialYear.of(effectiveFrom);
        FinancialYear currentFy = FinancialYear.of(today);
        if (startFy.compareTo(currentFy) > 0) {
            return java.util.List.of(startFy.label());
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        for (int y = startFy.startYear(); y <= currentFy.startYear(); y++) {
            list.add(new FinancialYear(y, y + 1).label());
        }
        return java.util.Collections.unmodifiableList(list);
    }

    @Override
    public int compareTo(FinancialYear o) {
        return Integer.compare(this.startYear, o.startYear);
    }
}
