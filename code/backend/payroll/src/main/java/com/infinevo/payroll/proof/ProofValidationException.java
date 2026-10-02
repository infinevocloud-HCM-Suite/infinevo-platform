package com.infinevo.payroll.proof;

import java.io.Serial;

/** A proof request whose values are wrong, whatever the state; the controller answers {@code 400}. */
public class ProofValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProofValidationException(String message) {
        super(message);
    }
}
