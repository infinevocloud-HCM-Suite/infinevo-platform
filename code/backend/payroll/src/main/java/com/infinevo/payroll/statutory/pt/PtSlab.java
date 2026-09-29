package com.infinevo.payroll.statutory.pt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * National reference table holding statutory professional tax slabs per state (W-31.2, V064).
 */
@Entity
@Table(name = "pt_slab", schema = "reference")
@Immutable
public class PtSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "state_code", length = 10, nullable = false)
    private String stateCode;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "from_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", precision = 19, scale = 4)
    private BigDecimal toAmount;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "is_female_exempt", nullable = false)
    private boolean isFemaleExempt;

    @Column(name = "deduction_months", length = 32)
    private String deductionMonths;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PtSlab() {}

    public PtSlab(
            String stateCode,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal fromAmount,
            BigDecimal toAmount,
            BigDecimal amount,
            boolean isFemaleExempt,
            String deductionMonths,
            short sortOrder) {
        this.stateCode = stateCode;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.amount = amount;
        this.isFemaleExempt = isFemaleExempt;
        this.deductionMonths = deductionMonths;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getStateCode() {
        return stateCode;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public BigDecimal getFromAmount() {
        return fromAmount;
    }

    public BigDecimal getToAmount() {
        return toAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isFemaleExempt() {
        return isFemaleExempt;
    }

    public String getDeductionMonths() {
        return deductionMonths;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PtSlabDto toDto() {
        return new PtSlabDto(fromAmount, toAmount, amount, isFemaleExempt, PtSlabDto.parseMonths(deductionMonths));
    }
}
