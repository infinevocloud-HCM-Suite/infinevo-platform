package com.infinevo.shared.entitlement;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that reaching this controller or method requires the bound tenant to hold an active
 * entitlement to the named {@link PlatformModule} (W-12.2).
 *
 * <p>Enforced by {@link RequiresModuleAspect}. A customer who has not purchased the module
 * receives {@code 403} with code {@code MODULE_NOT_ENTITLED}; a suspended tenant receives {@code 403}
 * with code {@code TENANT_SUSPENDED}.
 *
 * <p>If a module was revoked, read-only HTTP methods (GET, HEAD, OPTIONS) pass under
 * {@link Mode#READ_WRITE} for historical retention, while mutating methods (POST, PUT, DELETE, PATCH)
 * are refused with {@code MODULE_NOT_ENTITLED}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequiresModule {

    /** The platform module that must be entitled. */
    PlatformModule value();

    /** Enforcement mode for this endpoint. */
    Mode mode() default Mode.READ_WRITE;

    enum Mode {
        /**
         * Permissive: allows safe HTTP methods (GET, HEAD, OPTIONS) if the module was revoked
         * (for statutory historical retention), while requiring active entitlement for state mutations.
         */
        READ_WRITE,

        /**
         * Strict: requires active entitlement for all HTTP methods; revoked modules are refused even for reads.
         */
        ACTIVE_ONLY
    }
}
