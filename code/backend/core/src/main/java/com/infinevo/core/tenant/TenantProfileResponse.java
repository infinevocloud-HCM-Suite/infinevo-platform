package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * The bound tenant's profile (W-73.1, spec section 4).
 *
 * @param name the company name — read-only here; provisioning sets it
 * @param tagline the header's tagline, or {@code null} when none is set
 * @param logoDocumentId the logo's {@code core.document} id, or {@code null} when none is set
 * @param logoUrl a signed link to the logo, or {@code null} when there is no logo or no link service
 */
public record TenantProfileResponse(String name, String tagline, UUID logoDocumentId, String logoUrl) {}
