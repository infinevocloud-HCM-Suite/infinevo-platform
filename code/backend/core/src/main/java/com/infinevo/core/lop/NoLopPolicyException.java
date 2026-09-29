package com.infinevo.core.lop;

/**
 * Thrown when a tenant has no loss-of-pay policy in force for a period or date,
 * or when required inputs (such as working week configuration) are missing (W-18.1 §4, §13 decision 1).
 */
public class NoLopPolicyException extends RuntimeException {

    public NoLopPolicyException(String message) {
        super(message);
    }

    public NoLopPolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
