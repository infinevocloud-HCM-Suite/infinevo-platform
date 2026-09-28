package com.infinevo.core.approval;

import com.infinevo.core.employee.EmployeeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link DelegationService} (W-15.3).
 */
@Service
@Transactional
public class DelegationServiceImpl implements DelegationService {

    private final ApprovalDelegationRepository delegationRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public DelegationServiceImpl(
            ApprovalDelegationRepository delegationRepository, EmployeeRepository employeeRepository) {
        this.delegationRepository =
                Objects.requireNonNull(delegationRepository, "delegationRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    public DelegationResponse createDelegation(UUID tenantId, UUID callerEmployeeId, DelegationCreateRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(callerEmployeeId, "callerEmployeeId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        UUID delegateId = Objects.requireNonNull(request.delegateId(), "delegateId must not be null");
        LocalDate from = Objects.requireNonNull(request.from(), "effectiveFrom must not be null");
        LocalDate to = Objects.requireNonNull(request.to(), "effectiveTo must not be null");

        if (callerEmployeeId.equals(delegateId)) {
            throw new IllegalArgumentException("Delegator and delegate cannot be the same employee");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("effectiveFrom cannot be after effectiveTo");
        }

        // Verify delegate exists and belongs to the same tenant
        employeeRepository
                .findById(delegateId)
                .filter(e -> e.getTenantId().equals(tenantId) && !e.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Delegate employee not found: " + delegateId));

        // Refuse delegation chains (decision 1: A -> B, B cannot delegate to C; delegate cannot already be a delegator)
        List<ApprovalDelegation> overlappingForDelegate =
                delegationRepository.findActiveOverlappingForEmployee(tenantId, delegateId, from, to);
        boolean delegateIsAlreadyDelegator = overlappingForDelegate.stream()
                .anyMatch(d -> d.getDelegatorEmployeeId().equals(delegateId));
        if (delegateIsAlreadyDelegator) {
            throw new IllegalArgumentException(
                    "Delegation chains are not allowed: delegate already has active delegations to another employee");
        }

        // Refuse if delegator is currently acting as a delegate
        List<ApprovalDelegation> overlappingForCaller =
                delegationRepository.findActiveOverlappingForEmployee(tenantId, callerEmployeeId, from, to);
        boolean callerIsAlreadyDelegate = overlappingForCaller.stream()
                .anyMatch(d -> d.getDelegateEmployeeId().equals(callerEmployeeId));
        if (callerIsAlreadyDelegate) {
            throw new IllegalArgumentException(
                    "Delegation chains are not allowed: delegator is already serving as a delegate");
        }

        ApprovalDelegation delegation =
                new ApprovalDelegation(tenantId, callerEmployeeId, delegateId, request.flowTypes(), from, to);
        ApprovalDelegation saved = delegationRepository.save(delegation);
        return DelegationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DelegationResponse> getDelegations(UUID tenantId, UUID employeeId, LocalDate activeOn) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return delegationRepository.searchDelegations(tenantId, employeeId, activeOn).stream()
                .map(DelegationResponse::from)
                .toList();
    }

    @Override
    public void deleteDelegation(UUID tenantId, UUID delegationId, UUID callerEmployeeId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(delegationId, "delegationId must not be null");
        Objects.requireNonNull(callerEmployeeId, "callerEmployeeId must not be null");

        ApprovalDelegation delegation = delegationRepository
                .findByTenantIdAndId(tenantId, delegationId)
                .orElseThrow(() -> new NoSuchElementException("Delegation not found: " + delegationId));

        if (!delegation.getDelegatorEmployeeId().equals(callerEmployeeId)) {
            throw new AccessDeniedException("Cannot delete another employee's delegation");
        }

        delegation.setActive(false);
        delegationRepository.save(delegation);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> resolveDelegate(UUID tenantId, UUID delegatorId, ApprovalFlowType flowType, LocalDate asOf) {
        if (tenantId == null || delegatorId == null || asOf == null) {
            return Optional.empty();
        }
        List<ApprovalDelegation> active = delegationRepository.findActiveByDelegator(tenantId, delegatorId, asOf);
        return active.stream()
                .filter(d -> d.coversFlow(flowType))
                .map(ApprovalDelegation::getDelegateEmployeeId)
                .findFirst();
    }
}
