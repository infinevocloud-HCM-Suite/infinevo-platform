package com.infinevo.core.authz;

import java.util.List;
import java.util.UUID;

/** The roles one user holds in the bound tenant, after a grant (W-11.1, spec section 4). */
public record UserRolesResponse(UUID userAccountId, List<RoleResponse> roles) {}
