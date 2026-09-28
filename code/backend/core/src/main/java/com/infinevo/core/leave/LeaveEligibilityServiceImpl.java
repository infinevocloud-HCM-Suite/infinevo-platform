package com.infinevo.core.leave;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link LeaveEligibilityService} (W-16.1, spec section 4 & 7).
 */
@Service
@Transactional(readOnly = true)
public class LeaveEligibilityServiceImpl implements LeaveEligibilityService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeavePolicyEligibilityRepository eligibilityRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveEligibilityServiceImpl(
            LeaveTypeRepository leaveTypeRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeavePolicyEligibilityRepository eligibilityRepository,
            EmployeeRepository employeeRepository) {
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
        this.eligibilityRepository =
                Objects.requireNonNull(eligibilityRepository, "eligibilityRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    public boolean isEligible(Employee employee, LeaveType leaveType, LocalDate asOf) {
        if (employee == null || leaveType == null) {
            return false;
        }
        if (!leaveType.isActive()) {
            return false;
        }

        LocalDate evaluationDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        if (leaveType.getValidFrom().isAfter(evaluationDate)) {
            return false;
        }
        if (leaveType.getValidTo() != null && leaveType.getValidTo().isBefore(evaluationDate)) {
            return false;
        }

        Optional<LeavePolicy> policyOpt =
                leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                employee.getTenantId(), leaveType.getId(), evaluationDate);

        if (policyOpt.isEmpty()) {
            return false;
        }
        LeavePolicy policy = policyOpt.get();

        // 1. Gender check: if policy specifies gender, employee must match
        if (policy.getGender() != null && !policy.getGender().isBlank()) {
            if (employee.getGender() == null || !policy.getGender().equalsIgnoreCase(employee.getGender())) {
                return false;
            }
        }

        // 2. Dimension checks from leave_policy_eligibility
        List<LeavePolicyEligibility> eligibilities =
                eligibilityRepository.findByTenantIdAndPolicyId(employee.getTenantId(), policy.getId());

        // "no eligibility rows means everyone is eligible"
        if (eligibilities.isEmpty()) {
            return true;
        }

        Map<String, Set<UUID>> allowedByDimension = eligibilities.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDimension().toLowerCase(),
                        Collectors.mapping(LeavePolicyEligibility::getValueId, Collectors.toSet())));

        // Each dimension filters; combined dimensions are AND, not OR
        if (allowedByDimension.containsKey("department")) {
            UUID deptId =
                    employee.getDepartment() != null ? employee.getDepartment().getId() : null;
            if (deptId == null || !allowedByDimension.get("department").contains(deptId)) {
                return false;
            }
        }

        if (allowedByDimension.containsKey("designation")) {
            UUID desigId = employee.getDesignation() != null
                    ? employee.getDesignation().getId()
                    : null;
            if (desigId == null || !allowedByDimension.get("designation").contains(desigId)) {
                return false;
            }
        }

        if (allowedByDimension.containsKey("work_location")) {
            UUID locId = employee.getWorkLocation() != null
                    ? employee.getWorkLocation().getId()
                    : null;
            if (locId == null || !allowedByDimension.get("work_location").contains(locId)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isEligible(UUID tenantId, UUID employeeId, UUID leaveTypeId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");

        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElse(null);
        if (employee == null) {
            return false;
        }

        LeaveType leaveType =
                leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId).orElse(null);
        if (leaveType == null) {
            return false;
        }

        return isEligible(employee, leaveType, asOf);
    }

    @Override
    public List<LeaveTypeResponse> getEligibleLeaveTypes(UUID tenantId, UUID employeeId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");

        LocalDate evaluationDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with ID: " + employeeId));

        List<LeaveType> activeTypes = leaveTypeRepository.findByTenantIdAndIsActiveTrueOrderByCodeAsc(tenantId).stream()
                .filter(t -> !t.getValidFrom().isAfter(evaluationDate)
                        && (t.getValidTo() == null || !t.getValidTo().isBefore(evaluationDate)))
                .toList();

        List<LeaveTypeResponse> result = new ArrayList<>();
        for (LeaveType type : activeTypes) {
            if (isEligible(employee, type, evaluationDate)) {
                Optional<LeavePolicy> policyOpt = leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, type.getId(), evaluationDate);
                LeavePolicyResponse policyResponse = null;
                if (policyOpt.isPresent()) {
                    LeavePolicy policy = policyOpt.get();
                    List<LeavePolicyEligibility> el =
                            eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policy.getId());
                    policyResponse = LeavePolicyResponse.from(policy, el);
                }
                result.add(LeaveTypeResponse.from(type, policyResponse));
            }
        }

        return result;
    }
}
