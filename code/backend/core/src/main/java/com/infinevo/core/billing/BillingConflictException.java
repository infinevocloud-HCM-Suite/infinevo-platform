package com.infinevo.core.billing;

/** Thrown when a billing operation conflicts with current invoice/payment/subscription state (W-94.1). */
public class BillingConflictException extends RuntimeException {

    public BillingConflictException(String message) {
        super(message);
    }
}
