package com.infinevo.payroll.schedule;

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
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Pay schedule entity (W-28) — {@code payroll.pay_schedule}.
 *
 * <p>Holds the tenant's work week, pay-day rule, input cut-off day, and the first period start date.
 * Exactly one row per tenant under row-level security.
 */
@Entity
@Table(
        name = "pay_schedule",
        schema = "payroll",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_pay_schedule_tenant",
                    columnNames = {"tenant_id"})
        })
public class PaySchedule {

    public static final String DEFAULT_FREQUENCY = "MONTHLY";
    public static final short DEFAULT_INPUT_CUTOFF_DAY = 25;
    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "frequency", nullable = false, length = 16)
    private String frequency = DEFAULT_FREQUENCY;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "working_days", nullable = false, columnDefinition = "smallint[]")
    private Short[] workingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_day_rule", nullable = false, length = 24)
    private PayDayRule payDayRule;

    @Column(name = "pay_day_of_month")
    private Short payDayOfMonth;

    @Column(name = "input_cutoff_day", nullable = false)
    private short inputCutoffDay = DEFAULT_INPUT_CUTOFF_DAY;

    @Column(name = "first_period_start", nullable = false)
    private LocalDate firstPeriodStart;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected PaySchedule() {}

    public PaySchedule(
            UUID tenantId,
            Short[] workingDays,
            PayDayRule payDayRule,
            Short payDayOfMonth,
            short inputCutoffDay,
            LocalDate firstPeriodStart) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.workingDays = Objects.requireNonNull(workingDays, "workingDays must not be null");
        this.payDayRule = Objects.requireNonNull(payDayRule, "payDayRule must not be null");
        this.payDayOfMonth = payDayOfMonth;
        this.inputCutoffDay = inputCutoffDay;
        this.firstPeriodStart = Objects.requireNonNull(firstPeriodStart, "firstPeriodStart must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (frequency == null) {
            frequency = DEFAULT_FREQUENCY;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency != null ? frequency : DEFAULT_FREQUENCY;
    }

    public Short[] getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(Short[] workingDays) {
        this.workingDays = Objects.requireNonNull(workingDays, "workingDays must not be null");
    }

    public Set<DayOfWeek> getWorkingDaysAsDayOfWeek() {
        if (workingDays == null) {
            return Collections.emptySet();
        }
        Set<DayOfWeek> set = new LinkedHashSet<>();
        for (Short day : workingDays) {
            if (day != null) {
                set.add(DayOfWeek.of(day));
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public List<Integer> getWorkingDaysAsList() {
        if (workingDays == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(workingDays)
                .filter(Objects::nonNull)
                .map(Short::intValue)
                .toList();
    }

    public void setWorkingDaysFromList(List<Integer> days) {
        Objects.requireNonNull(days, "days must not be null");
        this.workingDays =
                days.stream().filter(Objects::nonNull).map(Integer::shortValue).toArray(Short[]::new);
    }

    public PayDayRule getPayDayRule() {
        return payDayRule;
    }

    public void setPayDayRule(PayDayRule payDayRule) {
        this.payDayRule = Objects.requireNonNull(payDayRule, "payDayRule must not be null");
    }

    public Short getPayDayOfMonth() {
        return payDayOfMonth;
    }

    public void setPayDayOfMonth(Short payDayOfMonth) {
        this.payDayOfMonth = payDayOfMonth;
    }

    public short getInputCutoffDay() {
        return inputCutoffDay;
    }

    public void setInputCutoffDay(short inputCutoffDay) {
        this.inputCutoffDay = inputCutoffDay;
    }

    public LocalDate getFirstPeriodStart() {
        return firstPeriodStart;
    }

    public void setFirstPeriodStart(LocalDate firstPeriodStart) {
        this.firstPeriodStart = Objects.requireNonNull(firstPeriodStart, "firstPeriodStart must not be null");
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
