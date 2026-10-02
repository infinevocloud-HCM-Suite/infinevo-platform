package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared date, period, and carry-forward calculation utilities for leave management.
 */
public final class LeaveDateUtils {

    private LeaveDateUtils() {}

    /**
     * Resolves the leave year start month for a tenant from {@code core.tenant}, defaulting to April (4).
     */
    public static int getTenantLeaveYearStartMonth(JdbcTemplate jdbcTemplate, UUID tenantId) {
        if (jdbcTemplate != null && tenantId != null) {
            try {
                java.util.List<Integer> results = jdbcTemplate.query(
                        "SELECT leave_year_start_month FROM core.tenant WHERE tenant_id = ?",
                        (rs, rowNum) -> {
                            int val = rs.getInt(1);
                            return rs.wasNull() ? null : val;
                        },
                        tenantId);
                if (!results.isEmpty() && results.get(0) != null) {
                    int m = results.get(0);
                    if (m >= 1 && m <= 12) {
                        return m;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return 4;
    }

    /**
     * Computes the start date of the current in-progress leave year as of a given date and start month.
     */
    public static LocalDate getCurrentLeaveYearStartDate(LocalDate asOf, int startMonth) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        int year = asOf.getYear();
        if (asOf.getMonthValue() < startMonth) {
            year--;
        }
        return LocalDate.of(year, startMonth, 1);
    }

    /**
     * Computes the end date of the current in-progress leave year as of a given date and start month.
     */
    public static LocalDate getCurrentLeaveYearEndDate(LocalDate asOf, int startMonth) {
        return getCurrentLeaveYearStartDate(asOf, startMonth).plusYears(1).minusDays(1);
    }

    /**
     * Validates and returns the canonical spelling of the leave year.
     * <ul>
     *   <li>Start month 1 (January): strictly {@code YYYY} (e.g. {@code 2026}).</li>
     *   <li>Start month != 1 (e.g. April): strictly {@code YYYY-YY} (e.g. {@code 2026-27}).</li>
     * </ul>
     */
    public static String canonicalizeLeaveYear(int startMonth, String leaveYear) {
        Objects.requireNonNull(leaveYear, "leaveYear must not be null");
        String trimmed = leaveYear.trim();
        if (startMonth == 1) {
            if (!trimmed.matches("^\\d{4}$")) {
                throw new IllegalArgumentException("Invalid leave year format '" + leaveYear
                        + "'. Tenant starting in January requires YYYY (e.g. 2026)");
            }
            return trimmed;
        } else {
            if (!trimmed.matches("^\\d{4}-\\d{2}$")) {
                throw new IllegalArgumentException("Invalid leave year format '" + leaveYear
                        + "'. Tenant starting in month " + startMonth + " requires YYYY-YY (e.g. 2026-27)");
            }
            int startYear = Integer.parseInt(trimmed.substring(0, 4));
            int endYearSuffix = Integer.parseInt(trimmed.substring(5, 7));
            int expectedSuffix = (startYear + 1) % 100;
            if (endYearSuffix != expectedSuffix) {
                throw new IllegalArgumentException("Invalid leave year format '" + leaveYear
                        + "': consecutive year suffix must be " + String.format(Locale.ROOT, "%02d", expectedSuffix));
            }
            return trimmed;
        }
    }

    /**
     * Calculates the continuous zero-indexed period index for a given date, start month, and reset frequency.
     */
    public static int getPeriodIndex(LocalDate date, int startMonth, ResetFrequency freq) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(freq, "freq must not be null");

        int monthOffset = (date.getMonthValue() - startMonth + 12) % 12;
        int leaveYear = date.getMonthValue() < startMonth ? date.getYear() - 1 : date.getYear();

        return switch (freq) {
            case MONTHLY -> (date.getYear() * 12) + date.getMonthValue();
            case QUARTERLY -> (leaveYear * 4) + (monthOffset / 3);
            case HALF_YEARLY -> (leaveYear * 2) + (monthOffset / 6);
            case YEARLY -> leaveYear;
        };
    }

    /**
     * Computes the effective carry-forward days that have not expired.
     * Counts only leave consumed on or before {@code carry_forward_expires_on} against carried days.
     */
    public static BigDecimal computeEffectiveCarriedForward(
            LeaveAllocation allocation,
            LocalDate asOf,
            BigDecimal totalConsumed,
            LeaveConsumptionRepository leaveConsumptionRepository,
            UUID tenantId) {
        if (allocation == null
                || allocation.getCarriedForwardDays() == null
                || allocation.getCarriedForwardDays().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal carried = allocation.getCarriedForwardDays();
        LocalDate expiresOn = allocation.getCarryForwardExpiresOn();

        if (expiresOn == null || !asOf.isAfter(expiresOn)) {
            return carried;
        }

        // Carried forward has expired. Only leave consumed on or before expiresOn counts against carried.
        BigDecimal consumedBeforeExpiry;
        if (leaveConsumptionRepository != null && allocation.getId() != null && tenantId != null) {
            consumedBeforeExpiry = leaveConsumptionRepository.sumConsumedDaysByAllocationAndConsumedOnLessThanEqual(
                    tenantId, allocation.getId(), expiresOn);
            if (consumedBeforeExpiry == null) {
                consumedBeforeExpiry = BigDecimal.ZERO;
            }
        } else {
            consumedBeforeExpiry = totalConsumed != null ? totalConsumed : BigDecimal.ZERO;
        }

        return carried.min(consumedBeforeExpiry);
    }
}
