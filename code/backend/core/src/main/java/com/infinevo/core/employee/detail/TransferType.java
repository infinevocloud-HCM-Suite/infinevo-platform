package com.infinevo.core.employee.detail;

/**
 * How the bank moves an employee's salary — {@code core.employee_bank.transfer_type},
 * {@code V260__employee_bank_transfer_type_and_narration.sql}. Separate from {@link PaymentMode}: payment mode
 * decides whether the employee is paid by bank at all; this is the bank's transfer method. The database checks
 * the same four values.
 */
public enum TransferType {
    /** Fund transfer within the same bank. */
    FT,
    IMPS,
    NEFT,
    RTGS
}
