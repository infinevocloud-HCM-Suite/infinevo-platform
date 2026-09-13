package com.itsdev.payroll.service.employee.preview;

import com.itsdev.payroll.dto.employee.preview.OrgStatutoryConfigDTO;

public interface OrgStatutoryService {
    OrgStatutoryConfigDTO getOrgStatutoryConfig(String  organizationId);
}

