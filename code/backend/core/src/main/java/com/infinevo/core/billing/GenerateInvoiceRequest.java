package com.infinevo.core.billing;

import java.time.LocalDate;
import java.util.UUID;

/** Request payload to generate a GST tax invoice for a tenant subscription (W-94.1). */
public record GenerateInvoiceRequest(
        UUID tenantId,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BillingFrequency billingFrequency,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String customerGstin,
        String customerStateCode) {}
