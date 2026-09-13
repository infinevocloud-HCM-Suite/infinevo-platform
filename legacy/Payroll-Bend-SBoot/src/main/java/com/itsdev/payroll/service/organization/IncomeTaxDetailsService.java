package com.itsdev.payroll.service.organization;

import com.itsdev.payroll.dto.organization.IncomeTaxDetailsDTO;

public interface IncomeTaxDetailsService {
    IncomeTaxDetailsDTO update(String organizationId, IncomeTaxDetailsDTO dto);
    IncomeTaxDetailsDTO get(String organizationId);
}