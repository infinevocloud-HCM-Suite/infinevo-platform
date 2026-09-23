package com.infinevo.core.org;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code core.work_location} (W-14.1).
 *
 * <p>One query beyond {@link OrgMasterRepository}: the tenant's filing address. The partial unique
 * index at {@code V013__work_location.sql} guarantees there is at most one, which is why this
 * returns an {@link Optional} and not a list.
 */
public interface WorkLocationRepository extends OrgMasterRepository<WorkLocation> {

    /** The tenant's filing address, if it has named one. At most one row by construction. */
    Optional<WorkLocation> findByTenantIdAndFilingAddressTrue(UUID tenantId);
}
