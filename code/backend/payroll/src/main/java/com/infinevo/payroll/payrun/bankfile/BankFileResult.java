package com.infinevo.payroll.payrun.bankfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Result of generating a bank salary payment file (W-88), containing the CSV bytes
 * and metadata regarding included vs omitted employees.
 *
 * @param content CSV byte content with UTF-8 BOM
 * @param filename suggested download filename
 * @param totalEmployees total included employees in pay run
 * @param exportedEmployees count of employees written into bank file
 * @param omittedEmployees count of employees excluded (missing bank, non-bank mode, invalid IFSC/account, zero net pay)
 * @param fileTotal sum of net pay amounts written to bank file
 * @param runNetPay total net pay of the pay run
 * @param omittedEmployeeNumbers list of employee numbers omitted
 */
public record BankFileResult(
        byte[] content,
        String filename,
        int totalEmployees,
        int exportedEmployees,
        int omittedEmployees,
        BigDecimal fileTotal,
        BigDecimal runNetPay,
        List<String> omittedEmployeeNumbers) {

    public BankFileResult {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(fileTotal, "fileTotal must not be null");
        Objects.requireNonNull(runNetPay, "runNetPay must not be null");
        omittedEmployeeNumbers = omittedEmployeeNumbers == null ? List.of() : List.copyOf(omittedEmployeeNumbers);
    }
}
