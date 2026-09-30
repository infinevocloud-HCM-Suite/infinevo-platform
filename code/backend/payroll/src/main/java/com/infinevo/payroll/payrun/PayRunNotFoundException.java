package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.UUID;

/** No such run in the bound tenant — another tenant's run is reported the same way. Maps to {@code 404}. */
public class PayRunNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayRunNotFoundException(UUID id) {
        super("No pay run " + id + " in this tenant");
    }
}
