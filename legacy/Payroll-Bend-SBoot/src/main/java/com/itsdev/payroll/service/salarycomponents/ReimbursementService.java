package com.itsdev.payroll.service.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.ReimbursementDTO;

import java.util.List;

public interface ReimbursementService {

    List<ReimbursementDTO> getAllReimbursements(String organizationId);

    ReimbursementDTO getReimbursement(String organizationId, String reimbursementId);

    ReimbursementDTO createReimbursement(String organizationId, ReimbursementDTO dto);

    ReimbursementDTO updateReimbursement(String organizationId, String reimbursementId, ReimbursementDTO dto);

    void deleteReimbursement(String organizationId, String reimbursementId);

    void inactivateReimbursement(String organizationId, String reimbursementId);

    void reactivateReimbursement(String organizationId, String reimbursementId);
}