package com.infinevo.core.tenant;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Request payload for provisioning a new tenant with initial modules (W-12.1).
 *
 * <p>{@code admin_email} is optional (D-42): when given, provisioning invites that address as the new
 * tenant's {@code tenant-admin}.
 */
public record TenantRequest(
        String name,
        String country_code,
        String timezone,
        Short leave_year_start_month,
        Set<PlatformModule> modules,
        String admin_email) {

    /** The shape {@code ReportScheduleServiceImpl} checks recipients with: one {@code @}, a dot after it. */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** {@code core.user_invitation.email} is {@code VARCHAR(255)}. */
    private static final int EMAIL_MAX_LENGTH = 255;

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

    public String adminEmail() {
        return admin_email;
    }

    /**
     * The administrator email, trimmed and lower-cased, or {@code null} when none was given.
     *
     * @throws IllegalArgumentException if an email was given and is not a valid address
     */
    public String validatedAdminEmail() {
        if (admin_email == null || admin_email.isBlank()) {
            return null;
        }
        String email = admin_email.trim().toLowerCase(Locale.ROOT);
        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("admin_email must be a valid email address");
        }
        return email;
    }
}
