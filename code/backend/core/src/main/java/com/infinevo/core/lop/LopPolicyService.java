package com.infinevo.core.lop;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        LocalDate effectiveFrom = request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now();
        boolean weekendsPayable = request.weekendsPayable() == null || request.weekendsPayable();
        boolean holidaysPayable = request.holidaysPayable() == null || request.holidaysPayable();
        LopRounding rounding = request.lopRounding() != null ? request.lopRounding() : LopRounding.HALF_UP_2;

        Optional<LopPolicy> existingPolicyOpt =
                policyRepository.findByTenantIdAndEffectiveFrom(tenantId, effectiveFrom);
        if (existingPolicyOpt.isPresent()) {
            LopPolicy existing = existingPolicyOpt.get();
            if (isSamePolicy(
                    existing,
                    request.workingDayBasis(),
                    request.configuredDaysPerMonth(),
                    weekendsPayable,
                    holidaysPayable,
                    rounding)) {
                return toResponse(existing);
            }
            throw new IllegalStateException(
                    "Cannot edit loss-of-pay policy in place for effective date "
                            + effectiveFrom
                            + ". Policy versions are immutable to ensure past payslips remain explainable. Provide a new effectiveFrom date to create a new version.");
        }

        LopPolicy newPolicy = new LopPolicy(
                tenantId,
                request.workingDayBasis(),
                request.configuredDaysPerMonth(),
                weekendsPayable,
                holidaysPayable,
                rounding,
                effectiveFrom);

        // saveAndFlush so the unique key uk_lop_policy_tenant_effective_from (V119) is checked here,
        // where it can be answered as a conflict, not at commit. It catches the race the
        // existence check above cannot: two saves for the same date at once.
        try {
            LopPolicy saved = policyRepository.saveAndFlush(newPolicy);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "A loss-of-pay policy version already exists for effective date "
                            + effectiveFrom
                            + ". Policy versions are immutable; provide a new effectiveFrom date.",
                    e);
        }
    }

    private boolean isSamePolicy(
            LopPolicy policy,
            WorkingDayBasis basis,
            BigDecimal configuredDays,
            boolean weekendsPayable,
            boolean holidaysPayable,
            LopRounding rounding) {
        if (policy.getWorkingDayBasis() != basis) {
            return false;
        }
        if (policy.isWeekendsPayable() != weekendsPayable) {
            return false;
        }
        if (policy.isHolidaysPayable() != holidaysPayable) {
            return false;
        }
        if (policy.getLopRounding() != rounding) {
            return false;
        }
        if (policy.getConfiguredDaysPerMonth() == null && configuredDays == null) {
            return true;
        }
        if (policy.getConfiguredDaysPerMonth() != null && configuredDays != null) {
            return policy.getConfiguredDaysPerMonth().compareTo(configuredDays) == 0;
        }
        return false;
    }

    private void validateRequest(LopPolicyRequest req) {
        if (req.workingDayBasis() == null) {
            throw new IllegalArgumentException("workingDayBasis is required and must not be null");
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
