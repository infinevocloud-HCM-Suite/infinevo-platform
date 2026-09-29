package com.infinevo.payroll.taxdeclaration.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an income tax declaration is not found for the requested criteria (W-32.1).
 *
 * <p>Produces HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DeclarationNotFoundException extends RuntimeException {

    public DeclarationNotFoundException(UUID id) {
        super("Income tax declaration not found with id " + id);
    }

    public DeclarationNotFoundException(UUID employeeId, String financialYear) {
        super("Income tax declaration not found for employee " + employeeId + " and FY " + financialYear);
    }
}
