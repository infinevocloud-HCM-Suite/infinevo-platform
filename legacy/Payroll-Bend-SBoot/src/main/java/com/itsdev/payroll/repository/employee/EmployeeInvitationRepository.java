package com.itsdev.payroll.repository.employee;

import com.itsdev.payroll.entity.employee.EmployeeInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeInvitationRepository extends JpaRepository<EmployeeInvitation, Long> {
        List<EmployeeInvitation> findByOrganization_OrganizationId(String organizationId);

        Optional<EmployeeInvitation> findByInvitationIdAndOrganization_OrganizationId(String invitationId,
                        String organizationId);

        Optional<EmployeeInvitation> findByEmployeeIdAndOrganization_OrganizationId(String employeeId,
                        String organizationId);

        Optional<EmployeeInvitation> findByAcceptanceToken(String acceptanceToken);

        Optional<EmployeeInvitation> findByEmailAndOrganization_OrganizationId(String email, String organizationId);

        List<EmployeeInvitation> findAllByEmailAndOrganization_OrganizationId(String email, String organizationId);

        /**
         * Check if invitation exists by email and organization
         */
        boolean existsByEmailAndOrganization_OrganizationId(String email, String organizationId);

        /**
         * Find all pending invitations by organization
         */
        List<EmployeeInvitation> findByOrganization_OrganizationIdAndIsInvitationAcceptedFalse(String organizationId);

        /**
         * Find by email, organization, and not accepted
         */
        Optional<EmployeeInvitation> findByEmailAndOrganization_OrganizationIdAndIsInvitationAcceptedFalse(String email,
                        String organizationId);
}
