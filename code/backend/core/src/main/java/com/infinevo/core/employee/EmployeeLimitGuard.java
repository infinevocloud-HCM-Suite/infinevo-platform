package com.infinevo.core.employee;

import com.infinevo.core.subscription.Subscription;
import com.infinevo.core.subscription.SubscriptionRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant's employee limit (W-84.2): one check for every path that adds a counted employee — create,
 * reactivation from {@code TERMINATED}, and the CSV import, which goes through create.
 *
 * <p><strong>Usage</strong> is counted live from {@code core.employee} every time, never stored: employees of
 * the bound tenant that are not soft-deleted and not {@code TERMINATED}. {@code SUSPENDED} counts, because
 * moving back from {@code SUSPENDED} is a plain status edit and must not be a side door. A {@code NULL}
 * {@code employee_limit} means no gate. The gate runs in every subscription status (founder, 2026-11-05).
 *
 * <p>The subscription is read through row-level security as the bound tenant, which may read its own row; no
 * platform bypass. Called inside the caller's write transaction, so the count and the insert read together.
 *
 * <p>The subscription repository is taken through an {@link ObjectProvider} because several narrow test
 * contexts, some in other modules, component-scan {@code core.employee} without the subscription package. The
 * real {@code app} and {@code worker} scan all of {@code com.infinevo}, so there the repository is always
 * present and the gate always runs.
 */
@Component
public class EmployeeLimitGuard {

    private final ObjectProvider<SubscriptionRepository> subscriptionRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeLimitGuard(
            ObjectProvider<SubscriptionRepository> subscriptionRepository, EmployeeRepository employeeRepository) {
        this.subscriptionRepository =
                Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    /**
     * Refuses when adding {@code adding} counted employees would take usage past the limit. Nothing is counted
     * when there is no limit.
     *
     * @throws EmployeeService.EmployeeLimitReachedException when {@code used + adding > limit}
     */
    @Transactional(readOnly = true)
    public void checkRoomFor(int adding) {
        UUID tenantId = TenantContext.require();
        Integer limit = limitOf(tenantId);
        if (limit == null) {
            return;
        }
        long used = used(tenantId);
        if (used + adding > limit) {
            throw new EmployeeService.EmployeeLimitReachedException(limit, used);
        }
    }

    /** The bound tenant's limit ({@code null} when unlimited) and its live usage. */
    @Transactional(readOnly = true)
    public Usage usage() {
        UUID tenantId = TenantContext.require();
        return new Usage(limitOf(tenantId), used(tenantId));
    }

    private Integer limitOf(UUID tenantId) {
        SubscriptionRepository subscriptions = subscriptionRepository.getIfAvailable();
        if (subscriptions == null) {
            return null;
        }
        return subscriptions
                .findByTenantId(tenantId)
                .map(Subscription::getEmployeeLimit)
                .orElse(null);
    }

    private long used(UUID tenantId) {
        return employeeRepository.countByTenantIdAndDeletedFalseAndStatusNot(tenantId, EmploymentStatus.TERMINATED);
    }

    /**
     * A tenant's limit and how much of it is in use.
     *
     * @param limit the plan's employee limit, {@code null} when there is none
     * @param used  employees not soft-deleted and not {@code TERMINATED}
     */
    public record Usage(Integer limit, long used) {

        /** How many more can be added: {@code null} when unlimited, never negative. */
        public Integer room() {
            return limit == null ? null : (int) Math.max(0L, limit - used);
        }
    }
}
