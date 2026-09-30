package com.infinevo.payroll.schedule;

import com.infinevo.shared.tenant.TenantContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PayPeriodService} (W-28 §4).
 */
@Service
public class PayPeriodServiceImpl implements PayPeriodService {

    private final PayScheduleRepository repository;

    public PayPeriodServiceImpl(PayScheduleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public PayPeriodResponse periodFor(YearMonth period) {
        return periodFor(TenantContext.require(), period);
    }

    @Override
    @Transactional(readOnly = true)
    public PayPeriodResponse periodFor(UUID tenantId, YearMonth period) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(period, "period must not be null");

        PaySchedule schedule = repository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new NoPayScheduleException("No pay schedule configured for tenant " + tenantId));

        YearMonth firstPeriod = YearMonth.from(schedule.getFirstPeriodStart());
        if (period.isBefore(firstPeriod)) {
            throw new IllegalArgumentException(
                    "Requested period " + period + " is before first configured period " + firstPeriod);
        }

        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        int cutoffDay = Math.min((int) schedule.getInputCutoffDay(), period.lengthOfMonth());
        LocalDate cutoffDate = period.atDay(cutoffDay);

        LocalDate payDate = derivePayDate(schedule, period, start, end);

        return new PayPeriodResponse(start, end, cutoffDate, payDate);
    }

    private LocalDate derivePayDate(PaySchedule schedule, YearMonth period, LocalDate start, LocalDate end) {
        return switch (schedule.getPayDayRule()) {
            case LAST_DAY_OF_PERIOD -> end;
            case LAST_WORKING_DAY -> {
                Set<DayOfWeek> workingDays = schedule.getWorkingDaysAsDayOfWeek();
                LocalDate candidate = end;
                while (!candidate.isBefore(start)) {
                    if (workingDays.contains(candidate.getDayOfWeek())) {
                        yield candidate;
                    }
                    candidate = candidate.minusDays(1);
                }
                // Fallback to start if no working days matched in month (should never happen with valid schedule)
                yield start;
            }
            case SPECIFIC_DAY -> {
                if (schedule.getPayDayOfMonth() == null) {
                    throw new IllegalStateException("payDayOfMonth is required when payDayRule is SPECIFIC_DAY");
                }
                YearMonth nextMonth = period.plusMonths(1);
                int dayOfMonth = Math.min(schedule.getPayDayOfMonth().intValue(), nextMonth.lengthOfMonth());
                yield nextMonth.atDay(dayOfMonth);
            }
        };
    }
}
