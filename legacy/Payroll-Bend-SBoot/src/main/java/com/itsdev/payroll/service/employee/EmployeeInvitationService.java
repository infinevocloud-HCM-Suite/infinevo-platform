package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;

import java.util.List;
import java.util.Optional;

public interface EmployeeInvitationService {
    EmployeeInvitationDTO createInvitation(String organizationId, EmployeeInvitationDTO dto);

    EmployeeInvitationDTO updateInvitation(String organizationId, String invitationId, EmployeeInvitationDTO dto);

    List<EmployeeInvitationDTO> getInvitations(String organizationId);

    EmployeeInvitationDTO getInvitation(String organizationId, String invitationId);

    EmployeeInvitationDTO acceptInvitation(String organizationId, String invitationId);

    Optional<EmployeeInvitationDTO> findByEmployeeId(String organizationId, String employeeId);

    EmployeeInvitationDTO findByAcceptanceToken(String token);

    // void markAsAccepted(String token);

    // void markAsRejected(String token);

    EmployeeInvitationDTO findByEmailAndOrganization(String email, String organizationId);

    /**
     * Mark invitation as accepted by email and organization
     */
    void markAsAccepted(String email, String organizationId);

    /**
     * Mark invitation as rejected by email and organization with reason
     */
    void markAsRejected(String email, String organizationId, String rejectionReason);

    /**
     * Mark invitation as rejected by token with reason
     */
    void markAsRejected(String token, String rejectionReason);

    void markAsAccepted(String acceptanceToken);

}
