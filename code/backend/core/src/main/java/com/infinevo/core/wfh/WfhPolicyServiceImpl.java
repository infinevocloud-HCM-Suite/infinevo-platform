package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WfhPolicyServiceImpl implements WfhPolicyService {

    private final WfhPolicyRepository policyRepository;

    public WfhPolicyServiceImpl(WfhPolicyRepository policyRepository) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository must not be null");
    }

    @Override
    public WfhPolicyResponse createPolicy(UUID tenantId, CreateWfhPolicyRequest request) {
        if (policyRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new IllegalArgumentException("WFH policy code already exists: " + request.code());
        }

        WfhPolicy policy = new WfhPolicy();
        policy.setTenantId(tenantId);
        policy.setCode(request.code().trim().toUpperCase());
        policy.setName(request.name().trim());
        policy.setAnnualDays(request.annualDays());
        policy.setMonthlyLimitDays(request.monthlyLimitDays());
        policy.setAllowHalfDay(request.allowHalfDay());
        policy.setAdvanceNoticeDays(request.advanceNoticeDays());
        policy.setMaxConsecutiveDays(request.maxConsecutiveDays());
        policy.setRequiresManagerApproval(request.requiresManagerApproval());
        policy.setRequiresHrApproval(request.requiresHrApproval());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setActive(request.active());

        WfhPolicy saved = policyRepository.save(policy);
        return WfhPolicyResponse.from(saved);
    }

    @Override
    public WfhPolicyResponse updatePolicy(UUID tenantId, UUID policyId, CreateWfhPolicyRequest request) {
        WfhPolicy policy = policyRepository
                .findById(policyId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NoSuchElementException("WFH policy not found: " + policyId));

        if (!policy.getCode().equalsIgnoreCase(request.code().trim())) {
            if (policyRepository.existsByTenantIdAndCode(
                    tenantId, request.code().trim().toUpperCase())) {
                throw new IllegalArgumentException("WFH policy code already exists: " + request.code());
            }
            policy.setCode(request.code().trim().toUpperCase());
        }

        policy.setName(request.name().trim());
        policy.setAnnualDays(request.annualDays());
        policy.setMonthlyLimitDays(request.monthlyLimitDays());
        policy.setAllowHalfDay(request.allowHalfDay());
        policy.setAdvanceNoticeDays(request.advanceNoticeDays());
        policy.setMaxConsecutiveDays(request.maxConsecutiveDays());
        policy.setRequiresManagerApproval(request.requiresManagerApproval());
        policy.setRequiresHrApproval(request.requiresHrApproval());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setActive(request.active());

        WfhPolicy saved = policyRepository.save(policy);
        return WfhPolicyResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WfhPolicyResponse> getActivePolicy(UUID tenantId) {
        return policyRepository
                .findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(tenantId)
                .map(WfhPolicyResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WfhPolicyResponse> listPolicies(UUID tenantId) {
        return policyRepository.findByTenantIdOrderByEffectiveFromDesc(tenantId).stream()
                .map(WfhPolicyResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WfhPolicyResponse> getPolicyById(UUID tenantId, UUID policyId) {
        return policyRepository
                .findById(policyId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .map(WfhPolicyResponse::from);
    }

    @Override
    public WfhPolicy getOrCreateDefaultPolicy(UUID tenantId) {
        return policyRepository
                .findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(tenantId)
                .orElseGet(() -> {
                    WfhPolicy defaultPolicy = new WfhPolicy();
                    defaultPolicy.setTenantId(tenantId);
                    defaultPolicy.setCode("DEFAULT_WFH");
                    defaultPolicy.setName("Standard Remote Work Policy");
                    defaultPolicy.setAnnualDays(new BigDecimal("12.00"));
                    defaultPolicy.setMonthlyLimitDays(new BigDecimal("2.00"));
                    defaultPolicy.setAllowHalfDay(true);
                    defaultPolicy.setAdvanceNoticeDays(1);
                    defaultPolicy.setMaxConsecutiveDays(3);
                    defaultPolicy.setRequiresManagerApproval(true);
                    defaultPolicy.setRequiresHrApproval(false);
                    defaultPolicy.setEffectiveFrom(LocalDate.of(LocalDate.now().getYear(), 1, 1));
                    defaultPolicy.setActive(true);
                    return policyRepository.save(defaultPolicy);
                });
    }
}
