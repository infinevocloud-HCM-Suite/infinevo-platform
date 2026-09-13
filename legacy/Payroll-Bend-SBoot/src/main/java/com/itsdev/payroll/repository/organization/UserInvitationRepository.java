package com.itsdev.payroll.repository.organization;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.UserInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {
        boolean existsByUserId(String userId);

        boolean existsByEmailAndOrganization(String email, Organization organization);

        Optional<UserInvitation> findByUserIdAndIsDeletedFalse(String userId);

        List<UserInvitation> findAllByOrganizationAndIsDeletedFalse(Organization organization);

        Optional<UserInvitation> findByAcceptanceToken(String acceptanceToken);

        /**
         * Find by email and organization ID
         */
        Optional<UserInvitation> findByEmailAndOrganization_OrganizationId(String email, String organizationId);

        /**
         * Find by email (any organization)
         */
        List<UserInvitation> findByEmail(String email);

        /**
         * Check if invitation exists by email and organization ID
         */
        boolean existsByEmailAndOrganization_OrganizationId(String email, String organizationId);

        /**
         * Find all pending invitations by organization
         */
        List<UserInvitation> findByOrganization_OrganizationIdAndIsDeletedFalseAndIsInvitationAcceptedFalse(
                        String organizationId);

        /**
         * Find by email, organization, and not deleted
         */
        Optional<UserInvitation> findByEmailAndOrganization_OrganizationIdAndIsDeletedFalse(String email,
                        String organizationId);

        Optional<UserInvitation> findByEmailAndOrganizationAndIsDeletedFalse(
                        String email,
                        Organization organization);

}
