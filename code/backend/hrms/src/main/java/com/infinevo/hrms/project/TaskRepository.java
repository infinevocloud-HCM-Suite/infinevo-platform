package com.infinevo.hrms.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code hrms.task} (W-41).
 *
 * <p>Every read takes {@code tenantId} (DEBT-022) to enforce tenant scoping.
 */
@Transactional(readOnly = true)
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Task> findAllByTenantIdAndProjectIdAndDeletedFalse(UUID tenantId, UUID projectId);

    @Query(
            """
        SELECT t FROM Task t
        WHERE t.tenantId = :tenantId
          AND t.projectId = :projectId
          AND t.deleted = false
          AND t.status IN :statuses
        ORDER BY t.createdAt ASC
    """)
    List<Task> findByTenantIdAndProjectIdAndStatusIn(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("statuses") Collection<TaskStatus> statuses);

    List<Task> findAllByTenantIdAndAssigneeEmployeeIdAndDeletedFalseOrderByDueDateAscCreatedAtAsc(
            UUID tenantId, UUID assigneeEmployeeId);
}
