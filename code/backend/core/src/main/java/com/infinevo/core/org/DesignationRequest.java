package com.infinevo.core.org;

/**
 * What a client may say about a designation on create and on update (W-14.1, spec section 4).
 *
 * <p>No tenant, no id and no audit field — see {@link OrgMasterRequest}. No {@code level} and no
 * {@code grade}: pay grade lives on the employment record, founder decision 1 (spec section 13).
 */
public record DesignationRequest(String code, String name, Boolean active) implements OrgMasterRequest {}
