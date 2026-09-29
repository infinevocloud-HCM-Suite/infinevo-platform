package com.infinevo.payroll.taxdeclaration.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when statutory reference rules or catalogue limits are missing from the reference database tables.
 *
 * <p>Produces HTTP 500 Internal Server Error.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ReferenceDataMissingException extends RuntimeException {

    public ReferenceDataMissingException(String table, String financialYear, String regime) {
        super(String.format(
                "Required statutory reference data missing from %s for FY=%s, regime=%s",
                table, financialYear, regime));
    }

    public ReferenceDataMissingException(String table, String key) {
        super(String.format("Required statutory reference data missing from %s for key=%s", table, key));
    }
}
