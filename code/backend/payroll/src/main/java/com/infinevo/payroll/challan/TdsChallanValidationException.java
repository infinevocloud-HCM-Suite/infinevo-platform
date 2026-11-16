package com.infinevo.payroll.challan;

/**
 * Thrown when TDS challan input fails statutory or business validation rules.
 */
public class TdsChallanValidationException extends RuntimeException {
    public TdsChallanValidationException(String message) {
        super(message);
    }
}
