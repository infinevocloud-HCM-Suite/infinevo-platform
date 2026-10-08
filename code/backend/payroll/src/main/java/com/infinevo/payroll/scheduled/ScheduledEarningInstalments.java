package com.infinevo.payroll.scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * The arithmetic of a schedule (W-73.6 §2), kept free of Spring so a unit test can hold it to the
 * worked example: 10,000 over 3 is 3,333.33 · 3,333.33 · 3,333.34 — the last instalment takes the
 * rounding, so the instalments always add back to the total.
 */
public final class ScheduledEarningInstalments {

    /** How a materialised instalment names itself on the ledger: {@code scheduled_earning:<id>:<yyyy-MM>}. */
    public static final String SOURCE_REF_PREFIX = "scheduled_earning:";

    /** Money scale on the ledger row (CONVENTIONS §2 rule 2): paise, rounded half up. */
    static final int SCALE = 2;

    private ScheduledEarningInstalments() {}

    /** The smallest instalment the ledger accepts: one paisa. */
    public static final BigDecimal MIN_INSTALMENT = new BigDecimal("0.01");

    /**
     * The amount of instalment {@code index} (0-based) of {@code instalments} over {@code total}.
     * Every instalment but the last is {@code total ÷ instalments} cut to two places (rounded down);
     * the last is whatever remains, so the sum is exactly {@code total}. Rounding down, not half-up,
     * keeps the last instalment at least as large as the others: with half-up, 0.18 over 12 is
     * 0.02 × 11 and then −0.04 (W-73.6 F-3). With {@code total ≥ instalments × 0.01} every
     * instalment is then at least one paisa — {@link #isSplittable}.
     */
    public static BigDecimal amountOf(BigDecimal total, int instalments, int index) {
        Objects.requireNonNull(total, "total must not be null");
        if (instalments < 1) {
            throw new IllegalArgumentException("instalments must be at least 1");
        }
        if (index < 0 || index >= instalments) {
            throw new IllegalArgumentException("index " + index + " is outside 0.." + (instalments - 1));
        }
        BigDecimal each = total.divide(BigDecimal.valueOf(instalments), SCALE, RoundingMode.DOWN);
        if (index < instalments - 1) {
            return each;
        }
        BigDecimal paidBefore = each.multiply(BigDecimal.valueOf(instalments - 1L));
        return total.setScale(SCALE, RoundingMode.HALF_UP).subtract(paidBefore);
    }

    /** True when {@code total} split over {@code instalments} gives every instalment at least one paisa. */
    public static boolean isSplittable(BigDecimal total, int instalments) {
        return total != null
                && instalments >= 1
                && total.compareTo(MIN_INSTALMENT.multiply(BigDecimal.valueOf(instalments))) >= 0;
    }

    /** The ledger reference of one instalment, keyed on the period it is paid into: the idempotency key. */
    public static String sourceRef(UUID scheduledEarningId, YearMonth period) {
        return SOURCE_REF_PREFIX + scheduledEarningId + ":" + period;
    }

    /** The schedule a ledger reference names, or {@code null} for a reference not of this shape. */
    public static UUID scheduleIdOf(String sourceRef) {
        if (sourceRef == null || !sourceRef.startsWith(SOURCE_REF_PREFIX)) {
            return null;
        }
        String rest = sourceRef.substring(SOURCE_REF_PREFIX.length());
        int colon = rest.indexOf(':');
        try {
            return UUID.fromString(colon < 0 ? rest : rest.substring(0, colon));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
