package com.infinevo.hrms.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code hrms.assignment} (W-41).
 *
 * <p>Every read takes {@code tenantId} (DEBT-022) to enforce tenant scoping.
 */
@Transactional(readOnly = true)
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Assignment> findAllByTenantIdAndProjectIdAndDeletedFalse(UUID tenantId, UUID projectId);

    Optional<Assignment> findByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
            UUID tenantId, UUID projectId, UUID employeeId);

    boolean existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(UUID tenantId, UUID projectId, UUID employeeId);

    List<Assignment> findAllByTenantIdAndEmployeeIdAndDeletedFalse(UUID tenantId, UUID employeeId);
}
