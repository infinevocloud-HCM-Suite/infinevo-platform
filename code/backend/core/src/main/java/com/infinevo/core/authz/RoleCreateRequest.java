package com.infinevo.core.authz;

import java.util.List;

/**
 * {@code POST /api/v1/roles} (W-11.1, spec section 4).
 *
 * <p>{@code code} is optional. Spec section 4 lists the body as "name, action codes", but
 * {@code core.role.code} is {@code NOT NULL} and unique in the tenant ({@code V021}), so the service
 * derives one from the name when none is given — "Payroll Reviewer" becomes {@code payroll-reviewer},
 * the same kebab form as the seven system codes. A client that wants a specific code may send it.
 * The code is fixed once created; {@link RoleUpdateRequest} has no such field.
 *
 * <p>No tenant field and there must never be one — the tenant is {@code TenantContext}'s.
 *
 * @param actionCodes required, may be empty. Every entry must exist in {@code reference.action}
 */
public record RoleCreateRequest(String code, String name, List<String> actionCodes) {}
