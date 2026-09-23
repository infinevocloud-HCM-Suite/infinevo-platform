package com.infinevo.core.employee;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /** The one live employee with this id in this tenant, if there is one. */
    Optional<Employee> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

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
}
