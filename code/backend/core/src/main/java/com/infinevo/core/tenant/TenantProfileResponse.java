package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * The bound tenant's profile (W-73.1, spec section 4).
 *
 * @param name the company name — read-only here; provisioning sets it
 * @param tagline the header's tagline, or {@code null} when none is set
 * @param logoDocumentId the logo's {@code core.document} id, or {@code null} when none is set
 * @param logoUrl a signed link to the logo, or {@code null} when there is no logo or no link service
 * @param leaveYearStartMonth the month (1-12) the tenant's leave year starts in (D-93); the allocation screen
 *     builds the leave-year window from it instead of assuming January
 */
public record TenantProfileResponse(
        String name, String tagline, UUID logoDocumentId, String logoUrl, Short leaveYearStartMonth) {

    /** Before D-93, without the leave-year start month. */
    public TenantProfileResponse(String name, String tagline, UUID logoDocumentId, String logoUrl) {
        this(name, tagline, logoDocumentId, logoUrl, null);
    }
}
