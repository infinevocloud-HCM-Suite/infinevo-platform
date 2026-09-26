package com.infinevo.core.tenant;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;

/**
 * Request payload for provisioning a new tenant with initial modules (W-12.1).
 */
public record TenantRequest(
        String name, String country_code, String timezone, Short leave_year_start_month, Set<PlatformModule> modules) {

    /**
     * Alternative camelCase accessors for ergonomic Java usage.
     */
    public String countryCode() {
        return country_code;
    }

    public String timeZone() {
        return timezone;
    }

    public Short leaveYearStartMonth() {
        return leave_year_start_month;
    }
}
