package com.infinevo.core.employee;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.employee} (W-13.1).
 *
 * <p>Two rules are built into the method names rather than left to each caller to remember.
 *
 * <p><strong>Every read names the tenant.</strong> Row-level security would hide the other tenants
 * anyway ({@code V010__employee.sql:55-62}), and it is the real boundary — but a repository whose
 * signatures name the tenant cannot be called by accident from a path where none is bound, and the
 * query then matches the {@code (tenant_id, ...)} indexes instead of scanning.
 *
 * <p><strong>Every finder declared here excludes soft-deleted rows</strong> — with one deliberate
 * exception, the uniqueness check below.
 *
 * <p><strong>That is not a guarantee, and this comment used to claim it was.</strong> This interface
 * extends {@link JpaRepository}, so {@code findById}, {@code findAll}, {@code getReferenceById} and
 * {@code deleteById} are all inherited and none of them filters {@code is_deleted}. Nothing in the
 * service reaches for them — {@code EmployeeServiceImpl.require} is the single read path — but the
 * compiler does not stop anyone.
 *
 * <p><strong>W-13.3 is the ticket this will bite.</strong> Search and listing is the natural place to
 * reach for {@code findAll} or a derived list query, and either will return soft-deleted employees
 * silently: they are valid rows, and the only thing marking them dead is a boolean nobody asked
 * about. Every query added there needs {@code AndDeletedFalse} or an explicit predicate.
 */
@Transactional(readOnly = true)
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /** The one live employee with this id in this tenant, if there is one. */
    Optional<Employee> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    /**
     * These employees in this tenant, <strong>deleted rows included</strong> - for naming the
     * subject of a record raised before the delete, never for listing.
     */
    List<Employee> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    /** The live employee linked to this user account in this tenant, if there is one (W-13.4). */
    Optional<Employee> findByTenantIdAndUserAccountIdAndDeletedFalse(UUID tenantId, UUID userAccountId);

    /**
     * True when this tenant already holds this employee number, <strong>deleted rows included</strong>.
     *
     * <p>The exclusion of {@code deleted} is the point. The unique index
     * ({@code V010__employee.sql:47}) is over every row, live or soft-deleted, so a check that
     * skipped deleted rows would pass and then fail in the database as a constraint violation. The
     * number of a deleted employee stays spent, which is also what an auditor expects: two people
     * never shared a payroll number.
     */
    boolean existsByTenantIdAndEmployeeNumber(UUID tenantId, String employeeNumber);

    /** The same check for an update, ignoring the row being updated. */
    boolean existsByTenantIdAndEmployeeNumberAndIdNot(UUID tenantId, String employeeNumber, UUID id);

    /**
     * How many employees in this tenant are assigned to this department — W-14.1, spec section 4.
     *
     * <p>This is what makes {@code DELETE /api/v1/departments/{id}} refusable while anyone holds the
     * record. The underscore is Spring Data's explicit property-path separator: the property is the
     * {@code department} association's {@code id}, not a scalar column called {@code departmentId}.
     *
     * <p><strong>Soft-deleted employees count.</strong> Every other finder here excludes them; these
     * three deliberately do not. A soft-deleted employee still holds the foreign key, so the delete
     * would fail in the database anyway — as a constraint violation carrying SQL rather than the
     * sentence {@code RecordInUseException} gives — and the pay run that named the department is
     * history somebody may have to reproduce.
     */
    long countByTenantIdAndDepartment_Id(UUID tenantId, UUID departmentId);

    /** The same count for a designation. See {@link #countByTenantIdAndDepartment_Id}. */
    long countByTenantIdAndDesignation_Id(UUID tenantId, UUID designationId);

    /** The same count for a work location. See {@link #countByTenantIdAndDepartment_Id}. */
    long countByTenantIdAndWorkLocation_Id(UUID tenantId, UUID workLocationId);

    /**
     * Paginated, filtered, free-text search over a tenant's employees (W-13.3, spec section 4).
     *
     * <p>{@code @EntityGraph} fetches the three org master associations in a single join, so the
     * caller can read {@code department}, {@code designation} and {@code workLocation} ids without
     * an N+1 problem.
     *
     * <p>All four filters — tenant, soft-delete, status and free-text — are evaluated in the query
     * so the page counts are correct and the database does the work rather than Java.
     */
    @EntityGraph(attributePaths = {"department", "designation", "workLocation"})
    @Query(
            """
            SELECT e FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:includeDeleted = true OR e.deleted = false)
              AND (:status IS NULL OR e.status = :status)
              AND (:q IS NULL OR :q = '' OR (
                   LOWER(e.firstName) LIKE LOWER(CONCAT(:q, '%'))
                OR LOWER(e.lastName) LIKE LOWER(CONCAT(:q, '%'))
                OR LOWER(e.employeeNumber) LIKE LOWER(CONCAT(:q, '%'))
                OR LOWER(e.workEmail) LIKE LOWER(CONCAT(:q, '%'))
              ))
            """)
    Page<Employee> search(
            @Param("tenantId") UUID tenantId,
            @Param("q") String q,
            @Param("status") EmploymentStatus status,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable);

    /**
     * The employees a pay run considers for {@code [start, end]} (W-29.1 §3), in one statement. The
     * status literals are parameters so the query stays a plain JPQL string.
     */
    @Query(
            """
            SELECT e FROM Employee e
            WHERE e.tenantId = :tenantId
              AND e.deleted = false
              AND e.dateOfJoining <= :end
              AND (e.status = :active
                OR (e.status = :terminated AND e.terminationDate >= :start))
            ORDER BY e.employeeNumber
            """)
    List<Employee> findEmployedBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("active") EmploymentStatus active,
            @Param("terminated") EmploymentStatus terminated);

    /** True if at least one active (non-deleted) employee exists in this tenant (W-24.1 setup checker). */
    boolean existsByTenantIdAndDeletedFalse(UUID tenantId);
}
