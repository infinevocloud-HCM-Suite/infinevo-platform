package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * What the header shows for a tenant (W-73.1, W-116).
 *
 * @param name the company name
 * @param tagline the tagline, or {@code null}
 * @param logoDocumentId the logo's document id, or {@code null}
 * @param logoUrl a signed link to the logo, or {@code null} when there is no logo or no link service
 * @param leaveYearStartMonth the leave year start month (1-12)
 * @param countryCode the 2-letter country code (W-116)
 * @param timezone the tenant's IANA timezone (W-116)
 */
public record TenantBranding(
        String name,
        String tagline,
        UUID logoDocumentId,
        String logoUrl,
        Short leaveYearStartMonth,
        String countryCode,
        String timezone) {

    /** Before D-93, without the leave-year start month. */
    public TenantBranding(String name, String tagline, UUID logoDocumentId, String logoUrl) {
        this(name, tagline, logoDocumentId, logoUrl, null, null, null);
    }

    /** Before W-116, without country and timezone. */
    public TenantBranding(String name, String tagline, UUID logoDocumentId, String logoUrl, Short leaveYearStartMonth) {
        this(name, tagline, logoDocumentId, logoUrl, leaveYearStartMonth, null, null);
    }
}
