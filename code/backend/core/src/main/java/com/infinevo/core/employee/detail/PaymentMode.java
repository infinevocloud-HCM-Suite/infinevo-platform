package com.infinevo.core.employee.detail;

/**
 * How an employee is paid (W-13.2) — {@code core.employee_bank.payment_mode},
 * {@code migration/src/main/resources/db/migration/core/V019__employee_bank.sql}.
 *
 * <p>Stored as the string name, never as an ordinal. An ordinal survives a reordering of this file
 * silently and turns every existing row into a different payment mode.
 *
 * <p>The frozen column is free text — {@code payment_mode} at
 * {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeeBankDetail.java:18},
 * with the vocabulary written in a comment above it at {@code :15}
 * ({@code // Payment mode: banktransfer, cash, cheque etc.}) and nowhere else. A comment is not a
 * constraint, so each screen invents its own spelling. Three named values replace it.
 *
 * <p><strong>There is deliberately no {@code CHECK} constraint on the column.</strong> That is the
 * same gap W-13.1 recorded for {@code core.employee.status}: the service is what keeps the
 * vocabulary, and a row written outside it — by a migration, a script, or a fix applied by hand —
 * fails at <em>read</em> rather than at write, as a Hibernate enum conversion error naming a
 * constant that does not exist. The constraint cannot be added before the real frozen values are
 * known, and <strong>W-67</strong> is the ticket that holds the production data to find out.
 */
public enum PaymentMode {

    /** Credited to the bank account on this section. The bank fields are meaningful only for this. */
    BANK_TRANSFER,

    /** Paid in cash. */
    CASH,

    /** Paid by cheque. */
    CHEQUE
}
