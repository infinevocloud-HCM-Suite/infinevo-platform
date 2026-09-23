package com.infinevo.core.org;

/**
 * What a client may say about a department on create and on update (W-14.1, spec section 4).
 *
 * <p>No tenant, no id and no audit field — see {@link OrgMasterRequest}. No {@code parentId} either:
 * departments are flat, founder decision 2 (spec section 13).
 */
public record DepartmentRequest(String code, String name, Boolean active) implements OrgMasterRequest {}
