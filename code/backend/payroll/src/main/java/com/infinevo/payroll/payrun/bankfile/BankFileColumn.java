package com.infinevo.payroll.payrun.bankfile;

/**
 * One column of a bank file template, stored in {@code payroll.bank_file_template.columns} (W-88.2).
 *
 * @param heading the column's heading in the file
 * @param source where the value comes from
 * @param field the catalogue field, for {@link BankFileColumnSource#FIELD}; null otherwise
 * @param value the fixed text, for {@link BankFileColumnSource#FIXED}; null otherwise
 * @param format a date, period or amount format the field's kind accepts; null for the default or for text
 */
public record BankFileColumn(
        String heading, BankFileColumnSource source, BankFileField field, String value, String format) {}
