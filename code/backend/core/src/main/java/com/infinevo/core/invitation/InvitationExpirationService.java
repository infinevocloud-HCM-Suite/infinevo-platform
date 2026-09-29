package com.infinevo.core.invitation;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to persist invitation expiration in an isolated transaction (W-24.2 Issue #9).
 *
 * <p>Using {@link Propagation#REQUIRES_NEW} ensures the status transition to {@code EXPIRED}
 * commits independently of any exception subsequently thrown to the caller.
 */
@Service
public class InvitationExpirationService {

    private final UserInvitationRepository userInvitationRepository;
    private final EmployeeInvitationRepository employeeInvitationRepository;

    public InvitationExpirationService(
            UserInvitationRepository userInvitationRepository,
            EmployeeInvitationRepository employeeInvitationRepository) {
        this.userInvitationRepository = Objects.requireNonNull(userInvitationRepository, "userInvitationRepository");
        this.employeeInvitationRepository =
                Objects.requireNonNull(employeeInvitationRepository, "employeeInvitationRepository");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUserInvitationExpired(UUID tenantId, UUID invitationId) {
        TenantContext.set(tenantId);
        try {
            userInvitationRepository.findByIdAndTenantId(invitationId, tenantId).ifPresent(inv -> {
                inv.setStatus(InvitationStatus.EXPIRED);
                inv.setUpdatedAt(Instant.now());
                inv.setUpdatedBy("system");
                userInvitationRepository.save(inv);
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEmployeeInvitationExpired(UUID tenantId, UUID invitationId) {
        TenantContext.set(tenantId);
        try {
            employeeInvitationRepository
                    .findByIdAndTenantId(invitationId, tenantId)
                    .ifPresent(inv -> {
                        inv.setStatus(InvitationStatus.EXPIRED);
                        inv.setUpdatedAt(Instant.now());
                        inv.setUpdatedBy("system");
                        employeeInvitationRepository.save(inv);
                    });
        } finally {
            TenantContext.clear();
        }
    }
}
