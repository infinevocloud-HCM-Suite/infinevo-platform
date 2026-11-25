package com.infinevo.payroll.payrun.bankfile;

/** Where a bank file column's value comes from (W-88.2 spec section 4.1). */
public enum BankFileColumnSource {
    /** A field from {@link BankFileField}. */
    FIELD,
    /** The same text on every row, such as {@code NEFT} or the company's debit account. */
    FIXED,
    /** 1, 2, 3 … in file order. */
    SERIAL
}
