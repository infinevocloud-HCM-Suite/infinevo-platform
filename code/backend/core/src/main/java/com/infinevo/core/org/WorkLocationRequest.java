package com.infinevo.core.org;

/**
 * What a client may say about a work location on create and on update (W-14.1, spec section 4).
 *
 * <p>No tenant, no id and no audit field — see {@link OrgMasterRequest}.
 *
 * <p>{@code stateCode} is asked for separately from {@code state} and is not derived from it: a
 * filing needs the code and free text cannot supply one (spec section 6).
 *
 * @param filingAddress null means false. At most one work location per tenant may set it, and the
 *     second is <strong>refused</strong> rather than quietly moving the flag —
 *     {@link WorkLocationService.FilingAddressAlreadySetException}.
 */
public record WorkLocationRequest(
        String code,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String stateCode,
        String zipCode,
        String countryCode,
        Boolean filingAddress,
        Boolean active)
        implements OrgMasterRequest {}
