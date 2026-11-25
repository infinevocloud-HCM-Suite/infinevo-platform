package com.infinevo.core.tenant;

/**
 * {@code PUT /api/v1/tenants/{id}/country} (W-116).
 *
 * @param countryCode the 2-letter country code
 */
public record TenantCountryRequest(String countryCode) {}
