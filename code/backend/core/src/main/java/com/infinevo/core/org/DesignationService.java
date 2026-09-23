package com.infinevo.core.org;

/**
 * The designation use cases (W-14.1, spec section 4). The contract and the failures are
 * {@link OrgMasterService}'s; this interface binds them to the designation's own request and response.
 */
public interface DesignationService extends OrgMasterService<DesignationRequest, DesignationResponse> {}
