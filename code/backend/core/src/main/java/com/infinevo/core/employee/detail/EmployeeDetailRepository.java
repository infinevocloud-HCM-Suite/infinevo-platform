package com.infinevo.core.employee.detail;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * The one query every detail section needs (W-13.2).
 *
 * <p>{@code @NoRepositoryBean}: Spring Data creates no proxy for this interface. The five that
 * extend it — {@link EmployeePersonalRepository}, {@link EmployeeContactRepository},
 * {@link EmployeeIdentificationRepository}, {@link EmployeeEmploymentRepository} and
 * {@link EmployeeBankRepository} — each get their own, against their own table. The derived query
 * resolves against whichever entity the subinterface binds, because both properties it names are on
 * {@link EmployeeDetail}. Same shape as {@code com.infinevo.core.org.OrgMasterRepository}.
 *
 * <p><strong>The read names the tenant.</strong> Row-level security would hide the other tenants
 * anyway ({@code V015__employee_personal.sql} and its four siblings), and that is the real boundary —
 * but a repository whose signature names the tenant cannot be called by accident from a path where
 * none is bound, and the query then matches the {@code (tenant_id, employee_id)} unique index instead
 * of scanning.
 *
 * <p><strong>The inherited {@link JpaRepository} finders do not scope by tenant and must not be
 * used.</strong> {@code findById}, {@code findAll}, {@code getReferenceById} and {@code deleteById}
 * are all inherited and none of them names a tenant. Nothing in
 * {@link AbstractEmployeeDetailServiceImpl} reaches for them — the single read path is the method
 * below — but the compiler does not stop anyone. This is the same hazard
 * {@code EmployeeRepository} records against itself.
 *
 * @param <T> the section this repository owns
 */
@NoRepositoryBean
public interface EmployeeDetailRepository<T extends EmployeeDetail> extends JpaRepository<T, UUID> {

    /**
     * The one section row for this employee in this tenant, if there is one.
     *
     * <p>{@code EmployeeId} is the {@code employee} association's identifier, not a scalar column:
     * Spring Data resolves it by traversing the {@code @ManyToOne} on {@link EmployeeDetail}. There
     * is no {@code employeeId} field for it to match first, so the traversal is unambiguous.
     *
     * <p>It returns at most one row because the unique index says so — {@code (tenant_id,
     * employee_id)} on every one of the five tables. That index, not a rule the service remembers, is
     * what makes "there is never a second section per employee" true.
     */
    Optional<T> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
