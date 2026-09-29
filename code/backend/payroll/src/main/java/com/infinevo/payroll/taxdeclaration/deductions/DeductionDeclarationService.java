package com.infinevo.payroll.taxdeclaration.deductions;

import com.infinevo.payroll.taxdeclaration.deductions.dto.DeductionDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6AItemResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import java.util.List;
import java.util.UUID;

/**
 * Service managing Chapter VI-A investments, pre-tax deductions, and previous employment declarations (W-32.3).
 */
public interface DeductionDeclarationService {

    List<Section6AItemResponse> getSection6AItemsOwn(String financialYear);

    List<Section6AItemResponse> getSection6AItems(UUID employeeId, String financialYear);

    DeductionDeclarationResponse readOwn(String financialYear);

    DeductionDeclarationResponse read(UUID employeeId, String financialYear);

    DeductionDeclarationResponse readByDeclarationId(UUID declarationId);

    DeductionDeclarationResponse replaceSection6AOwn(String financialYear, List<Section6ALineRequest> requests);

    DeductionDeclarationResponse replaceSection6A(
            UUID employeeId, String financialYear, List<Section6ALineRequest> requests);

    DeductionDeclarationResponse replacePreTaxDeductionsOwn(
            String financialYear, List<PreTaxDeductionRequest> requests);

    DeductionDeclarationResponse replacePreTaxDeductions(
            UUID employeeId, String financialYear, List<PreTaxDeductionRequest> requests);

    DeductionDeclarationResponse replacePrevEmploymentOwn(String financialYear, List<PrevEmploymentRequest> requests);

    DeductionDeclarationResponse replacePrevEmployment(
            UUID employeeId, String financialYear, List<PrevEmploymentRequest> requests);
}
