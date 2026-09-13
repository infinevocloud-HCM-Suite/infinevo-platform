package com.itsdev.payroll.service.payruns;

import com.itsdev.payroll.dto.payruns.PayRunDTO;
import com.itsdev.payroll.dto.payruns.PersistedPayRunDTO;
import com.itsdev.payroll.entity.organization.Organization;

import java.util.List;
import java.util.Map;

public interface PayRunService {


    PayRunDTO getPayRunById(String organizationId, String payrunId);

    PayRunDTO createPayRun(String organizationId, PayRunDTO dto);

    PayRunDTO updatePayRun(String organizationId, String payrunId, PayRunDTO dto);

    Map<String, Object> getAllPayRunsStructuredResponse(String organizationId);

    List<PersistedPayRunDTO> getAllCompletedPayRuns(String organizationId);

    PayRunDTO approvePayRun(String organizationId, String payrunId, boolean canPostPayrunTransactions);

    PayRunDTO rejectPayRun(String organizationId, String payrunId, String rejectedReason);

    void completePayRunPayment(String organizationId, String payrunId, List<String> employeeIds);
    
    void deletePayRun(String organizationId, String payrunId);

    String getCurrentPayrunForItDeclaration(String organizationId);

}
