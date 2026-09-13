package com.itsdev.payroll.mapper;

import com.itsdev.payroll.dto.OrgSetupStepsDTO;
import com.itsdev.payroll.entity.OrgSetupSteps;

public class OrgSetupStepsMapper {

    public static OrgSetupStepsDTO toDto(OrgSetupSteps entity) {
        OrgSetupStepsDTO dto = new OrgSetupStepsDTO();
        dto.setWorkLocationSetup(entity.isWorkLocationSetup());
        dto.setEmployeeSetup(entity.isEmployeeSetup());
        dto.setPayScheduleSetup(entity.isPayScheduleSetup());
        dto.setPriorPayrollSetup(entity.isPriorPayrollSetup());
        dto.setOrgTaxSetup(entity.isOrgTaxSetup());
        dto.setSalaryComponentsSetup(entity.isSalaryComponentsSetup());
        dto.setESISetup(entity.isESISetup());
        dto.setEPFSetup(entity.isEPFSetup());
        dto.setPTAXSetup(entity.isPTAXSetup());
        return dto;
    }
}
