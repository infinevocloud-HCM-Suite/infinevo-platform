package com.infinevo.payroll.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a notice period settlement (buyout payout or shortfall recovery) upon employee exit (W-90.2).
 * Maps to {@code payroll.notice_period_settlement}.
 */
@Entity
@Table(name = "notice_period_settlement", schema = "payroll")
public class NoticePeriodSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "resignation_date", nullable = false)
    private LocalDate resignationDate;

    @Column(name = "expected_exit_date", nullable = false)
    private LocalDate expectedExitDate;

    @Column(name = "actual_exit_date", nullable = false)
    private LocalDate actualExitDate;

    @Column(name = "required_notice_days", nullable = false)
    private int requiredNoticeDays;

    @Column(name = "served_notice_days", nullable = false)
    private int servedNoticeDays;

    @Column(name = "shortfall_days", nullable = false)
    private int shortfallDays;

    @Column(name = "daily_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "rate_basis", nullable = false, length = 20)
    private String rateBasis;

    @Column(name = "settlement_type", nullable = false, length = 20)
    private String settlementType;

    @Column(name = "settlement_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "waiver_reason", length = 255)
    private String waiverReason;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public NoticePeriodSettlement() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.createdBy = "system";
        this.updatedBy = "system";
        this.status = "DRAFT";
        this.rateBasis = "GROSS";
        this.settlementType = "RECOVERY";
        this.settlementAmount = BigDecimal.ZERO;
    }

    public NoticePeriodSettlement(
            UUID tenantId,
            UUID employeeId,
            LocalDate resignationDate,
            LocalDate expectedExitDate,
            LocalDate actualExitDate,
            int requiredNoticeDays,
            int servedNoticeDays,
            int shortfallDays,
            BigDecimal dailyRate,
            String rateBasis,
            String settlementType,
            BigDecimal settlementAmount,
            String waiverReason,
            String approvedBy,
            String status,
            String createdBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.resignationDate = Objects.requireNonNull(resignationDate, "resignationDate");
        this.expectedExitDate = Objects.requireNonNull(expectedExitDate, "expectedExitDate");
        this.actualExitDate = Objects.requireNonNull(actualExitDate, "actualExitDate");
        this.requiredNoticeDays = requiredNoticeDays;
        this.servedNoticeDays = servedNoticeDays;
        this.shortfallDays = shortfallDays;
        this.dailyRate = Objects.requireNonNull(dailyRate, "dailyRate");
        this.rateBasis = rateBasis != null ? rateBasis : "GROSS";
        this.settlementType = Objects.requireNonNull(settlementType, "settlementType");
        this.settlementAmount = Objects.requireNonNull(settlementAmount, "settlementAmount");
        this.waiverReason = waiverReason;
        this.approvedBy = approvedBy;
        this.status = status != null ? status : "DRAFT";
        this.createdBy = createdBy != null ? createdBy : "system";
        this.updatedBy = this.createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

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

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getResignationDate() {
        return resignationDate;
    }

    public void setResignationDate(LocalDate resignationDate) {
        this.resignationDate = resignationDate;
    }

    public LocalDate getExpectedExitDate() {
        return expectedExitDate;
    }

    public void setExpectedExitDate(LocalDate expectedExitDate) {
        this.expectedExitDate = expectedExitDate;
    }

    public LocalDate getActualExitDate() {
        return actualExitDate;
    }

    public void setActualExitDate(LocalDate actualExitDate) {
        this.actualExitDate = actualExitDate;
    }

    public int getRequiredNoticeDays() {
        return requiredNoticeDays;
    }

    public void setRequiredNoticeDays(int requiredNoticeDays) {
        this.requiredNoticeDays = requiredNoticeDays;
    }

    public int getServedNoticeDays() {
        return servedNoticeDays;
    }

    public void setServedNoticeDays(int servedNoticeDays) {
        this.servedNoticeDays = servedNoticeDays;
    }

    public int getShortfallDays() {
        return shortfallDays;
    }

    public void setShortfallDays(int shortfallDays) {
        this.shortfallDays = shortfallDays;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public String getRateBasis() {
        return rateBasis;
    }

    public void setRateBasis(String rateBasis) {
        this.rateBasis = rateBasis;
    }

    public String getSettlementType() {
        return settlementType;
    }

    public void setSettlementType(String settlementType) {
        this.settlementType = settlementType;
    }

    public BigDecimal getSettlementAmount() {
        return settlementAmount;
    }

    public void setSettlementAmount(BigDecimal settlementAmount) {
        this.settlementAmount = settlementAmount;
    }

    public String getWaiverReason() {
        return waiverReason;
    }

    public void setWaiverReason(String waiverReason) {
        this.waiverReason = waiverReason;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
