package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.util.List;

/**
 * Body of PUT /api/tax-slabs/{id}.
 *
 * Carries only the edited slab rows. The regime, financial year and active flag
 * are NEVER taken from the client — they are preserved from the existing master
 * record so the tax engine (which looks up by FY + regime) keeps resolving the
 * same row.
 */
public class TaxSlabSaveRequest {

    private List<TaxSlabRowDTO> slabs;

    public List<TaxSlabRowDTO> getSlabs() { return slabs; }
    public void setSlabs(List<TaxSlabRowDTO> slabs) { this.slabs = slabs; }
}
