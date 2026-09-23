package com.infinevo.core.org;

/**
 * The department use cases (W-14.1, spec section 4). The contract and the failures are
 * {@link OrgMasterService}'s; this interface binds them to the department's own request and response.
 */
public interface DepartmentService extends OrgMasterService<DepartmentRequest, DepartmentResponse> {}
