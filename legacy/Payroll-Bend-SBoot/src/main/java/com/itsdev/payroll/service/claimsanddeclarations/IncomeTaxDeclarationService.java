package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;

public interface IncomeTaxDeclarationService {
    IncomeTaxDeclarationDTO getIncomeTaxDeclaration(String organizationId);
    IncomeTaxDeclarationDTO updateIncomeTaxDeclaration(String organizationId, IncomeTaxDeclarationDTO dto);
}
