package com.infinevo.core.notification;

import com.infinevo.core.employee.EmploymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code SUBJECT} audience (W-20.2): every active employee of the tenant — the people a
 * tenant-wide reminder such as a timesheet or a declaration is about. Terminated and suspended
 * employees are not reminded.
 *
 * <p>Ids only, in one query: loading every employee entity to read its id would not scale.
 */
@Component
public class SubjectAudienceResolver implements ReminderAudienceResolver {

    public static final String AUDIENCE = "SUBJECT";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public String audience() {
        return AUDIENCE;
    }

    /**
     * Its own read-only transaction: the sweep calls this with the tenant bound and none open, and
     * the tenant binding only holds inside one.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
        return entityManager
                .createQuery(
                        "SELECT e.id FROM Employee e WHERE e.tenantId = :tenantId AND e.deleted = false"
                                + " AND e.status = :status ORDER BY e.id",
                        UUID.class)
                .setParameter("tenantId", tenantId)
                .setParameter("status", EmploymentStatus.ACTIVE)
                .getResultList();
    }
}
