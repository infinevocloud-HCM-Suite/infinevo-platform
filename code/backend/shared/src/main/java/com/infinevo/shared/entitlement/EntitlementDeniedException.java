package com.infinevo.shared.entitlement;

import com.infinevo.shared.error.ApiError;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * Thrown when an entitlement check refuses access (W-12.2).
 *
 * <p>Carries an {@link ApiError} (either {@code MODULE_NOT_ENTITLED} or {@code TENANT_SUSPENDED})
 * and the {@link PlatformModule} that was required.
 */
public class EntitlementDeniedException extends AccessDeniedException {

    private final ApiError error;
    private final PlatformModule module;

    public EntitlementDeniedException(ApiError error, PlatformModule module) {
        super(Objects.requireNonNull(error, "error must not be null").defaultMessage()
                + (module != null ? ": requires module '" + module.name() + "'" : ""));
        this.error = error;
        this.module = module;
    }

    public ApiError error() {
        return error;
    }

    public PlatformModule module() {
        return module;
    }
}
