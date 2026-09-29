package com.infinevo.payroll.taxdeclaration;

import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowResponse;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TaxDeclarationWindowService {

    TaxDeclarationWindowResponse get(String financialYear);

    TaxDeclarationWindowResponse upsert(String financialYear, TaxDeclarationWindowRequest request);

    boolean isOpen(String financialYear, LocalDate today);

    Optional<IncomeTaxDeclarationWindow> find(UUID tenantId, String financialYear);

    IncomeTaxDeclarationWindow findOrCreateDefault(UUID tenantId, String financialYear);
}
