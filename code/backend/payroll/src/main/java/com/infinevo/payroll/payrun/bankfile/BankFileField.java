package com.infinevo.payroll.payrun.bankfile;

/**
 * The fixed field catalogue a bank file column can draw from (W-88.2 spec section 4.1). A new field is added
 * here, with its label and kind, and given a value in {@link BankFileValues}.
 */
public enum BankFileField {
    EMPLOYEE_NUMBER("Employee number", BankFileValueKind.TEXT),
    EMPLOYEE_NAME("Employee name", BankFileValueKind.TEXT),
    FIRST_NAME("First name", BankFileValueKind.TEXT),
    LAST_NAME("Last name", BankFileValueKind.TEXT),
    ACCOUNT_HOLDER_NAME("Account holder name", BankFileValueKind.TEXT),
    ACCOUNT_NUMBER("Account number", BankFileValueKind.TEXT),
    IFSC("IFSC", BankFileValueKind.TEXT),
    BANK_NAME("Bank name", BankFileValueKind.TEXT),
    ACCOUNT_TYPE("Account type", BankFileValueKind.TEXT),
    NARRATION("Narration (Salary + period)", BankFileValueKind.TEXT),
    NET_PAY("Net pay", BankFileValueKind.AMOUNT),
    PAY_PERIOD("Pay period", BankFileValueKind.PERIOD),
    PAID_ON("Paid on", BankFileValueKind.DATE),
    VALUE_DATE("File date", BankFileValueKind.DATE),
    WORK_EMAIL("Work email", BankFileValueKind.TEXT),
    MOBILE("Mobile", BankFileValueKind.TEXT),
    DEPARTMENT("Department", BankFileValueKind.TEXT),
    DESIGNATION("Designation", BankFileValueKind.TEXT),
    // Founder 2026-11-27: from the employee's Bank tab (core.employee_bank, V260).
    TRANSFER_TYPE("Transfer type (FT, IMPS, NEFT, RTGS)", BankFileValueKind.TEXT),
    CREDIT_NARRATION("Credit narration (employee's)", BankFileValueKind.TEXT);

    private final String label;
    private final BankFileValueKind kind;

    BankFileField(String label, BankFileValueKind kind) {
        this.label = label;
        this.kind = kind;
    }

    public String label() {
        return label;
    }

    public BankFileValueKind kind() {
        return kind;
    }
}
