package com.infinevo.core.lop;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing loss-of-pay and working-day policies (W-18.1).
 *
 * <p>Enforces effective-date versioning so past payslips remain explainable.
 */
@Service
public class LopPolicyService {

    private final LopPolicyRepository policyRepository;

    public LopPolicyService(LopPolicyRepository policyRepository) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository must not be null");
    }

    /**
     * Resolves the policy in force for the current tenant as of the given date.
     * Throws {@link NoLopPolicyException} when no policy has effective_from &lt;= asOf.
     */
    @Transactional(readOnly = true)
    public LopPolicyResponse getPolicyInForce(LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        LocalDate targetDate = asOf != null ? asOf : LocalDate.now();
        LopPolicy policy = policyRepository
                .findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(tenantId, targetDate)
                .orElseThrow(() -> new NoLopPolicyException(
                        "No loss-of-pay policy in force for tenant " + tenantId + " as of " + targetDate));
        return toResponse(policy);
    }

    /**
     * Finds the entity in force for a given tenant and date (used by calculators and query seams).
     */
    @Transactional(readOnly = true)
    public Optional<LopPolicy> findPolicyInForceEntity(UUID tenantId, LocalDate asOf) {
        if (tenantId == null || asOf == null) {
            return Optional.empty();
        }
        return policyRepository.findFirstByTenantIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                tenantId, asOf);
    }

    /**
     * Creates or updates a policy version for the current tenant on {@code effectiveFrom}.
     */
    @Transactional
    public LopPolicyResponse savePolicy(LopPolicyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);

        UUID tenantId = TenantContext.require();
        boolean weekendsPayable = request.weekendsPayable() == null || request.weekendsPayable();
        boolean holidaysPayable = request.holidaysPayable() == null || request.holidaysPayable();
        LopRounding rounding = request.lopRounding() != null ? request.lopRounding() : LopRounding.HALF_UP_2;

        LopPolicy policy = policyRepository
                .findByTenantIdAndEffectiveFrom(tenantId, request.effectiveFrom())
                .orElseGet(() -> new LopPolicy(
                        tenantId,
                        request.workingDayBasis(),
                        request.configuredDaysPerMonth(),
                        weekendsPayable,
                        holidaysPayable,
                        rounding,
                        request.effectiveFrom()));

        policy.setWorkingDayBasis(request.workingDayBasis());
        policy.setConfiguredDaysPerMonth(request.configuredDaysPerMonth());
        policy.setWeekendsPayable(weekendsPayable);
        policy.setHolidaysPayable(holidaysPayable);
        policy.setLopRounding(rounding);
        policy.setEffectiveFrom(request.effectiveFrom());

        LopPolicy saved = policyRepository.save(policy);
        return toResponse(saved);
    }

    private void validateRequest(LopPolicyRequest req) {
        if (req.workingDayBasis() == null) {
            throw new IllegalArgumentException("workingDayBasis is required and must not be null");
        }
        if (req.effectiveFrom() == null) {
            throw new IllegalArgumentException("effectiveFrom is required and must not be null");
        }
        if (req.workingDayBasis() != WorkingDayBasis.ORG_DAYS && req.configuredDaysPerMonth() != null) {
            throw new IllegalArgumentException(
                    "configuredDaysPerMonth must be null when workingDayBasis is not ORG_DAYS");
        }
        if (req.workingDayBasis() == WorkingDayBasis.ORG_DAYS && req.configuredDaysPerMonth() != null) {
            if (req.configuredDaysPerMonth().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("configuredDaysPerMonth must be positive");
            }
        }
    }

    public LopPolicyResponse toResponse(LopPolicy policy) {
        return new LopPolicyResponse(
                policy.getId(),
                policy.getTenantId(),
                policy.getWorkingDayBasis(),
                policy.getConfiguredDaysPerMonth(),
                policy.isWeekendsPayable(),
                policy.isHolidaysPayable(),
                policy.getLopRounding(),
                policy.getEffectiveFrom());
    }
}
