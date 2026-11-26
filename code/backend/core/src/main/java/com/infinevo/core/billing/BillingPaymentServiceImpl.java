package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records payments against issued invoices and settles invoice balances atomically (W-94.1).
 */
@Service
public class BillingPaymentServiceImpl implements BillingPaymentService {

    private final BillingInvoiceRepository invoiceRepository;
    private final BillingPaymentRepository paymentRepository;
    private final BillingTenantExecutor tenantExecutor;

    public BillingPaymentServiceImpl(
            BillingInvoiceRepository invoiceRepository,
            BillingPaymentRepository paymentRepository,
            BillingTenantExecutor tenantExecutor) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.paymentRepository = Objects.requireNonNull(paymentRepository, "paymentRepository must not be null");
        this.tenantExecutor = Objects.requireNonNull(tenantExecutor, "tenantExecutor must not be null");
    }

    @Override
    @Transactional
    public BillingPaymentResponse recordPayment(UUID invoiceId, RecordPaymentRequest request) {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        tenantExecutor.platformTenant().requirePlatformTenant();

        if (request.paymentDate() == null) {
            throw new IllegalArgumentException("paymentDate must not be null");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (request.paymentMethod() == null) {
            throw new IllegalArgumentException("paymentMethod must not be null");
        }
        if (request.transactionReference() == null
                || request.transactionReference().isBlank()) {
            throw new IllegalArgumentException("transactionReference must not be blank");
        }
        String normalizedRef = request.transactionReference().trim();
        if (normalizedRef.length() > 100) {
            throw new IllegalArgumentException("transactionReference must be at most 100 characters");
        }
        BigDecimal paymentAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        UUID ownerTenant = tenantExecutor.resolvePlatformTargetTenantForInvoice(invoiceId);
        return tenantExecutor.asTenant(
                ownerTenant,
                () -> applyPaymentInBoundTenant(ownerTenant, invoiceId, request, paymentAmount, normalizedRef));
    }

    private BillingPaymentResponse applyPaymentInBoundTenant(
            UUID ownerTenant,
            UUID invoiceId,
            RecordPaymentRequest request,
            BigDecimal paymentAmount,
            String normalizedRef) {
        BillingInvoice invoice = invoiceRepository
                .findByIdAndTenantIdForUpdate(invoiceId, ownerTenant)
                .orElseThrow(() -> new BillingInvoiceNotFoundException(invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.CANCELLED
                || invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BillingConflictException("Cannot record payment for invoice " + invoice.getInvoiceNumber()
                    + " in status " + invoice.getStatus());
        }

        if (paymentRepository.existsByTenantIdAndTransactionReference(ownerTenant, normalizedRef)) {
            throw new BillingConflictException(
                    "Duplicate transactionReference '" + normalizedRef + "' for tenant " + ownerTenant);
        }

        String actor = BillingTenantExecutor.currentActor();
        BillingPayment payment = new BillingPayment(
                ownerTenant,
                invoiceId,
                request.paymentDate(),
                paymentAmount,
                request.paymentMethod(),
                normalizedRef,
                request.notes() != null && !request.notes().isBlank()
                        ? request.notes().trim()
                        : null,
                actor);
        BillingPayment savedPayment = paymentRepository.saveAndFlush(payment);

        BigDecimal cumulativePaid =
                paymentRepository.sumCompletedAmount(invoiceId, ownerTenant).setScale(2, RoundingMode.HALF_UP);
        invoice.setAmountPaid(cumulativePaid);
        invoice.setUpdatedBy(actor);
        if (cumulativePaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (cumulativePaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoiceRepository.saveAndFlush(invoice);

        return BillingPaymentResponse.from(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingPaymentResponse> listPayments(UUID invoiceId) {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        UUID targetTenant = tenantExecutor.resolveReadableTenantForInvoice(invoiceId);
        return tenantExecutor.asTenant(targetTenant, () -> {
            if (invoiceRepository.findByIdAndTenantId(invoiceId, targetTenant).isEmpty()) {
                throw new BillingInvoiceNotFoundException(invoiceId);
            }
            return paymentRepository
                    .findByInvoiceIdAndTenantIdOrderByPaymentDateAscCreatedAtAsc(invoiceId, targetTenant)
                    .stream()
                    .map(BillingPaymentResponse::from)
                    .toList();
        });
    }
}
