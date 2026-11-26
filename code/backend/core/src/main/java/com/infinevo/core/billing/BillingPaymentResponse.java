package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** API response representation of a recorded invoice payment (W-94.1). */
public record BillingPaymentResponse(
        UUID id,
        UUID tenantId,
        UUID invoiceId,
        LocalDate paymentDate,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        String transactionReference,
        PaymentStatus status,
        String notes,
        String recordedBy,
        Instant createdAt) {

    static BillingPaymentResponse from(BillingPayment payment) {
        return new BillingPaymentResponse(
                payment.getId(),
                payment.getTenantId(),
                payment.getInvoiceId(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getStatus(),
                payment.getNotes(),
                payment.getRecordedBy(),
                payment.getCreatedAt());
    }
}
