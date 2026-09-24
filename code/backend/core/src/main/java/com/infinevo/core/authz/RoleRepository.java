package com.infinevo.core.authz;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@code core.role} (W-11.1).
 *
 * <p><strong>Every read names the tenant</strong>, the rule {@code EmployeeRepository} and
 * {@code OrgMasterRepository} follow. Row-level security is the real boundary ({@code V021}); the
 * tenant in the signature makes the query match the {@code (tenant_id, ...)} indexes and means the
 * method cannot be called by accident from a path where no tenant is bound. The inherited
 * {@code findById} / {@code findAll} name none and nothing in this package uses them.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Role> findByTenantIdOrderByCodeAsc(UUID tenantId);

    /** The roles of a grant that exist in this tenant. Ids from another tenant simply do not come back. */
    List<Role> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
