package com.itsdev.payroll.service;

import com.itsdev.payroll.dto.OrgSetupStepsDTO;

public interface OrgSetupStepsService {
    OrgSetupStepsDTO getOrgSetupSteps(String organizationId);
}
