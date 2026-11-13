package com.infinevo.payroll.payrun.bankfile;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One row in the bank salary payment file (W-88).
 *
 * @param employeeNumber employee identifier unique within tenant
 * @param employeeName full name of the employee
 * @param accountHolderName beneficiary account holder name as recorded in bank section
 * @param bankAccountNumber beneficiary bank account number (never numeric, preserves leading zeros)
 * @param ifscCode beneficiary bank IFSC code
 * @param bankName name of the bank
 * @param accountType account type (e.g. SAVINGS, CURRENT, SALARY)
 * @param paymentMode payment mode (e.g. BANK_TRANSFER)
 * @param netPay final net salary payable
 * @param narration transfer remark/narration (e.g. "Salary 2026-11")
 */
public record BankFileRow(
        String employeeNumber,
        String employeeName,
        String accountHolderName,
        String bankAccountNumber,
        String ifscCode,
        String bankName,
        String accountType,
        String paymentMode,
        BigDecimal netPay,
        String narration) {

    public BankFileRow {
        Objects.requireNonNull(employeeNumber, "employeeNumber must not be null");
        Objects.requireNonNull(netPay, "netPay must not be null");
    }
}
