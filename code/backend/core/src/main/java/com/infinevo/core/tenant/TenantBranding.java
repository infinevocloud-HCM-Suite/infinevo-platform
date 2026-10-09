package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * What the header shows for a tenant (W-73.1): the three {@code core.tenant} columns read in one query,
 * and the logo's signed link resolved from the document id.
 *
 * @param name the company name
 * @param tagline the tagline, or {@code null}
 * @param logoDocumentId the logo's document id, or {@code null}
 * @param logoUrl a signed link to the logo, or {@code null} when there is no logo or no link service
 */
public record TenantBranding(String name, String tagline, UUID logoDocumentId, String logoUrl) {}
