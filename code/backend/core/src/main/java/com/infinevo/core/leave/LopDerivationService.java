package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Service for calculating loss-of-pay (LOP) days and month-wise distribution (W-16.4a).
 */
public interface LopDerivationService {

    /**
     * Splits excess days across calendar months.
     * Only produces LOP when {@code mode == ExceedBalanceMode.MARK_AS_LOP}.
     *
     * @param fromDate start date of the leave request
     * @param toDate end date of the leave request
     * @param isHalfDay whether the leave is half-day
     * @param mode policy exceed balance mode
     * @param excessDays total excess days beyond available balance
     * @return map of YearMonth to LOP days for that month
     */
    Map<YearMonth, BigDecimal> splitExcessDays(
            LocalDate fromDate, LocalDate toDate, boolean isHalfDay, ExceedBalanceMode mode, BigDecimal excessDays);

    /**
     * Computes excess days (if any) and derives month-wise LOP.
     */
    Map<YearMonth, BigDecimal> deriveLop(
            LocalDate fromDate,
            LocalDate toDate,
            boolean isHalfDay,
            ExceedBalanceMode mode,
            BigDecimal requestedDays,
            BigDecimal availableBalance);
}
