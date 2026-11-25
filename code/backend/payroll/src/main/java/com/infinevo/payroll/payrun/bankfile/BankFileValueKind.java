package com.infinevo.payroll.payrun.bankfile;

import java.util.List;

/** What sort of value a bank file field holds, and so which formats it takes (W-88.2 spec section 4.2). */
public enum BankFileValueKind {
    TEXT(List.of()),
    AMOUNT(List.of("DECIMAL_2", "WHOLE_RUPEES", "PAISE")),
    DATE(List.of("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "ddMMyyyy", "MM/dd/yyyy")),
    PERIOD(List.of("yyyy-MM", "MMM yyyy", "MM/yyyy"));

    private final List<String> formats;

    BankFileValueKind(List<String> formats) {
        this.formats = formats;
    }

    /** The formats this kind accepts; the first is the default. Empty for text, which takes none. */
    public List<String> formats() {
        return formats;
    }

    public String defaultFormat() {
        return formats.isEmpty() ? null : formats.get(0);
    }
}
