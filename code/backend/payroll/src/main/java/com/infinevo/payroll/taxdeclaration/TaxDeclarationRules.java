package com.infinevo.payroll.taxdeclaration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pure domain rules for tax declaration editability and regime validation (W-32.1).
 */
public final class TaxDeclarationRules {

    /**
     * The calendar the declaration window is expressed in. Window dates are Indian dates, so
     * "today" must be read in this zone regardless of where the JVM runs (a UTC container would
     * otherwise keep the window open until 05:30 IST the next day).
     */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private TaxDeclarationRules() {}

    /** The default clock every declaration service uses when none is injected. */
    public static Clock defaultClock() {
        return Clock.system(ZONE);
    }

    /** Today's date in {@link #ZONE} according to {@code clock}. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /**
     * The human-readable reason a declaration failed {@link #isEditable}. Every {@code PUT} on the
     * header or a section answers {@code 409 NOT_EDITABLE} (spec §4); this is the message beside it.
     * Only {@code reopen} reports {@code LOCKED} and {@code WINDOW_CLOSED} as codes of their own.
     */
    public static String notEditableReason(
            EmployeeInvestmentDeclaration header,
            IncomeTaxDeclarationWindow window,
            LocalDate today,
            boolean ignoreWindow) {
        if (header != null && header.isLocked()) {
            return "Tax declaration is locked";
        }
        if (header != null && header.getStatus() == DeclarationStatus.SUBMITTED) {
            return "Submitted tax declaration cannot be edited";
        }
        if (!ignoreWindow && (window == null || !window.isOpenOn(today))) {
            return "Tax declaration window is closed";
        }
        return "Tax declaration is not currently editable";
    }

    /**
     * Determines whether a tax declaration can be edited.
     *
     * <p>A declaration is editable if:
     * <ul>
     *   <li>It is in {@link DeclarationStatus#DRAFT} status</li>
     *   <li>It is not locked by an officer ({@code !header.isLocked()})</li>
     *   <li>Either {@code ignoreWindow} is {@code true}, or the declaration window is open today
     *       (i.e., not locked and {@code window_opens_on <= today <= window_closes_on})</li>
     * </ul>
     */
    public static boolean isEditable(
            EmployeeInvestmentDeclaration header,
            IncomeTaxDeclarationWindow window,
            LocalDate today,
            boolean ignoreWindow) {
        if (header == null) {
            return false;
        }
        if (header.getStatus() != DeclarationStatus.DRAFT) {
            return false;
        }
        if (header.isLocked()) {
            return false;
        }
        if (ignoreWindow) {
            return true;
        }
        if (window == null) {
            return false;
        }
        return window.isOpenOn(today);
    }
}
