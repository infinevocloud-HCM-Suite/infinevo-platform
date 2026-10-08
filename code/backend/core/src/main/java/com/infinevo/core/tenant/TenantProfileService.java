package com.infinevo.core.tenant;

import java.time.Duration;
import java.util.Optional;

/**
 * The bound tenant's own profile — name, tagline, logo (W-73.1).
 *
 * <p>No method takes a tenant. Every one reads it from {@code TenantContext}, which the binding filter set
 * from the verified token; row-level security on {@code core.tenant} answers for that row alone, so while
 * platform staff act inside a customer tenant this is the customer's profile.
 */
public interface TenantProfileService {

    /**
     * How long the header's logo link lives (spec section 9): a day, so a page left open keeps its logo, and
     * within {@code DocumentLinkService.MAX_TTL}.
     */
    Duration LOGO_LINK_TTL = Duration.ofHours(24);

    /** The bound tenant's profile, with a signed link to its logo when it has one. */
    TenantProfileResponse current();

    /**
     * Sets the tagline and the logo. The name is not changed here.
     *
     * @throws IllegalArgumentException answered {@code 400}: a tagline over 80 characters, or a logo document
     *     that is not a live {@code TENANT_LOGO} image under 512 KB in the bound tenant — the same sentence
     *     whether it does not exist or belongs to another tenant
     */
    TenantProfileResponse update(TenantProfileRequest request);

    /**
     * What the header shows for the bound tenant: name, tagline, logo id and a signed link to the logo valid
     * for {@link #LOGO_LINK_TTL}. Empty when no tenant is bound or the row cannot be read.
     */
    Optional<TenantBranding> branding();
}
