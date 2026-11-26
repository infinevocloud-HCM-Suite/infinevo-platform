package com.infinevo.payroll.challan;

/**
 * Thrown when the requested TDS challan does not exist for the tenant.
 */
public class TdsChallanNotFoundException extends RuntimeException {
    public TdsChallanNotFoundException(String message) {
        super(message);
    }
}
