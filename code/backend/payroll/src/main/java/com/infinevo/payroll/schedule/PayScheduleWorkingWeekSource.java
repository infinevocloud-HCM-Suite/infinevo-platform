package com.infinevo.payroll.schedule;

import com.infinevo.core.lop.WorkingWeekSource;
import java.time.DayOfWeek;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the {@link WorkingWeekSource} port for the {@code core} loss-of-pay calculator (W-18.1 §4, W-28 §4).
 *
 * <p>Reads the tenant's configured working days from {@code payroll.pay_schedule}.
 * Throws {@link NoPayScheduleException} (409) if no schedule is configured for the tenant.
 * Note: {@code employeeId} is unused per legacy tenant-level rule, but retained in the port contract.
 */
@Component
public class PayScheduleWorkingWeekSource implements WorkingWeekSource {

    private final PayScheduleRepository repository;

    public PayScheduleWorkingWeekSource(PayScheduleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    // Read-only transaction of its own when called outside one: the tenant-binding datasource binds
    // the tenant only inside a transaction, and refuses an auto-commit connection.
    @Override
    @Transactional(readOnly = true)
    public Set<DayOfWeek> weekdaysFor(UUID tenantId, UUID employeeId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        PaySchedule schedule = repository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new NoPayScheduleException("No pay schedule configured for tenant " + tenantId));
        return schedule.getWorkingDaysAsDayOfWeek();
    }
}
