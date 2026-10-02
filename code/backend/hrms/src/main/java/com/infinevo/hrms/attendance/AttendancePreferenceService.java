package com.infinevo.hrms.attendance;

/**
 * Service contract for managing attendance preferences (W-40.1).
 */
public interface AttendancePreferenceService {

    /**
     * Retrieves the current tenant's attendance preferences.
     *
     * <p>If the tenant has not saved custom preferences, returns system defaults with
     * {@code isDefault = true}.
     *
     * @return the active attendance preferences or system defaults
     */
    AttendancePreferenceResponse current();

    /**
     * Saves attendance preferences for the current tenant.
     *
     * <p>Upserts the single configuration row per tenant, enforcing validation rules.
     *
     * @param request the preferences to save
     * @return the saved attendance preferences with {@code isDefault = false}
     */
    AttendancePreferenceResponse save(AttendancePreferenceRequest request);
}
