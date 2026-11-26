package com.infinevo.payroll.challan;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * ITNS 281 TDS deposit challan record (W-89.1).
 */
@Entity
@Audited
@Table(
        name = "tds_challan",
        schema = "payroll",
        indexes = {
            @Index(
                    name = "uk_tds_challan_bsr_serial_date",
                    columnList = "tenant_id, bsr_code, challan_serial_no, tender_date",
                    unique = true),
            @Index(name = "idx_tds_challan_tenant_fy_quarter", columnList = "tenant_id, financial_year, quarter")
        })
public class TdsChallan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "quarter", nullable = false, length = 2)
    private String quarter;

    @Column(name = "bsr_code", nullable = false, length = 7)
    private String bsrCode;

    @Column(name = "challan_serial_no", nullable = false, length = 5)
    private String challanSerialNo;

    @Column(name = "tender_date", nullable = false)
    private LocalDate tenderDate;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "surcharge_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal surchargeAmount = BigDecimal.ZERO;

    @Column(name = "cess_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    @Column(name = "interest_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "fee_penalty_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal feePenaltyAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "minor_head", nullable = false, length = 3)
    private String minorHead = "200";

    @Column(name = "remarks", length = 255)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public TdsChallan() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getQuarter() {
        return quarter;
    }

    public void setQuarter(String quarter) {
        this.quarter = quarter;
    }

    public String getBsrCode() {
        return bsrCode;
    }

    public void setBsrCode(String bsrCode) {
        this.bsrCode = bsrCode;
    }

    public String getChallanSerialNo() {
        return challanSerialNo;
    }

    public void setChallanSerialNo(String challanSerialNo) {
        this.challanSerialNo = challanSerialNo;
    }

    public LocalDate getTenderDate() {
        return tenderDate;
    }

    public void setTenderDate(LocalDate tenderDate) {
        this.tenderDate = tenderDate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getSurchargeAmount() {
        return surchargeAmount;
    }

    public void setSurchargeAmount(BigDecimal surchargeAmount) {
        this.surchargeAmount = surchargeAmount;
    }

    public BigDecimal getCessAmount() {
        return cessAmount;
    }

    public void setCessAmount(BigDecimal cessAmount) {
        this.cessAmount = cessAmount;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public void setInterestAmount(BigDecimal interestAmount) {
        this.interestAmount = interestAmount;
    }

    public BigDecimal getFeePenaltyAmount() {
        return feePenaltyAmount;
    }

    public void setFeePenaltyAmount(BigDecimal feePenaltyAmount) {
        this.feePenaltyAmount = feePenaltyAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getMinorHead() {
        return minorHead;
    }

    public void setMinorHead(String minorHead) {
        this.minorHead = minorHead;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** Tax + surcharge + cess: the part of the deposit that is split across employees. Interest and fee are not. */
    public BigDecimal allocatable() {
        return taxAmount.add(surchargeAmount).add(cessAmount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TdsChallan that = (TdsChallan) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
