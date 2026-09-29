package com.infinevo.payroll.taxdeclaration;

/**
 * Status of an employee's annual income tax declaration (W-32.1).
 *
 * <p>Spec §4: {@code DRAFT} and {@code SUBMITTED} only. Legacy's {@code NOT_CREATED} was a stub
 * the read returned before a row existed; here the first read creates the row. {@code APPROVED}
 * is W-34's state on the proof of investment, not on this table.
 */
public enum DeclarationStatus {
    DRAFT,
    SUBMITTED
}
