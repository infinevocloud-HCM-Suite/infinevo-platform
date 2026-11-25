package com.infinevo.payroll.payrun.bankfile;

/**
 * A bank file template request that cannot be met (W-88.2): {@link Kind#NOT_FOUND} is {@code 404},
 * {@link Kind#CONFLICT} — a name in use, or a change to the built-in — is {@code 409}.
 */
public class BankFileTemplateException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        CONFLICT
    }

    private final Kind kind;

    public BankFileTemplateException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
