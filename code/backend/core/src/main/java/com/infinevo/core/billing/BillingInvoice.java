package com.infinevo.core.billing;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Subscription GST tax invoice entity ({@code core.billing_invoice}, W-94.1).
 */
@Entity
@Table(name = "billing_invoice", schema = "core")
@Audited
public class BillingInvoice {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invoice_number", nullable = false, length = 50, updatable = false)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "plan_tier", nullable = false, length = 30)
    private String planTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", nullable = false, length = 20)
    private BillingFrequency billingFrequency;

    @Column(name = "active_headcount", nullable = false)
    private int activeHeadcount;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "cgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "igst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal igstAmount;

    @Column(name = "total_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalTax;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO.setScale(2);

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "customer_gstin", length = 15)
    private String customerGstin;

    @Column(name = "sac_code", nullable = false, length = 10)
    private String sacCode = BillingProperties.DEFAULT_SAC_CODE;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillingInvoiceItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected BillingInvoice() {}

    public BillingInvoice(
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
            String customerGstin,
            String sacCode,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "invoiceNumber must not be null");
        this.invoiceDate = Objects.requireNonNull(invoiceDate, "invoiceDate must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.billingPeriodStart = Objects.requireNonNull(billingPeriodStart, "billingPeriodStart must not be null");
        this.billingPeriodEnd = Objects.requireNonNull(billingPeriodEnd, "billingPeriodEnd must not be null");
        this.planTier = Objects.requireNonNull(planTier, "planTier must not be null");
        this.billingFrequency = Objects.requireNonNull(billingFrequency, "billingFrequency must not be null");
        this.activeHeadcount = activeHeadcount;
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal must not be null");
        this.discountAmount = Objects.requireNonNull(discountAmount, "discountAmount must not be null");
        this.taxableAmount = Objects.requireNonNull(taxableAmount, "taxableAmount must not be null");
        this.cgstAmount = Objects.requireNonNull(cgstAmount, "cgstAmount must not be null");
        this.sgstAmount = Objects.requireNonNull(sgstAmount, "sgstAmount must not be null");
        this.igstAmount = Objects.requireNonNull(igstAmount, "igstAmount must not be null");
        this.totalTax = Objects.requireNonNull(totalTax, "totalTax must not be null");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        this.customerGstin = customerGstin;
        this.sacCode = Objects.requireNonNull(sacCode, "sacCode must not be null");
        String resolvedActor = actor != null && !actor.isBlank() ? actor : ACTOR_SYSTEM;
        this.createdBy = resolvedActor;
        this.updatedBy = resolvedActor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
        BillingInvoiceItem item = new BillingInvoiceItem(tenantId, this, description, quantity, unitPrice, amount);
        items.add(item);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getBillingPeriodStart() {
        return billingPeriodStart;
    }

    public LocalDate getBillingPeriodEnd() {
        return billingPeriodEnd;
    }

    public String getPlanTier() {
        return planTier;
    }

    public BillingFrequency getBillingFrequency() {
        return billingFrequency;
    }

    public int getActiveHeadcount() {
        return activeHeadcount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getCgstAmount() {
        return cgstAmount;
    }

    public BigDecimal getSgstAmount() {
        return sgstAmount;
    }

    public BigDecimal getIgstAmount() {
        return igstAmount;
    }

    public BigDecimal getTotalTax() {
        return totalTax;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = Objects.requireNonNull(amountPaid, "amountPaid must not be null");
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public String getCustomerGstin() {
        return customerGstin;
    }

    public String getSacCode() {
        return sacCode;
    }

    public List<BillingInvoiceItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy != null && !updatedBy.isBlank() ? updatedBy : ACTOR_SYSTEM;
    }
}
