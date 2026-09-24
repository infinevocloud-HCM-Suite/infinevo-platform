package com.infinevo.core.employee.detail;

/**
 * What kind of bank account an employee is paid into (W-13.2) —
 * {@code core.employee_bank.bank_account_type},
 * {@code migration/src/main/resources/db/migration/core/V019__employee_bank.sql}.
 *
 * <p>Stored as the string name, never as an ordinal, for the reason {@link PaymentMode} gives.
 *
 * <p>The frozen column is free text — {@code bank_account_type} at
 * {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeeBankDetail.java:34},
 * with no vocabulary stated anywhere.
 *
 * <p><strong>There is deliberately no {@code CHECK} constraint on the column</strong>, and the
 * consequence is the same one {@link PaymentMode} carries: a row written outside the service fails
 * at <em>read</em> as a Hibernate enum conversion error, not at write. <strong>W-67</strong> is the
 * ticket that knows what the frozen values actually are and can turn this into a constraint.
 */
public enum BankAccountType {

    /** A savings account — the ordinary case for salary in India. */
    SAVINGS,

    /** A current account. */
    CURRENT,

    /** A salary account opened by the employer with the payroll bank. */
    SALARY
}
