package com.itsdev.payroll.entity.employeeTDS;

import com.itsdev.payroll.enumeration.TdsSourceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_tds")
public class EmployeeTds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 255)
    private String employeeId;

    @Column(name = "organization_id", nullable = false, length = 255)
    private String organizationId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "tds_source_type", nullable = false, length = 50)
    private TdsSourceType tdsSourceType;

    @Column(name = "poi_id")
    private Long poiId;

    @Column(name = "tax_regime", nullable = false, length = 20)
    private String taxRegime; // "OLD" or "NEW"

    @Column(name = "annual_gross_salary", precision = 12, scale = 2, nullable = false)
    private BigDecimal annualGrossSalary;

    @Column(name = "annual_taxable_income", precision = 12, scale = 2, nullable = false)
    private BigDecimal annualTaxableIncome;

    @Column(name = "final_annual_tax", precision = 12, scale = 2, nullable = false)
    private BigDecimal finalAnnualTax;

    @Column(name = "effective_from_month", nullable = false, length = 20)
    private String effectiveFromMonth;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }

    public TdsSourceType getTdsSourceType() { return tdsSourceType; }
    public void setTdsSourceType(TdsSourceType tdsSourceType) { this.tdsSourceType = tdsSourceType; }

    public Long getPoiId() { return poiId; }
    public void setPoiId(Long poiId) { this.poiId = poiId; }

    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }

    public BigDecimal getAnnualGrossSalary() { return annualGrossSalary; }
    public void setAnnualGrossSalary(BigDecimal annualGrossSalary) { this.annualGrossSalary = annualGrossSalary; }

    public BigDecimal getAnnualTaxableIncome() { return annualTaxableIncome; }
    public void setAnnualTaxableIncome(BigDecimal annualTaxableIncome) { this.annualTaxableIncome = annualTaxableIncome; }

    public BigDecimal getFinalAnnualTax() { return finalAnnualTax; }
    public void setFinalAnnualTax(BigDecimal finalAnnualTax) { this.finalAnnualTax = finalAnnualTax; }

    public String getEffectiveFromMonth() { return effectiveFromMonth; }
    public void setEffectiveFromMonth(String effectiveFromMonth) { this.effectiveFromMonth = effectiveFromMonth; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Helper method to get display name
    public String getTdsSourceTypeDisplayName() {
        return tdsSourceType != null ? tdsSourceType.getDisplayName() : "";
    }
}