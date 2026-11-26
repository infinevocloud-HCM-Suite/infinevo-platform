package com.infinevo.core.billing;

/** Settlement lifecycle status for {@code core.billing_invoice} (W-94.1). */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}
