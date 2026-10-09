package com.infinevo.core.invitation;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link EmployeeInvitation}.
 */
@Repository
public interface EmployeeInvitationRepository extends JpaRepository<EmployeeInvitation, UUID> {

    List<EmployeeInvitation> findByTenantId(UUID tenantId);

    List<EmployeeInvitation> findByTenantIdAndStatus(UUID tenantId, InvitationStatus status);

    List<EmployeeInvitation> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<EmployeeInvitation> findByTenantIdAndStatusAndEmployeeId(
            UUID tenantId, InvitationStatus status, UUID employeeId);

    Optional<EmployeeInvitation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmployeeInvitation> findByTokenHash(String tokenHash);

    /**
     * The live, active employees in this tenant with a work email, no linked account and no live pending
     * invitation — the ones "Invite all without access" invites (W-73.7). Ordered by employee number.
     */
    @Query(
            """
            SELECT e.id FROM Employee e
            WHERE e.tenantId = :tenantId
              AND e.deleted = false
              AND e.status = com.infinevo.core.employee.EmploymentStatus.ACTIVE
              AND e.userAccountId IS NULL
              AND e.workEmail IS NOT NULL
              AND TRIM(e.workEmail) <> ''
              AND NOT EXISTS (
                  SELECT i.id FROM EmployeeInvitation i
                  WHERE i.tenantId = :tenantId
                    AND i.employeeId = e.id
                    AND i.status = com.infinevo.core.invitation.InvitationStatus.PENDING
                    AND i.expiresAt > :now)
            ORDER BY e.employeeNumber
            """)
    List<UUID> findEmployeeIdsWithoutAccess(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    @Query(
            "SELECT e FROM EmployeeInvitation e WHERE e.tenantId = :tenantId AND e.employeeId = :employeeId AND e.status = com.infinevo.core.invitation.InvitationStatus.PENDING AND e.expiresAt > :now")
    Optional<EmployeeInvitation> findActivePendingByEmployeeId(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("now") Instant now);

    @Query(value = "SELECT * FROM core.find_employee_invitation_by_token_hash(:tokenHash)", nativeQuery = true)
    Optional<EmployeeInvitation> findByTokenHashSecurityDefiner(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmployeeInvitation e WHERE e.id = :id AND e.tenantId = :tenantId")
    Optional<EmployeeInvitation> findByIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
