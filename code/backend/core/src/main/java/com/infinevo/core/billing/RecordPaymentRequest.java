package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request payload to record an offline/online payment against an invoice (W-94.1). */
public record RecordPaymentRequest(
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String transactionReference,
        String notes) {}
