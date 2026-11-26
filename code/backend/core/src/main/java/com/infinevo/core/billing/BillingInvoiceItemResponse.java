package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.util.UUID;

/** Line item view returned inside {@link BillingInvoiceResponse} (W-94.1). */
public record BillingInvoiceItemResponse(
        UUID id, String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {

    static BillingInvoiceItemResponse from(BillingInvoiceItem item) {
        return new BillingInvoiceItemResponse(
                item.getId(), item.getDescription(), item.getQuantity(), item.getUnitPrice(), item.getAmount());
    }
}
