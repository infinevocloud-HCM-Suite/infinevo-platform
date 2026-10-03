package com.infinevo.payroll.dashboard;

/** The payroll dashboard for the bound tenant, read-only (W-37 §4). */
public interface PayrollDashboardService {

    /** Lowest and highest {@code fy} the dashboard accepts (W-37 §4); outside is {@code 400}. */
    int MIN_YEAR = 2000;

    int MAX_YEAR = 2100;

    /**
     * The whole dashboard.
     *
     * @param fy the year the financial year starts ({@code 2026} = April 2026 to March 2027); null for
     *     the year containing today
     * @throws IllegalArgumentException when {@code fy} is outside {@link #MIN_YEAR}..{@link #MAX_YEAR}
     */
    PayrollDashboardResponse summary(Integer fy);
}
