package com.infinevo.payroll.statutory.pt;

/**
 * Indicates whether a state's professional tax slabs are loaded from the national reference seed,
 * a tenant-specific override, or if no professional tax applies to the state (W-31.2).
 */
public enum PtSource {
    REFERENCE,
    OVERRIDE,
    NONE
}
