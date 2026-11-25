package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.employee.Employee;
import com.infinevo.payroll.payrun.PayRun;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What one pass over a pay run gives the bank file (W-88, W-88.2).
 *
 * @param payRun the run
 * @param rows the employees the file pays, sorted by employee number
 * @param employees the employee records behind the rows, by id, for fields W-88's row does not carry
 * @param omissions the considered employees left out, and why
 * @param runNetPay the net pay of every included employee in the run
 * @param selectedNetPay the net pay of the employees considered — the run, or the selection
 */
public record BankFileRun(
        PayRun payRun,
        List<BankFileRow> rows,
        Map<UUID, Employee> employees,
        List<BankFileOmission> omissions,
        BigDecimal runNetPay,
        BigDecimal selectedNetPay) {

    public BankFileRun {
        Objects.requireNonNull(payRun, "payRun must not be null");
        rows = List.copyOf(rows);
        employees = Map.copyOf(employees);
        omissions = List.copyOf(omissions);
        Objects.requireNonNull(runNetPay, "runNetPay must not be null");
        Objects.requireNonNull(selectedNetPay, "selectedNetPay must not be null");
    }

    /** The sum of the rows' net pay. */
    public BigDecimal fileTotal() {
        return rows.stream().map(BankFileRow::netPay).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
