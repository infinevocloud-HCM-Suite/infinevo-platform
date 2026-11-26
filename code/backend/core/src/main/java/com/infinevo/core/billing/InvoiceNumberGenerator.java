package com.infinevo.core.billing;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Generates sequential Indian financial-year invoice numbers ({@code INF/YYYY-YY/0001}, W-94.1).
 */
@Component
public class InvoiceNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /** Formats the Indian financial year containing {@code date}, e.g. {@code 2026-27} for 2026-04-01..2027-03-31. */
    public static String financialYear(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        int endShort = (startYear + 1) % 100;
        return String.format("%04d-%02d", startYear, endShort);
    }

    public String nextNumber(LocalDate invoiceDate) {
        String fy = financialYear(invoiceDate);
        return jdbcTemplate.queryForObject("SELECT core.next_billing_invoice_number(?)", String.class, fy);
    }
}
