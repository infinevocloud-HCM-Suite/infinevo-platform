package com.infinevo.shared.entitlement;

/**
 * The sellable platform modules a tenant may hold a subscription to (W-12.1).
 *
 * <p>Exactly two values — {@link #HRMS} and {@link #PAYROLL}. {@code core} is not a module;
 * every tenant holds all core capabilities.
 */
public enum PlatformModule {
    HRMS,
    PAYROLL
}
