package com.infinevo.payroll.deduction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The per-line rules of W-35.2 §4 that need nothing but the line and today's month — amount, period,
 * type, reason, remarks — and the batch size. The checks that read other data (the employee is active,
 * the document is theirs) are the service's. Pure, so {@code EmployeeDeductionRulesTest} needs no
 * Spring context.
 */
public final class EmployeeDeductionRules {

    /** The legacy grid's limit (§2). */
    public static final int MAX_LINES = 500;

    static final int REASON_MAX = 255;
    static final int REMARKS_MAX = 500;
    private static final int AMOUNT_MAX_SCALE = 2;

    /** Payroll months are Indian months: the zone decides which month "now" is. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    /** A line after parsing: every field typed and checked. */
    public record ParsedLine(
            UUID employeeId,
            YearMonth period,
            DeductionType deductionType,
            BigDecimal amount,
            String reason,
            String remarks,
            UUID documentId) {}

    private EmployeeDeductionRules() {}

    public static Clock defaultClock() {
        return Clock.system(ZONE);
    }

    /**
     * Every line of the batch parsed, in order.
     *
     * @param currentMonth this month; a period more than one month after it is refused
     * @throws EmployeeDeductionValidationException on the first failing line, naming it, or for an empty
     *     or oversized batch
     */
    public static List<ParsedLine> parseBatch(List<EmployeeDeductionLineRequest> lines, YearMonth currentMonth) {
        Objects.requireNonNull(currentMonth, "currentMonth must not be null");
        if (lines == null || lines.isEmpty()) {
            throw new EmployeeDeductionValidationException("A batch needs at least one line");
        }
        if (lines.size() > MAX_LINES) {
            throw new EmployeeDeductionValidationException(
                    "A batch has at most " + MAX_LINES + " lines; this one has " + lines.size());
        }
        List<ParsedLine> parsed = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            parsed.add(parseLine(i, lines.get(i), currentMonth));
        }
        return parsed;
    }

    static ParsedLine parseLine(int line, EmployeeDeductionLineRequest request, YearMonth currentMonth) {
        if (request == null) {
            throw new EmployeeDeductionValidationException(line, "the line is empty");
        }
        if (request.employeeId() == null) {
            throw new EmployeeDeductionValidationException(line, "employee_id is required");
        }
        YearMonth period = parsePeriod(line, request.period());
        if (period.isAfter(currentMonth.plusMonths(1))) {
            throw new EmployeeDeductionValidationException(
                    line, "period " + period + " is more than one month after " + currentMonth);
        }
        DeductionType type = parseType(line, request.deductionType());
        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new EmployeeDeductionValidationException(line, "amount must be greater than 0");
        }
        if (amount.stripTrailingZeros().scale() > AMOUNT_MAX_SCALE) {
            throw new EmployeeDeductionValidationException(line, "amount has at most 2 decimal places");
        }
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty() || reason.length() > REASON_MAX) {
            throw new EmployeeDeductionValidationException(line, "reason must be 1 to " + REASON_MAX + " characters");
        }
        String remarks = request.remarks() == null || request.remarks().isBlank()
                ? null
                : request.remarks().strip();
        if (remarks != null && remarks.length() > REMARKS_MAX) {
            throw new EmployeeDeductionValidationException(
                    line, "remarks must be at most " + REMARKS_MAX + " characters");
        }
        return new ParsedLine(request.employeeId(), period, type, amount, reason, remarks, request.documentId());
    }

    /**
     * A reversal's reason, as the ledger keeps it.
     *
     * @throws EmployeeDeductionValidationException if blank or longer than 255 characters
     */
    public static String reversalReason(String reason) {
        String stripped = reason == null ? "" : reason.strip();
        if (stripped.isEmpty() || stripped.length() > REASON_MAX) {
            throw new EmployeeDeductionValidationException("reason must be 1 to " + REASON_MAX + " characters");
        }
        return stripped;
    }

    private static YearMonth parsePeriod(int line, String period) {
        if (period == null || period.isBlank()) {
            throw new EmployeeDeductionValidationException(line, "period is required, as YYYY-MM");
        }
        try {
            return YearMonth.parse(period.strip());
        } catch (DateTimeParseException e) {
            throw new EmployeeDeductionValidationException(line, "period must be YYYY-MM, was " + period);
        }
    }

    private static DeductionType parseType(int line, String type) {
        if (type == null || type.isBlank()) {
            throw new EmployeeDeductionValidationException(line, "deduction_type is required");
        }
        try {
            return DeductionType.valueOf(type.strip());
        } catch (IllegalArgumentException e) {
            throw new EmployeeDeductionValidationException(
                    line, "deduction_type must be one of " + List.of(DeductionType.values()) + ", was " + type);
        }
    }
}
