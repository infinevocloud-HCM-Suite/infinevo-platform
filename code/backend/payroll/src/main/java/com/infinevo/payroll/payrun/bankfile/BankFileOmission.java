package com.infinevo.payroll.payrun.bankfile;

import java.util.Objects;

/**
 * An employee included in the pay run but left out of the bank file, and why (W-88 rules, W-88.2 preview).
 *
 * @param employeeNumber the employee's number, or their id when the employee record is missing
 * @param reason in words: missing bank details, payment mode, blank account or IFSC, no net pay
 */
public record BankFileOmission(String employeeNumber, String reason) {

    public BankFileOmission {
        Objects.requireNonNull(employeeNumber, "employeeNumber must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
