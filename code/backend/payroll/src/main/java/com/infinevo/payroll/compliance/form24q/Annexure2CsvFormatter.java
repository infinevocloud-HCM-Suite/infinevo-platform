package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Formats Form 24Q Annexure II annual dataset into RFC 4180 compliant CSV (W-89.3).
 */
public final class Annexure2CsvFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private Annexure2CsvFormatter() {}

    public static byte[] format(Annexure2PreviewResponse preview) {
        StringBuilder sb = new StringBuilder();

        // CSV Header
        sb.append("Sl No,Employee Code,PAN of Employee,Name of Employee,Regime,Period From,Period To,Gross"
                        + " Salary,Exemptions u/s 10,Standard Deduction u/s 16(ia),Professional Tax u/s"
                        + " 16(iii),Total Deductions u/s 16,Income under Salaries,House Property Income,Other"
                        + " Income,Gross Total Income,Chapter VI-A Deductions,Total Taxable Income,Tax on Total"
                        + " Income,Rebate u/s 87A,Surcharge,Health & Education Cess,Relief u/s 89,Total Tax"
                        + " Payable,TDS Current Employer,TDS Previous Employer,Shortfall / (Excess)")
                .append("\r\n");

        for (SalaryDetailRecord r : preview.records()) {
            sb.append(r.serialNumberInBatch()).append(",");
            sb.append(escape(r.employeeNumber())).append(",");
            sb.append(escape(r.pan())).append(",");
            sb.append(escape(r.employeeName())).append(",");
            sb.append(escape(r.regime())).append(",");
            sb.append(escape(r.employmentFrom() != null ? r.employmentFrom().format(DATE_FORMATTER) : ""))
                    .append(",");
            sb.append(escape(r.employmentTo() != null ? r.employmentTo().format(DATE_FORMATTER) : ""))
                    .append(",");
            sb.append(formatAmount(r.grossSalary())).append(",");
            sb.append(formatAmount(r.exemptionsSection10())).append(",");
            sb.append(formatAmount(r.standardDeduction())).append(",");
            sb.append(formatAmount(r.professionalTax())).append(",");
            sb.append(formatAmount(r.totalDeductionsSection16())).append(",");
            sb.append(formatAmount(r.incomeChargeableUnderSalaries())).append(",");
            sb.append(formatAmount(r.housePropertyIncome())).append(",");
            sb.append(formatAmount(r.otherIncome())).append(",");
            sb.append(formatAmount(r.grossTotalIncome())).append(",");
            sb.append(formatAmount(r.chapterViaDeductions())).append(",");
            sb.append(formatAmount(r.totalTaxableIncome())).append(",");
            sb.append(formatAmount(r.taxOnTotalIncome())).append(",");
            sb.append(formatAmount(r.rebate87A())).append(",");
            sb.append(formatAmount(r.surcharge())).append(",");
            sb.append(formatAmount(r.cess())).append(",");
            sb.append(formatAmount(r.relief89())).append(",");
            sb.append(formatAmount(r.totalTaxPayable())).append(",");
            sb.append(formatAmount(r.totalTdsDeducted())).append(",");
            sb.append(formatAmount(r.prevEmployerTds())).append(",");
            sb.append(formatAmount(r.shortfallOrExcess()));
            sb.append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return "\"" + value + "\"";
    }
}
