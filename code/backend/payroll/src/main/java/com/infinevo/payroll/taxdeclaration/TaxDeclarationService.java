package com.infinevo.payroll.taxdeclaration;

import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing employee income tax declarations and their lifecycle (W-32.1).
 */
public interface TaxDeclarationService {

    TaxDeclarationResponse readOwn(String financialYear);

    TaxDeclarationResponse saveOwn(String financialYear, TaxDeclarationRequest request);

    TaxDeclarationResponse submitOwn(String financialYear);

    TaxDeclarationResponse reopenOwn(String financialYear);

    TaxDeclarationResponse read(UUID employeeId, String financialYear);

    TaxDeclarationResponse save(UUID employeeId, String financialYear, TaxDeclarationRequest request);

    TaxDeclarationResponse submit(UUID employeeId, String financialYear);

    TaxDeclarationResponse reopen(UUID employeeId, String financialYear);

    TaxDeclarationResponse lock(UUID employeeId, String financialYear);

    TaxDeclarationResponse unlock(UUID employeeId, String financialYear);

    /**
     * Gatekeeper for all section writes (.2, .3, .4).
     *
     * <p>Returns true if the declaration is DRAFT, unlocked, and the FY window is currently open.
     */
    boolean editable(UUID declarationId);

    /**
     * Gatekeeper with optional window check bypass (for officer actions).
     */
    boolean editable(UUID declarationId, boolean ignoreWindow);

    EmployeeInvestmentDeclaration require(UUID declarationId);

    EmployeeInvestmentDeclaration require(UUID employeeId, String financialYear);

    Optional<EmployeeInvestmentDeclaration> find(UUID employeeId, String financialYear);
}
