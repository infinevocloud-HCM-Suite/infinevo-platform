package com.itsdev.payroll.service.employeeitdeclaration.taxCalculator;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabCreateRequest;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabResponseDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.TaxSlabSaveRequest;

import java.util.List;

/**
 * Read + update API for income-tax regime slabs shown on the "TaxSlab Regim" tab.
 */
public interface TaxSlabService {

    /** All active slab regimes (typically one OLD + one NEW), parsed for the UI. */
    List<TaxSlabResponseDTO> getActiveSlabs();

    /**
     * Create a new regime (financialYear + taxRegime + initial slabs).
     * Rejects if an active row already exists for the same (FY, regime) pair.
     */
    TaxSlabResponseDTO createRegime(TaxSlabCreateRequest request, String createdBy);

    /**
     * Validate and save edited slabs for ONE regime, in place.
     * Writes a {@code TaxSlabMasterHistory} audit row.
     *
     * @param id        TaxSlabMaster.id being edited
     * @param request   edited slab rows
     * @param changedBy userId from the JWT (for audit)
     * @return the freshly saved regime as a response DTO
     */
    TaxSlabResponseDTO updateSlabs(Long id, TaxSlabSaveRequest request, String changedBy);

    /**
     * Soft-delete a regime by setting isActive = false.
     * Writes a DELETE audit row.
     */
    void deleteRegime(Long id, String deletedBy);
}
