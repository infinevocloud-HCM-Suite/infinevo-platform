package com.infinevo.payroll.schedule;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PayScheduleService} (W-28 §4).
 *
 * <p>GET never writes a row — the unique index prevents a duplicate row race, and the
 * {@code GET-creates-row} habit is one of the named risks in the spec.
 */
@Service
public class PayScheduleServiceImpl implements PayScheduleService {

    private final PayScheduleRepository repository;

    public PayScheduleServiceImpl(PayScheduleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public PayScheduleResponse get() {
        UUID tenantId = TenantContext.require();
        return repository
                .findByTenantId(tenantId)
                .map(s -> PayScheduleResponse.from(s, true))
                .orElseGet(() -> PayScheduleResponse.defaultMissing(tenantId));
    }

    @Override
    @Transactional
    public PayScheduleResponse upsert(PayScheduleRequest request) {
        UUID tenantId = TenantContext.require();
        validate(request);

        PaySchedule schedule = repository.findByTenantId(tenantId).orElseGet(() -> {
            // Build a brand-new entity with safe defaults; all fields are overwritten below
            return new PaySchedule(
                    tenantId,
                    new Short[0],
                    PayDayRule.LAST_WORKING_DAY,
                    null,
                    PaySchedule.DEFAULT_INPUT_CUTOFF_DAY,
                    LocalDate.now().withDayOfMonth(1));
        });

        schedule.setWorkingDaysFromList(request.workingDays());
        schedule.setPayDayRule(request.payDayRule());
        schedule.setPayDayOfMonth(
                request.payDayOfMonth() != null ? request.payDayOfMonth().shortValue() : null);
        schedule.setInputCutoffDay(
                request.inputCutoffDay() != null
                        ? request.inputCutoffDay().shortValue()
                        : PaySchedule.DEFAULT_INPUT_CUTOFF_DAY);
        schedule.setFirstPeriodStart(request.firstPeriodStart());

        PaySchedule saved = repository.save(schedule);
        return PayScheduleResponse.from(saved, true);
    }

    // ── validation ───────────────────────────────────────────────────────────

    private void validate(PayScheduleRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.workingDays() == null || request.workingDays().isEmpty()) {
            throw new IllegalArgumentException("workingDays must not be empty");
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (Integer day : request.workingDays()) {
            if (day == null || day < 1 || day > 7) {
                throw new IllegalArgumentException(
                        "workingDays must contain ISO day numbers 1 (Monday) to 7 (Sunday), got: " + day);
            }
            if (!seen.add(day)) {
                throw new IllegalArgumentException("workingDays contains duplicate day: " + day);
            }
        }

        if (request.payDayRule() == null) {
            throw new IllegalArgumentException("payDayRule must not be null");
        }
        if (request.payDayRule() == PayDayRule.SPECIFIC_DAY) {
            if (request.payDayOfMonth() == null) {
                throw new IllegalArgumentException("payDayOfMonth is required when payDayRule is SPECIFIC_DAY");
            }
            if (request.payDayOfMonth() < 1 || request.payDayOfMonth() > 28) {
                throw new IllegalArgumentException(
                        "payDayOfMonth must be between 1 and 28, got: " + request.payDayOfMonth());
            }
        } else if (request.payDayOfMonth() != null) {
            throw new IllegalArgumentException("payDayOfMonth must be absent when payDayRule is not SPECIFIC_DAY");
        }

        if (request.inputCutoffDay() != null && (request.inputCutoffDay() < 1 || request.inputCutoffDay() > 28)) {
            throw new IllegalArgumentException(
                    "inputCutoffDay must be between 1 and 28, got: " + request.inputCutoffDay());
        }

        if (request.firstPeriodStart() == null) {
            throw new IllegalArgumentException("firstPeriodStart must not be null");
        }
        if (request.firstPeriodStart().getDayOfMonth() != 1) {
            throw new IllegalArgumentException(
                    "firstPeriodStart must be the first day of a month, got: " + request.firstPeriodStart());
        }

        // W-29 adds the check that firstPeriodStart may not move earlier than an existing pay run.
        // Until then, any valid first-of-month date is accepted.
    }

    /**
     * Returns a deduplicated ISO day-of-week list in ascending order.
     */
    @SuppressWarnings("unused")
    private static List<Integer> deduplicate(List<Integer> days) {
        return days.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
