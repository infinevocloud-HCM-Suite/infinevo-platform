package com.infinevo.core.billing;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.subscription.Subscription;
import com.infinevo.core.subscription.SubscriptionNotFoundException;
import com.infinevo.core.subscription.SubscriptionRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates GST-compliant subscription invoices and lists tenant billing history (W-94.1).
 */
@Service
public class BillingInvoiceServiceImpl implements BillingInvoiceService {

    private final BillingInvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EmployeeRepository employeeRepository;
    private final BillingPricingCalculator pricingCalculator;
    private final GstCalculator gstCalculator;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final BillingProperties billingProperties;
    private final BillingTenantExecutor tenantExecutor;
    private final JdbcTemplate jdbcTemplate;

    public BillingInvoiceServiceImpl(
            BillingInvoiceRepository invoiceRepository,
            SubscriptionRepository subscriptionRepository,
            EmployeeRepository employeeRepository,
            BillingPricingCalculator pricingCalculator,
            GstCalculator gstCalculator,
            InvoiceNumberGenerator invoiceNumberGenerator,
            BillingProperties billingProperties,
            BillingTenantExecutor tenantExecutor,
            JdbcTemplate jdbcTemplate) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.subscriptionRepository =
                Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.pricingCalculator = Objects.requireNonNull(pricingCalculator, "pricingCalculator must not be null");
        this.gstCalculator = Objects.requireNonNull(gstCalculator, "gstCalculator must not be null");
        this.invoiceNumberGenerator =
                Objects.requireNonNull(invoiceNumberGenerator, "invoiceNumberGenerator must not be null");
        this.billingProperties = Objects.requireNonNull(billingProperties, "billingProperties must not be null");
        this.tenantExecutor = Objects.requireNonNull(tenantExecutor, "tenantExecutor must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    @Transactional
    public BillingInvoiceResponse generate(GenerateInvoiceRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        tenantExecutor.platformTenant().requirePlatformTenant();

        UUID targetTenantId = request.tenantId();
        if (targetTenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (tenantExecutor.platformTenant().isPlatformTenant(targetTenantId)) {
            throw new IllegalArgumentException("Cannot generate a billing invoice for the platform tenant");
        }
        if (request.billingPeriodStart() == null || request.billingPeriodEnd() == null) {
            throw new IllegalArgumentException("billingPeriodStart and billingPeriodEnd must not be null");
        }
        if (request.billingPeriodEnd().isBefore(request.billingPeriodStart())) {
            throw new IllegalArgumentException("billingPeriodEnd must not be before billingPeriodStart");
        }

        LocalDate invoiceDate = request.invoiceDate() != null ? request.invoiceDate() : LocalDate.now();
        LocalDate dueDate = request.dueDate() != null
                ? request.dueDate()
                : invoiceDate.plusDays(billingProperties.defaultDueDays());
        if (dueDate.isBefore(invoiceDate)) {
            throw new IllegalArgumentException("dueDate must not be before invoiceDate");
        }
        BillingFrequency frequency =
                request.billingFrequency() != null ? request.billingFrequency() : BillingFrequency.MONTHLY;

        return tenantExecutor.asTenant(
                targetTenantId,
                () -> createInvoiceInBoundTenant(targetTenantId, request, frequency, invoiceDate, dueDate));
    }

    private BillingInvoiceResponse createInvoiceInBoundTenant(
            UUID targetTenantId,
            GenerateInvoiceRequest request,
            BillingFrequency frequency,
            LocalDate invoiceDate,
            LocalDate dueDate) {
        Subscription subscription = subscriptionRepository
                .findByTenantId(targetTenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(targetTenantId));
        if (subscription.getPlan() == null) {
            throw new BillingConflictException("Tenant " + targetTenantId + " has no subscription plan configured");
        }

        long rawCount = employeeRepository.countByTenantIdAndDeletedFalseAndStatusNot(
                targetTenantId, EmploymentStatus.TERMINATED);
        int activeHeadcount = Math.toIntExact(rawCount);

        BillingPricingCalculator.PricingResult pricing = pricingCalculator.calculate(
                subscription.getPlan(), subscription.getSupportPlan(), frequency, activeHeadcount);
        GstCalculator.GstResult gst =
                gstCalculator.calculate(pricing.taxableAmount(), request.customerGstin(), request.customerStateCode());

        String invoiceNumber = invoiceNumberGenerator.nextNumber(invoiceDate);
        String actor = BillingTenantExecutor.currentActor();

        BillingInvoice invoice = new BillingInvoice(
                targetTenantId,
                invoiceNumber,
                invoiceDate,
                dueDate,
                request.billingPeriodStart(),
                request.billingPeriodEnd(),
                subscription.getPlan().name(),
                frequency,
                activeHeadcount,
                pricing.subtotal(),
                pricing.discountAmount(),
                pricing.taxableAmount(),
                gst.cgstAmount(),
                gst.sgstAmount(),
                gst.igstAmount(),
                gst.totalTax(),
                gst.totalAmount(),
                gst.normalizedGstin(),
                billingProperties.sacCode(),
                actor);

        for (BillingPricingCalculator.LineItemSpec item : pricing.items()) {
            invoice.addItem(item.description(), item.quantity(), item.unitPrice(), item.amount());
        }

        BillingInvoice saved = invoiceRepository.saveAndFlush(invoice);
        return BillingInvoiceResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingInvoiceResponse getInvoice(UUID invoiceId) {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        UUID targetTenant = tenantExecutor.resolveReadableTenantForInvoice(invoiceId);
        return tenantExecutor.asTenant(targetTenant, () -> invoiceRepository
                .findByIdAndTenantId(invoiceId, targetTenant)
                .map(BillingInvoiceResponse::from)
                .orElseThrow(() -> new BillingInvoiceNotFoundException(invoiceId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingInvoiceResponse> listInvoices(UUID tenantIdFilter) {
        UUID boundTenant = TenantContext.require();
        boolean isPlatform = tenantExecutor.platformTenant().isPlatformTenant(boundTenant);

        if (!isPlatform) {
            if (tenantIdFilter != null && !tenantIdFilter.equals(boundTenant)) {
                tenantExecutor.platformTenant().requirePlatformTenant();
            }
            return invoiceRepository.findByTenantIdOrderByInvoiceDateDescCreatedAtDesc(boundTenant).stream()
                    .map(BillingInvoiceResponse::from)
                    .toList();
        }

        if (tenantIdFilter != null) {
            return tenantExecutor.asTenant(
                    tenantIdFilter,
                    () -> invoiceRepository.findByTenantIdOrderByInvoiceDateDescCreatedAtDesc(tenantIdFilter).stream()
                            .map(BillingInvoiceResponse::from)
                            .toList());
        }

        List<UUID> tenantIds =
                jdbcTemplate.queryForList("SELECT tenant_id FROM core.list_billing_invoice_tenants()", UUID.class);
        List<BillingInvoiceResponse> all = new ArrayList<>();
        for (UUID tenantId : tenantIds) {
            all.addAll(tenantExecutor.asTenant(
                    tenantId,
                    () -> invoiceRepository.findByTenantIdOrderByInvoiceDateDescCreatedAtDesc(tenantId).stream()
                            .map(BillingInvoiceResponse::from)
                            .toList()));
        }
        all.sort(Comparator.comparing(BillingInvoiceResponse::invoiceDate, Comparator.reverseOrder())
                .thenComparing(BillingInvoiceResponse::createdAt, Comparator.reverseOrder()));
        return all;
    }
}
