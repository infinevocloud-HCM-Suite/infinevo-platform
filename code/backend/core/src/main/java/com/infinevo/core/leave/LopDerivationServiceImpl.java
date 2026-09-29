package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link LopDerivationService} (W-16.4a).
 * Distributes excess days from the end date backwards across calendar months.
 */
@Service
public class LopDerivationServiceImpl implements LopDerivationService {

    private static final BigDecimal SCALE_TWO_ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal FULL_DAY = new BigDecimal("1.00");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.50");

    @Override
    public Map<YearMonth, BigDecimal> splitExcessDays(
            LocalDate fromDate, LocalDate toDate, boolean isHalfDay, ExceedBalanceMode mode, BigDecimal excessDays) {
        if (mode != ExceedBalanceMode.MARK_AS_LOP) {
            return Collections.emptyMap();
        }
        if (excessDays == null || excessDays.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyMap();
        }
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");

        List<LocalDate> allDates = new ArrayList<>();
        LocalDate curr = fromDate;
        while (!curr.isAfter(toDate)) {
            allDates.add(curr);
            curr = curr.plusDays(1);
        }

        BigDecimal remainingExcess = excessDays.setScale(2, RoundingMode.HALF_UP);
        Map<YearMonth, BigDecimal> monthlyLop = new LinkedHashMap<>();

        // Distribute from last date backwards (frozen rule: LeaveRequestServiceImpl.java:382-392)
        for (int i = allDates.size() - 1; i >= 0 && remainingExcess.compareTo(BigDecimal.ZERO) > 0; i--) {
            LocalDate date = allDates.get(i);
            YearMonth ym = YearMonth.from(date);
            BigDecimal dayCapacity = (isHalfDay && allDates.size() == 1) ? HALF_DAY : FULL_DAY;
            BigDecimal amountToAdd = remainingExcess.min(dayCapacity);

            monthlyLop.merge(ym, amountToAdd, BigDecimal::add);
            remainingExcess = remainingExcess.subtract(amountToAdd);
        }

        // Ensure all values have scale 2
        Map<YearMonth, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyLop.entrySet()) {
            result.put(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Map<YearMonth, BigDecimal> deriveLop(
            LocalDate fromDate,
            LocalDate toDate,
            boolean isHalfDay,
            ExceedBalanceMode mode,
            BigDecimal requestedDays,
            BigDecimal availableBalance) {
        if (mode != ExceedBalanceMode.MARK_AS_LOP) {
            return Collections.emptyMap();
        }
        if (requestedDays == null || requestedDays.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyMap();
        }
        BigDecimal balance = availableBalance != null ? availableBalance : BigDecimal.ZERO;
        BigDecimal excess = requestedDays.subtract(balance);
        if (excess.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyMap();
        }
        return splitExcessDays(fromDate, toDate, isHalfDay, mode, excess);
    }
}
