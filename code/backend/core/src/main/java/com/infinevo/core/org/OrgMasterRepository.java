package com.infinevo.core.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * The five queries every org master needs (W-14.1).
 *
 * <p>{@code @NoRepositoryBean}: Spring Data creates no proxy for this interface. The three that
 * extend it — {@link DepartmentRepository}, {@link DesignationRepository},
 * {@link WorkLocationRepository} — each get their own, against their own table. The derived queries
 * resolve against whichever entity the subinterface binds, because every property named here is on
 * {@link OrgMaster}.
 *
 * <p><strong>Every read names the tenant.</strong> Row-level security would hide the other tenants
 * anyway ({@code V011__department.sql} and its two siblings), and that is the real boundary — but a
 * repository whose signatures name the tenant cannot be called by accident from a path where none is
 * bound, and the query then matches the {@code (tenant_id, ...)} indexes instead of scanning. The
 * same rule {@code EmployeeRepository} follows.
 *
 * <p>{@link JpaRepository} still contributes {@code findById}, {@code findAll} and
 * {@code deleteById}, none of which names a tenant. Nothing in
 * {@link AbstractOrgMasterServiceImpl} reaches for them — {@code require} is the single read path —
 * but the compiler does not stop anyone.
 */
@NoRepositoryBean
public interface OrgMasterRepository<T extends OrgMaster> extends JpaRepository<T, UUID> {

    /** The one record with this id in this tenant, if there is one. */
    Optional<T> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Every record in this tenant, active or not, ordered by code so a list is stable. */
    List<T> findByTenantIdOrderByCodeAsc(UUID tenantId);

    /** Only the records still available for a new assignment — {@code ?activeOnly=true}. */
    List<T> findByTenantIdAndActiveTrueOrderByCodeAsc(UUID tenantId);

    /** True when this tenant already holds this code. The index is {@code (tenant_id, code)}. */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /** The same check for an update, ignoring the row being updated. */
    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID id);
}
