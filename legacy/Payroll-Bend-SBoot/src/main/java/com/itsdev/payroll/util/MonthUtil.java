package com.itsdev.payroll.util;

public final class MonthUtil {

    // Prevent instantiation
    private MonthUtil() {}

    /**
     * Convert month name to month number.
     *
     * Accepted values (case-insensitive):
     * january, february, march, april, may, june,
     * july, august, september, october, november, december
     *
     * @param monthName month as string (e.g. "march")
     * @return month number (1–12)
     */
    public static int toMonthNumber(String monthName) {

        if (monthName == null || monthName.trim().isEmpty()) {
            throw new IllegalArgumentException("Month name cannot be null or empty");
        }

        switch (monthName.trim().toLowerCase()) {
            case "january":
                return 1;
            case "february":
                return 2;
            case "march":
                return 3;
            case "april":
                return 4;
            case "may":
                return 5;
            case "june":
                return 6;
            case "july":
                return 7;
            case "august":
                return 8;
            case "september":
                return 9;
            case "october":
                return 10;
            case "november":
                return 11;
            case "december":
                return 12;
            default:
                throw new IllegalArgumentException(
                        "Invalid month name: " + monthName
                );
        }
    }

    /**
     * Optional helper: validate month name.
     */
    public static boolean isValidMonth(String monthName) {
        try {
            toMonthNumber(monthName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
