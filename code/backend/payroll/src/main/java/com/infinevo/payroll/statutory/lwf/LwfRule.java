package com.infinevo.payroll.statutory.lwf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Dated statutory calculation rule for a state's Labour Welfare Fund (W-92).
 */
@Entity
@Table(name = "lwf_rule", schema = "reference")
public class LwfRule {

    public static final String MODE_FLAT = "FLAT";
    public static final String MODE_PERCENTAGE_WITH_CAP = "PERCENTAGE_WITH_CAP";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "state_code", length = 10, nullable = false)
    private String stateCode;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "deduction_months", nullable = false, columnDefinition = "integer[]")
    private Integer[] deductionMonths;

    @Column(name = "calc_mode", length = 32, nullable = false)
    private String calcMode;

    @Column(name = "min_gross", precision = 14, scale = 4, nullable = false)
    private BigDecimal minGross = BigDecimal.ZERO;

    @Column(name = "max_gross", precision = 14, scale = 4)
    private BigDecimal maxGross;

    @Column(name = "employee_amount", precision = 14, scale = 4)
    private BigDecimal employeeAmount;

    @Column(name = "employer_amount", precision = 14, scale = 4)
    private BigDecimal employerAmount;

    @Column(name = "employee_rate", precision = 6, scale = 4)
    private BigDecimal employeeRate;

    @Column(name = "employer_rate", precision = 6, scale = 4)
    private BigDecimal employerRate;

    @Column(name = "employee_cap", precision = 14, scale = 4)
    private BigDecimal employeeCap;

    @Column(name = "employer_cap", precision = 14, scale = 4)
    private BigDecimal employerCap;

    protected LwfRule() {}

    public LwfRule(
            UUID id,
            String stateCode,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Integer[] deductionMonths,
            String calcMode,
            BigDecimal minGross,
            BigDecimal maxGross,
            BigDecimal employeeAmount,
            BigDecimal employerAmount,
            BigDecimal employeeRate,
            BigDecimal employerRate,
            BigDecimal employeeCap,
            BigDecimal employerCap) {
        this.id = id;
        this.stateCode = stateCode;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.deductionMonths = deductionMonths;
        this.calcMode = calcMode;
        this.minGross = minGross != null ? minGross : BigDecimal.ZERO;
        this.maxGross = maxGross;
        this.employeeAmount = employeeAmount;
        this.employerAmount = employerAmount;
        this.employeeRate = employeeRate;
        this.employerRate = employerRate;
        this.employeeCap = employeeCap;
        this.employerCap = employerCap;
    }

    public UUID getId() {
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

    public List<Integer> getDeductionMonthsAsList() {
        return deductionMonths != null ? Arrays.asList(deductionMonths) : List.of();
    }

    public Integer[] getDeductionMonths() {
        return deductionMonths;
    }

    public String getCalcMode() {
        return calcMode;
    }

    public BigDecimal getMinGross() {
        return minGross;
    }

    public BigDecimal getMaxGross() {
        return maxGross;
    }

    public BigDecimal getEmployeeAmount() {
        return employeeAmount;
    }

    public BigDecimal getEmployerAmount() {
        return employerAmount;
    }

    public BigDecimal getEmployeeRate() {
        return employeeRate;
    }

    public BigDecimal getEmployerRate() {
        return employerRate;
    }

    public BigDecimal getEmployeeCap() {
        return employeeCap;
    }

    public BigDecimal getEmployerCap() {
        return employerCap;
    }

    public boolean appliesToMonth(int month) {
        if (deductionMonths == null) {
            return false;
        }
        for (Integer m : deductionMonths) {
            if (m != null && m == month) {
                return true;
            }
        }
        return false;
    }
}
