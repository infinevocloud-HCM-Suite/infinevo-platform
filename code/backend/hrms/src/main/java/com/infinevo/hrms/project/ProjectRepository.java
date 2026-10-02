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
 * Reads and writes {@code hrms.project} (W-41).
 *
 * <p>Every read takes {@code tenantId} (DEBT-022) to enforce tenant scoping.
 */
@Transactional(readOnly = true)
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCaseAndDeletedFalse(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(UUID tenantId, String name, UUID id);

    List<Project> findAllByTenantIdAndIdInAndDeletedFalse(UUID tenantId, Collection<UUID> ids);

    /**
     * Every parameter is bound non-null. PostgreSQL types an untyped null as {@code bytea}, and
     * {@code lower(bytea)} does not exist, so an "optional" filter written as {@code :x IS NULL OR ...}
     * fails at run time. Instead the caller passes every status when none is asked for, a flag when the
     * manager filter is off, and {@code %} when there is no search text ({@link ProjectLikePattern}).
     */
    @Query(
            """
        SELECT p FROM Project p
        WHERE p.tenantId = :tenantId
          AND p.deleted = false
          AND p.status IN :statuses
          AND (:anyManager = true OR p.managerEmployeeId = :managerEmployeeId)
          AND (LOWER(p.name) LIKE :pattern ESCAPE '!'
               OR (p.description IS NOT NULL AND LOWER(p.description) LIKE :pattern ESCAPE '!'))
        ORDER BY p.name ASC
    """)
    List<Project> searchProjects(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<ProjectStatus> statuses,
            @Param("anyManager") boolean anyManager,
            @Param("managerEmployeeId") UUID managerEmployeeId,
            @Param("pattern") String pattern);
}
