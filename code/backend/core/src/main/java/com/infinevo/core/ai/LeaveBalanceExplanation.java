package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.leave.LeaveAllocation;
import com.infinevo.core.leave.LeaveBalanceResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * How one of the caller's own leave balances is made up (W-114.15): the parts the balance service already worked out,
 * the leave year's dates, and one sentence with the service's own figures filled in, so the model never does the sum.
 *
 * <p>Fields are copied one by one, so no id leaves: not the employee's, the leave type's, the allocation's or the
 * policy's (rule 4). Days are {@link BigDecimal} written as two-decimal text (rule 3). There is no opening balance and
 * no count of days lapsed in the data, so neither is ever written.
 */
final class LeaveBalanceExplanation {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private LeaveBalanceExplanation() {}

    /** The allocation fields the answer needs, read before the transaction ends. */
    record Year(LocalDate start, LocalDate end, BigDecimal proRateFactor, LocalDate lastAccruedOn) {

        static Year of(LeaveAllocation a) {
            return new Year(a.getYearStartDate(), a.getYearEndDate(), a.getProRateFactor(), a.getLastAccruedOn());
        }
    }

    /**
     * @param balances the caller's balances, as {@code LeaveBalanceService} gives them
     * @param years the allocation fields, keyed by {@link #key(LeaveBalanceResponse)}
     * @param asOf the date the balances were read on
     */
    static JsonNode explain(List<LeaveBalanceResponse> balances, Map<String, Year> years, LocalDate asOf) {
        ObjectNode out = NODES.objectNode();
        ArrayNode list = out.putArray("balances");
        for (LeaveBalanceResponse b : balances) {
            list.add(one(b, years.get(key(b)), asOf));
        }
        return AiToolResults.withoutIds(out);
    }

    /** One balance with its parts and the sentence. */
    static ObjectNode one(LeaveBalanceResponse b, Year year, LocalDate asOf) {
        ObjectNode row = NODES.objectNode();
        ObjectNode type = row.putObject("leaveType");
        putText(type, "code", b.leaveTypeCode());
        putText(type, "name", b.leaveTypeName());
        putText(row, "leaveYear", b.leaveYear());
        if (year != null) {
            putDate(row, "yearStart", year.start());
            putDate(row, "yearEnd", year.end());
        }
        putDays(row, "granted", b.entitlementDays());
        putDays(row, "accrued", b.accruedDays());
        putDays(row, "carriedForward", b.carriedForwardDays());
        putDays(row, "used", b.consumedDays());
        putDays(row, "balance", b.remainingDays());
        putDays(row, "pending", b.pendingDays());
        putDays(row, "available", b.availableDays());
        putDate(row, "carryForwardExpiresOn", b.carryForwardExpiresOn());
        putDate(row, "asOf", asOf);
        // The service's carried figure already stops counting the day after this date (LeaveDateUtils:130).
        row.put(
                "carryForwardExpired",
                b.carryForwardExpiresOn() != null && asOf != null && asOf.isAfter(b.carryForwardExpiresOn()));
        // The response's "expired" is about the leave type (ended or switched off, W-77.1), not the carry-forward.
        row.put("leaveTypeEnded", b.expired());
        if (year != null) {
            if (year.proRateFactor() != null) {
                row.put(
                        "proRateFactor",
                        year.proRateFactor().stripTrailingZeros().toPlainString());
            }
            putDate(row, "lastAccruedOn", year.lastAccruedOn());
        }
        row.put("howItAddsUp", sentence(b));
        return row;
    }

    /**
     * "balance = granted + accrued + carried forward - used; available = balance - pending, never below zero", with
     * the service's own figures. The tool adds nothing up itself (rule 5).
     */
    static String sentence(LeaveBalanceResponse b) {
        return "balance " + days(b.remainingDays())
                + " = granted " + days(b.entitlementDays())
                + " + accrued " + days(b.accruedDays())
                + " + carried forward " + days(b.carriedForwardDays())
                + " - used " + days(b.consumedDays())
                + "; available " + days(b.availableDays())
                + " = balance " + days(b.remainingDays())
                + " - pending " + days(b.pendingDays())
                + ", never below zero";
    }

    /** Matches a balance to its allocation: the leave type and the leave year. */
    static String key(LeaveBalanceResponse b) {
        return b.leaveTypeId() + "|" + b.leaveYear();
    }

    static String key(LeaveAllocation a) {
        return a.getLeaveTypeId() + "|" + a.getLeaveYear();
    }

    /** Days at two decimals: {@code 4.5} is {@code "4.50"}; missing is {@code "0.00"}. */
    static String days(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static void putDays(ObjectNode out, String name, BigDecimal value) {
        out.put(name, days(value));
    }

    private static void putDate(ObjectNode out, String name, LocalDate value) {
        if (value != null) {
            out.put(name, value.toString());
        }
    }

    private static void putText(ObjectNode out, String name, String value) {
        if (value != null) {
            out.put(name, value);
        }
    }
}
