package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API response representation of a generated GST subscription invoice (W-94.1). */
public record BillingInvoiceResponse(
        UUID id,
        UUID tenantId,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        String planTier,
        BillingFrequency billingFrequency,
        int activeHeadcount,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal totalTax,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        String currency,
        InvoiceStatus status,
        String customerGstin,
        String sacCode,
        List<BillingInvoiceItemResponse> items,
        Instant createdAt) {

    static BillingInvoiceResponse from(BillingInvoice invoice) {
        List<BillingInvoiceItemResponse> itemResponses = invoice.getItems().stream()
                .map(BillingInvoiceItemResponse::from)
                .toList();
        return new BillingInvoiceResponse(
                invoice.getId(),
                invoice.getTenantId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getBillingPeriodStart(),
                invoice.getBillingPeriodEnd(),
                invoice.getPlanTier(),
                invoice.getBillingFrequency(),
                invoice.getActiveHeadcount(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxableAmount(),
                invoice.getCgstAmount(),
                invoice.getSgstAmount(),
                invoice.getIgstAmount(),
                invoice.getTotalTax(),
                invoice.getTotalAmount(),
                invoice.getAmountPaid(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getCustomerGstin(),
                invoice.getSacCode(),
                itemResponses,
                invoice.getCreatedAt());
    }
}
