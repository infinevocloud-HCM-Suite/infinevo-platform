package com.infinevo.payroll.component;

import com.infinevo.shared.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BenefitService} (W-26.1).
 */
@Service
@Transactional
public class BenefitServiceImpl implements BenefitService {

    private static final int MAX_ACTOR_LEN = 100;
    private final BenefitRepository benefitRepository;

    public BenefitServiceImpl(BenefitRepository benefitRepository) {
        this.benefitRepository = Objects.requireNonNull(benefitRepository, "benefitRepository must not be null");
    }

    @Override
    public BenefitResponse create(BenefitRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();

        boolean isCodeDuplicate = request.code() != null
                && benefitRepository.existsByTenantIdAndCode(
                        tenantId, request.code().trim());

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        SalaryComponentValidator.validateCommon(
                request.code(),
                request.name(),
                request.displayName(),
                request.calculationType(),
                request.defaultValue(),
                request.percentageOf(),
                request.maxLimit(),
                isCodeDuplicate,
                fieldErrors);

        validateBenefitFields(request, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        Benefit benefit = new Benefit(tenantId, currentActor());
        populateFields(benefit, request);
        Benefit saved = benefitRepository.save(benefit);
        return BenefitResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BenefitResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Benefit benefit = benefitRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("benefit", id));
        return BenefitResponse.from(benefit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BenefitResponse> list(boolean activeOnly) {
        UUID tenantId = TenantContext.require();
        List<Benefit> list = activeOnly
                ? benefitRepository.findAllByTenantIdAndActiveAndDeletedFalse(tenantId, true)
                : benefitRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        return list.stream().map(BenefitResponse::from).toList();
    }

    @Override
    public BenefitResponse update(UUID id, BenefitRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();
        Benefit benefit = benefitRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("benefit", id));

        boolean isCodeDuplicate = request.code() != null
                && benefitRepository.existsByTenantIdAndCodeAndIdNot(
                        tenantId, request.code().trim(), id);

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        SalaryComponentValidator.validateCommon(
                request.code(),
                request.name(),
                request.displayName(),
                request.calculationType(),
                request.defaultValue(),
                request.percentageOf(),
                request.maxLimit(),
                isCodeDuplicate,
                fieldErrors);

        validateBenefitFields(request, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        populateFields(benefit, request);
        benefit.setUpdatedBy(currentActor());
        Benefit saved = benefitRepository.save(benefit);
        return BenefitResponse.from(saved);
    }

    @Override
    public BenefitResponse updateActive(UUID id, boolean active) {
        UUID tenantId = TenantContext.require();
        Benefit benefit = benefitRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("benefit", id));

        benefit.setActive(active);
        benefit.setUpdatedBy(currentActor());
        Benefit saved = benefitRepository.save(benefit);
        return BenefitResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Benefit benefit = benefitRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("benefit", id));

        benefit.setDeleted(true);
        benefit.setUpdatedBy(currentActor());
        benefitRepository.save(benefit);
    }

    private void validateBenefitFields(BenefitRequest request, Map<String, String> fieldErrors) {
        if (request.benefitPlan() != null && request.benefitPlan().length() > 64) {
            fieldErrors.put("benefitPlan", "Benefit plan must not exceed 64 characters");
        }
        if (request.benefitCategory() != null && request.benefitCategory().length() > 32) {
            fieldErrors.put("benefitCategory", "Benefit category must not exceed 32 characters");
        }
        if (request.taxExemptSection() != null && request.taxExemptSection().length() > 16) {
            fieldErrors.put("taxExemptSection", "Tax exempt section must not exceed 16 characters");
        }
        if (request.taxExemptionSubType() != null
                && request.taxExemptionSubType().length() > 32) {
            fieldErrors.put("taxExemptionSubType", "Tax exemption sub type must not exceed 32 characters");
        }
    }

    private void populateFields(Benefit benefit, BenefitRequest request) {
        benefit.setCode(request.code().trim());
        benefit.setName(request.name().trim());
        benefit.setDisplayName(
                request.displayName() != null ? request.displayName().trim() : null);
        benefit.setBenefitPlan(request.benefitPlan());
        benefit.setBenefitCategory(request.benefitCategory());
        benefit.setCalculationType(
                request.calculationType() != null ? request.calculationType() : CalculationType.FLAT);
        benefit.setDefaultValue(request.defaultValue());
        benefit.setPercentageOf(request.percentageOf());
        benefit.setMaxLimit(request.maxLimit());
        benefit.setPreTax(Boolean.TRUE.equals(request.preTax()));
        benefit.setOneTime(Boolean.TRUE.equals(request.oneTime()));
        benefit.setProRata(Boolean.TRUE.equals(request.proRata()));
        benefit.setSuperannuation(Boolean.TRUE.equals(request.superannuation()));
        benefit.setIncludedInCtc(Boolean.TRUE.equals(request.includedInCtc()));
        benefit.setIncludedInSalaryStructure(Boolean.TRUE.equals(request.includedInSalaryStructure()));
        benefit.setAllowsEmployerContribution(Boolean.TRUE.equals(request.allowsEmployerContribution()));
        benefit.setAllowsEmployeeContribution(Boolean.TRUE.equals(request.allowsEmployeeContribution()));
        benefit.setTaxExemptSection(request.taxExemptSection());
        benefit.setTaxExemptionSubType(request.taxExemptionSubType());
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return SalaryComponent.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR_LEN ? name.substring(0, MAX_ACTOR_LEN) : name;
    }
}
