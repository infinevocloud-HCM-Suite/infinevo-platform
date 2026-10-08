package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * {@code PUT /api/v1/tenants/current/profile} (W-73.1, spec section 4).
 *
 * @param tagline the line under the company name in the header; blank clears it; at most 80 characters
 * @param logoDocumentId a live {@code TENANT_LOGO} document in the bound tenant, or {@code null} to remove the
 *     logo and fall back to the company's initials
 */
public record TenantProfileRequest(String tagline, UUID logoDocumentId) {}
