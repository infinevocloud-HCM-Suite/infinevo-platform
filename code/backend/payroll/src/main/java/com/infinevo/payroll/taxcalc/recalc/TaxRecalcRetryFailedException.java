package com.infinevo.payroll.taxcalc.recalc;

/** A retried tax recalculation failed again (D-147); the failure row carries the new message. Answered 422. */
public class TaxRecalcRetryFailedException extends RuntimeException {

    public TaxRecalcRetryFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
