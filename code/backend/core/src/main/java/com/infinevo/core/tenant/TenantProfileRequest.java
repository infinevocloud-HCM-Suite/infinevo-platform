package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * {@code PUT /api/v1/tenants/current/profile} (W-73.1, W-116).
 *
 * @param tagline the line under the company name in the header; blank clears it; at most 80 characters
 * @param logoDocumentId a live {@code TENANT_LOGO} document in the bound tenant, or {@code null} to remove the
 *     logo and fall back to the company's initials
 * @param countryCode ISO 2-letter country code from reference.country, or null if unchanged
 * @param timezone IANA timezone string, or null if unchanged
 * @param leaveYearStartMonth month 1-12 when the company leave year starts, or null if unchanged
 */
public record TenantProfileRequest(
        String tagline, UUID logoDocumentId, String countryCode, String timezone, Short leaveYearStartMonth) {

    /** Older constructor sending only tagline and logo (W-73.1). */
    public TenantProfileRequest(String tagline, UUID logoDocumentId) {
        this(tagline, logoDocumentId, null, null, null);
    }
}
