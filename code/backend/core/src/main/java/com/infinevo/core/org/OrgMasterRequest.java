package com.infinevo.core.org;

/**
 * The three fields every org master request carries (W-14.1, spec section 4).
 *
 * <p>Implemented by {@link DepartmentRequest}, {@link DesignationRequest} and
 * {@link WorkLocationRequest}. A record's accessors satisfy it as they stand, so this costs the
 * three DTOs nothing and lets {@link AbstractOrgMasterServiceImpl} validate the common part once
 * rather than three times.
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before any of these is read. A
 * tenant a caller can state is a tenant a caller can change, which is the single thing the tenancy
 * design exists to prevent.
 */
public interface OrgMasterRequest {

    /** Unique within the tenant, never globally — the index is {@code (tenant_id, code)}. */
    String code();

    String name();

    /**
     * Null means "true" on create and "leave it where it is" on update.
     *
     * <p>So a client that omits the field on an update cannot silently reactivate a value an
     * administrator deliberately retired, which is the same rule {@code EmployeeRequest.status}
     * follows.
     */
    Boolean active();
}
