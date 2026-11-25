package com.infinevo.payroll.payrun.bankfile;

import java.util.List;

/** One catalogue field for the template editor: its code, label, kind and the formats it accepts (W-88.2). */
public record BankFileFieldResponse(String code, String label, BankFileValueKind kind, List<String> formats) {

    static BankFileFieldResponse from(BankFileField field) {
        return new BankFileFieldResponse(
                field.name(), field.label(), field.kind(), field.kind().formats());
    }
}
