package com.itsdev.payroll.service.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.ProfessionalTaxDTO;
import com.itsdev.payroll.dto.statutorycomponents.TaxSlabUpdateRequest;
import com.itsdev.payroll.entity.organization.Organization;

import java.util.List;

public interface ProfessionalTaxService {

    ProfessionalTaxDTO createDefaultTax(Organization organization, String state);

//    ProfessionalTaxDTO getProfessionalTax(String organizationId);

    ProfessionalTaxDTO updateProfessionalTax(String organizationId, String taxId, ProfessionalTaxDTO dto);

    List<ProfessionalTaxDTO> getAllProfessionalTaxes(String organizationId);
    
     ProfessionalTaxDTO updateSlabAndEffectiveDate(
            String organizationId, String taxId, TaxSlabUpdateRequest dto);
    
    ProfessionalTaxDTO resetToDefaultSlabs(String organizationId, String taxId);
}
