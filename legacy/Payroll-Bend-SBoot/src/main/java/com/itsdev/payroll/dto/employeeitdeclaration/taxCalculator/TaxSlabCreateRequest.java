package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.util.List;

/**
 * Body for POST /api/tax-slabs (create a brand-new regime).
 *
 * Unlike TaxSlabSaveRequest (which updates an existing row and never touches
 * financialYear/taxRegime), a create request must supply both so the service
 * can check for a duplicate active row before inserting.
 */
public class TaxSlabCreateRequest {

    private String financialYear; // e.g. "2026-27"
    private String taxRegime;     // "OLD" or "NEW"
    private List<TaxSlabRowDTO> slabs;

    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }

    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }

    public List<TaxSlabRowDTO> getSlabs() { return slabs; }
    public void setSlabs(List<TaxSlabRowDTO> slabs) { this.slabs = slabs; }
}
