package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * The bound tenant's profile (W-73.1, W-116).
 *
 * @param name the company name — read-only here; provisioning sets it
 * @param tagline the header's tagline, or {@code null} when none is set
 * @param logoDocumentId the logo's {@code core.document} id, or {@code null} when none is set
 * @param logoUrl a signed link to the logo, or {@code null} when there is no logo or no link service
 * @param leaveYearStartMonth the month (1-12) the tenant's leave year starts in (D-93)
 * @param countryCode the 2-letter country code (W-116)
 * @param timezone the tenant's IANA timezone (W-116)
 * @param countryLocked true if the tenant has any processed pay runs (W-116)
 * @param leaveYearLocked true if the tenant has any leave allocations or requests (W-116)
 */
public record TenantProfileResponse(
        String name,
        String tagline,
        UUID logoDocumentId,
        String logoUrl,
        Short leaveYearStartMonth,
        String countryCode,
        String timezone,
        boolean countryLocked,
        boolean leaveYearLocked) {

    /** Before D-93, without the leave-year start month. */
    public TenantProfileResponse(String name, String tagline, UUID logoDocumentId, String logoUrl) {
        this(name, tagline, logoDocumentId, logoUrl, null, null, null, false, false);
    }

    /** Before W-116, with leave-year start month only. */
    public TenantProfileResponse(
            String name, String tagline, UUID logoDocumentId, String logoUrl, Short leaveYearStartMonth) {
        this(name, tagline, logoDocumentId, logoUrl, leaveYearStartMonth, null, null, false, false);
    }
}
