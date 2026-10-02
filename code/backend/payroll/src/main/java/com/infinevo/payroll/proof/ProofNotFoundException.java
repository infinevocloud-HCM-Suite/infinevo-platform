package com.infinevo.payroll.proof;

import java.io.Serial;

/**
 * No such proof, item or file for this caller. One answer for "never existed", "another employee's" and
 * "another item's", so an id cannot be probed (W-34.1 spec section 4).
 */
public class ProofNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProofNotFoundException(String message) {
        super(message);
    }
}
