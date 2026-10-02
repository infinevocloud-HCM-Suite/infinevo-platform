package com.infinevo.payroll.tds;

/**
 * Which actor or subsystem produced an employee TDS record (W-36.1 §4).
 *
 * <p>Replaces legacy POI_BASED / DEFAULT_REGIME / SALARY_REVISION (legacy/enumeration/TdsSourceType.java:5-7)
 * which collapse into {@link #DECLARATION}; the reason is kept in the record's note.
 */
public enum TdsSource {
    /** Written by the tax calculator (W-33) from an approved investment declaration. */
    DECLARATION,
    /** Set directly by a payroll officer via PUT /api/v1/payroll/employees/{id}/tds/{fy}. */
    OFFICER
}
