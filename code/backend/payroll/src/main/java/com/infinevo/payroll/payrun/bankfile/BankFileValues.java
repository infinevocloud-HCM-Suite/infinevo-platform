package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.employee.Employee;
import com.infinevo.payroll.payrun.PayRun;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns one bank file row and a template column into the text the file holds (W-88.2 spec sections 4.1, 4.2). */
final class BankFileValues {

    private BankFileValues() {}

    static String value(
            BankFileColumn column, int serial, BankFileRow row, Employee employee, PayRun run, LocalDate today) {
        return switch (column.source()) {
            case SERIAL -> String.valueOf(serial);
            case FIXED -> column.value() == null ? "" : column.value();
            case FIELD -> field(column.field(), formatOf(column), row, employee, run, today);
        };
    }

    /** The column's format, or its field kind's default when none is set. */
    static String formatOf(BankFileColumn column) {
        if (column.format() != null && !column.format().isBlank()) {
            return column.format();
        }
        return column.field() == null ? null : column.field().kind().defaultFormat();
    }

    private static String field(
            BankFileField field, String format, BankFileRow row, Employee employee, PayRun run, LocalDate today) {
        return switch (field) {
            case EMPLOYEE_NUMBER -> row.employeeNumber();
            case EMPLOYEE_NAME -> row.employeeName();
            case FIRST_NAME -> text(employee == null ? null : employee.getFirstName());
            case LAST_NAME -> text(employee == null ? null : employee.getLastName());
            case ACCOUNT_HOLDER_NAME -> row.accountHolderName();
            case ACCOUNT_NUMBER -> row.bankAccountNumber();
            case IFSC -> row.ifscCode();
            case BANK_NAME -> row.bankName();
            case ACCOUNT_TYPE -> row.accountType();
            case NARRATION -> row.narration();
            case NET_PAY -> amount(row.netPay(), format);
            case PAY_PERIOD -> period(run.getPeriod(), format);
            case PAID_ON -> run.getPaidOn() == null ? "" : date(run.getPaidOn(), format);
            case VALUE_DATE -> date(today, format);
            case WORK_EMAIL -> text(employee == null ? null : employee.getWorkEmail());
            case MOBILE -> text(employee == null ? null : employee.getMobile());
            case DEPARTMENT ->
                employee == null || employee.getDepartment() == null
                        ? ""
                        : text(employee.getDepartment().getName());
            case DESIGNATION ->
                employee == null || employee.getDesignation() == null
                        ? ""
                        : text(employee.getDesignation().getName());
        };
    }

    static String amount(BigDecimal amount, String format) {
        return switch (format == null ? "DECIMAL_2" : format) {
            case "WHOLE_RUPEES" -> amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
            case "PAISE" ->
                amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
            default -> amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        };
    }

    static String date(LocalDate date, String pattern) {
        return date.format(DateTimeFormatter.ofPattern(pattern == null ? "dd/MM/yyyy" : pattern, Locale.ENGLISH));
    }

    static String period(YearMonth period, String pattern) {
        return period.format(DateTimeFormatter.ofPattern(pattern == null ? "yyyy-MM" : pattern, Locale.ENGLISH));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
