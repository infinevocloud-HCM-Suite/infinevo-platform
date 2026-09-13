package com.itsdev.payroll.service.payRun.offCyclePayrun;

import com.itsdev.payroll.dto.payRun.offcyclepayrun.OffCyclePayRunDTO;
import com.itsdev.payroll.dto.payRun.offcyclepayrun.OffCyclePayRunImportRequestDTO;
import com.itsdev.payroll.dto.payRun.offcyclepayrun.OffCyclePayRunReleaseWithheldImportRequestDTO;

import java.util.List;

public interface OffCyclePayRunService {

    OffCyclePayRunDTO createPayRun(String organizationId, OffCyclePayRunDTO requestDto);

    OffCyclePayRunDTO updatePayRun(String organizationId, String payrollRunId, OffCyclePayRunDTO requestDto);

    OffCyclePayRunDTO getPayRun(String organizationId, String payrollRunId);

    List<OffCyclePayRunDTO> getAllPayRuns(String organizationId);

    void deletePayRun(String organizationId, String payrollRunId);

    OffCyclePayRunDTO importEmployeesToPayRun(String organizationId, String payrollRunId, OffCyclePayRunImportRequestDTO requestDto);


    OffCyclePayRunDTO importReleaseWithheldSalary(
            String organizationId,
            String payrollRunId,
            OffCyclePayRunReleaseWithheldImportRequestDTO requestDto
    );

}
