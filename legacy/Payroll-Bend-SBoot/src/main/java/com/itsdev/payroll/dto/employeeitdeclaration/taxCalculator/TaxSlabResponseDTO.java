package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.util.List;

/**
 * What GET /api/tax-slabs returns for ONE regime card (OLD or NEW).
 *
 * The stored {@code slabJson} string is parsed into a structured {@code slabs}
 * list so the React table can render rows directly without parsing JSON itself.
 */
public class TaxSlabResponseDTO {

    private Long id;
    private String financialYear;
    private String taxRegime;     // OLD or NEW
    private Boolean isActive;
    private List<TaxSlabRowDTO> slabs;

    public TaxSlabResponseDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }

    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public List<TaxSlabRowDTO> getSlabs() { return slabs; }
    public void setSlabs(List<TaxSlabRowDTO> slabs) { this.slabs = slabs; }
}
