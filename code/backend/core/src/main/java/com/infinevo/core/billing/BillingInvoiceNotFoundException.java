package com.infinevo.core.billing;

import java.util.UUID;

/** Thrown when a requested billing invoice does not exist in the visible tenant scope (W-94.1). */
public class BillingInvoiceNotFoundException extends RuntimeException {

    public BillingInvoiceNotFoundException(UUID invoiceId) {
        super("Billing invoice not found: " + invoiceId);
    }
}
