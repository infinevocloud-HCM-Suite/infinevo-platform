package com.infinevo.payroll.payrun;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasis;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One employee a run considered, {@code INCLUDED} or {@code SKIPPED} with the reason (W-29.1 §6),
 * and the five totals of its lines once computed (W-29.2 §6). No name or number snapshot (W-29.1 §13
 * decision 7): the lines carry the component snapshots.
 */
@Entity
@Table(name = "employee_payrun", schema = "payroll")
@Audited
public class EmployeePayRun {

    /** Zero at the stored scale, so a new or reset row reads back exactly as it was written. */
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(4);

    /** Zero at the day-count scale (W-29.3 §6). */
    private static final BigDecimal ZERO_DAYS = BigDecimal.ZERO.setScale(2);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "payrun_id", nullable = false, updatable = false)
    private UUID payrunId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "salary_version_id", updatable = false)
    private UUID salaryVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "inclusion_status", nullable = false, length = 16, updatable = false)
    private InclusionStatus inclusionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 32, updatable = false)
    private SkipReason skipReason;

    @Column(name = "gross_earnings", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossEarnings = ZERO_AMOUNT;

    @Column(name = "total_reimbursements", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalReimbursements = ZERO_AMOUNT;

    @Column(name = "total_benefits", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBenefits = ZERO_AMOUNT;

    @Column(name = "total_deductions", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDeductions = ZERO_AMOUNT;

    @Column(name = "net_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal netPay = ZERO_AMOUNT;

    @Column(name = "lop_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal lopDays = ZERO_DAYS;

    @Column(name = "unpaid_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal unpaidDays = ZERO_DAYS;

    @Column(name = "paid_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidDays = ZERO_DAYS;

    @Column(name = "unpriced_input_count", nullable = false)
    private int unpricedInputCount;

    @Column(name = "computed_at")
    private Instant computedAt;

    @Column(name = "computation_error", length = 500)
    private String computationError;

    @Column(name = "computed_attempt", nullable = false)
    private int computedAttempt;

    @Column(name = "lop_policy_id")
    private UUID lopPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "working_day_basis", length = 24)
    private WorkingDayBasis workingDayBasis;

    @Column(name = "pay_divisor", precision = 10, scale = 2)
    private BigDecimal payDivisor;

    @Column(name = "payable_days", precision = 10, scale = 2)
    private BigDecimal payableDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "lop_rounding", length = 16)
    private LopRounding lopRounding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected EmployeePayRun() {}

    public EmployeePayRun(UUID tenantId, UUID payrunId, InclusionDecision decision, String actor) {
        Objects.requireNonNull(decision, "decision must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.payrunId = Objects.requireNonNull(payrunId, "payrunId must not be null");
        this.employeeId = decision.employeeId();
        this.salaryVersionId = decision.salaryVersionId();
        this.inclusionStatus = decision.inclusionStatus();
        this.skipReason = decision.skipReason();
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /** A named employee's row on an off-cycle run (W-30.2): included with or without a salary version. */
    public EmployeePayRun(UUID tenantId, UUID payrunId, OffCycleInclusion inclusion, String actor) {
        Objects.requireNonNull(inclusion, "inclusion must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.payrunId = Objects.requireNonNull(payrunId, "payrunId must not be null");
        this.employeeId = inclusion.employeeId();
        this.salaryVersionId = inclusion.salaryVersionId();
        this.inclusionStatus = inclusion.inclusionStatus();
        this.skipReason = inclusion.skipReason();
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /**
     * The row's totals and day figures after a successful computation, with the policy stamp that
     * produced them (W-18.2); clears any earlier error. {@code unpricedInputCount} is the number of
     * overtime rows with hours and no amount (W-29.3 §3). No stamp, no figure: the stamp is required.
     */
    public void recordComputation(
            PayRunTotals totals,
            PayRunDays days,
            int unpricedInputCount,
            PolicyStamp stamp,
            int attempt,
            String actor,
            Instant at) {
        Objects.requireNonNull(totals, "totals must not be null");
        Objects.requireNonNull(days, "days must not be null");
        Objects.requireNonNull(stamp, "stamp must not be null: a pay figure is never written without its policy");
        this.lopPolicyId = stamp.policyId();
        this.workingDayBasis = stamp.workingDayBasis();
        this.payDivisor = stamp.divisor().setScale(2, RoundingMode.HALF_UP);
        this.payableDays = stamp.payableDays().setScale(2, RoundingMode.HALF_UP);
        this.lopRounding = stamp.lopRounding();
        this.lopDays = days.lopDays();
        this.unpaidDays = days.unpaidDays();
        this.paidDays = days.paidDays();
        this.unpricedInputCount = unpricedInputCount;
        this.grossEarnings = totals.grossEarnings().raw();
        this.totalReimbursements = totals.totalReimbursements().raw();
        this.totalBenefits = totals.totalBenefits().raw();
        this.totalDeductions = totals.totalDeductions().raw();
        // Rounded to 2 by PayRunTotals, held at the column scale so it reads back unchanged.
        this.netPay = totals.netPay().setScale(4);
        this.computedAt = Objects.requireNonNull(at, "at must not be null");
        this.computationError = null;
        this.computedAttempt = attempt;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    /**
     * The employee could not be computed: totals back to zero, the reason kept on the row, and no stamp —
     * there is no figure for it to explain.
     */
    public void recordError(String error, int attempt, String actor, Instant at) {
        Objects.requireNonNull(error, "error must not be null");
        this.grossEarnings = ZERO_AMOUNT;
        this.totalReimbursements = ZERO_AMOUNT;
        this.totalBenefits = ZERO_AMOUNT;
        this.totalDeductions = ZERO_AMOUNT;
        this.netPay = ZERO_AMOUNT;
        this.lopDays = ZERO_DAYS;
        this.unpaidDays = ZERO_DAYS;
        this.paidDays = ZERO_DAYS;
        this.unpricedInputCount = 0;
        this.lopPolicyId = null;
        this.workingDayBasis = null;
        this.payDivisor = null;
        this.payableDays = null;
        this.lopRounding = null;
        this.computedAt = Objects.requireNonNull(at, "at must not be null");
        this.computationError = error.length() > 500 ? error.substring(0, 500) : error;
        this.computedAttempt = attempt;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPayrunId() {
        return payrunId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getSalaryVersionId() {
        return salaryVersionId;
    }

    public InclusionStatus getInclusionStatus() {
        return inclusionStatus;
    }

    public SkipReason getSkipReason() {
        return skipReason;
    }

    public BigDecimal getGrossEarnings() {
        return grossEarnings;
    }

    public BigDecimal getTotalReimbursements() {
        return totalReimbursements;
    }

    public BigDecimal getTotalBenefits() {
        return totalBenefits;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public BigDecimal getLopDays() {
        return lopDays;
    }

    public BigDecimal getUnpaidDays() {
        return unpaidDays;
    }

    public BigDecimal getPaidDays() {
        return paidDays;
    }

    public BigDecimal getPayableDays() {
        return payableDays;
    }

    public int getUnpricedInputCount() {
        return unpricedInputCount;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public String getComputationError() {
        return computationError;
    }

    /** The policy stamp behind this row's figure (W-18.2); empty before a computation and after a failure. */
    public Optional<PolicyStamp> getStamp() {
        if (lopPolicyId == null) {
            return Optional.empty();
        }
        return Optional.of(new PolicyStamp(lopPolicyId, workingDayBasis, payDivisor, payableDays, lopRounding));
    }

    /** The attempt that last computed this row (W-29.4); zero before the first. */
    public int getComputedAttempt() {
        return computedAttempt;
    }
}
