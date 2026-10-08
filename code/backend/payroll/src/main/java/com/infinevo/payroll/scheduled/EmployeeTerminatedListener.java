package com.infinevo.payroll.scheduled;

import com.infinevo.core.employee.EmployeeTerminatedEvent;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * W-73.6 §3: when {@code core} terminates an employee, the employee's remaining scheduled earnings
 * are cancelled with reason {@code terminated}. Synchronous and in the publisher's transaction, so
 * the termination and the cancellation commit — or roll back — together. The tenant is taken from
 * the event rather than assumed bound, and bound for the call if it is not.
 */
@Component
public class EmployeeTerminatedListener {

    private final ScheduledEarningService scheduledEarnings;

    public EmployeeTerminatedListener(ScheduledEarningService scheduledEarnings) {
        this.scheduledEarnings = Objects.requireNonNull(scheduledEarnings, "scheduledEarnings must not be null");
    }

    @EventListener
    public void onEmployeeTerminated(EmployeeTerminatedEvent event) {
        if (event == null || event.employeeId() == null || event.tenantId() == null) {
            return;
        }
        UUID previous = TenantContext.current().orElse(null);
        boolean rebound = !event.tenantId().equals(previous);
        if (rebound) {
            TenantContext.set(event.tenantId());
        }
        try {
            scheduledEarnings.cancelForTerminatedEmployee(event.employeeId());
        } finally {
            if (rebound) {
                if (previous != null) {
                    TenantContext.set(previous);
                } else {
                    TenantContext.clear();
                }
            }
        }
    }
}
