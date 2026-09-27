package com.infinevo.payroll.component;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
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
 * Implementation of {@link DeductionService} (W-26.1).
 */
@Service
@Transactional
public class DeductionServiceImpl implements DeductionService {

    private static final int MAX_ACTOR_LEN = 100;
    private final DeductionRepository deductionRepository;

    public DeductionServiceImpl(DeductionRepository deductionRepository) {
        this.deductionRepository = Objects.requireNonNull(deductionRepository, "deductionRepository must not be null");
    }

    @Override
    public DeductionResponse create(DeductionRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();

        boolean isCodeDuplicate = request.code() != null
                && deductionRepository.existsByTenantIdAndCode(
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

        validateDeductionFields(request, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        Deduction deduction = new Deduction(tenantId, currentActor());
        populateFields(deduction, request);
        Deduction saved = deductionRepository.save(deduction);
        return DeductionResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeductionResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Deduction deduction = deductionRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("deduction", id));
        return DeductionResponse.from(deduction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeductionResponse> list(boolean activeOnly) {
        UUID tenantId = TenantContext.require();
        List<Deduction> list = activeOnly
                ? deductionRepository.findAllByTenantIdAndActiveAndDeletedFalse(tenantId, true)
                : deductionRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        return list.stream().map(DeductionResponse::from).toList();
    }

    @Override
    public DeductionResponse update(UUID id, DeductionRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();
        Deduction deduction = deductionRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("deduction", id));

        boolean isCodeDuplicate = request.code() != null
                && deductionRepository.existsByTenantIdAndCodeAndIdNot(
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

        validateDeductionFields(request, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        populateFields(deduction, request);
        deduction.setUpdatedBy(currentActor());
        Deduction saved = deductionRepository.save(deduction);
        return DeductionResponse.from(saved);
    }

    @Override
    public DeductionResponse updateActive(UUID id, boolean active) {
        UUID tenantId = TenantContext.require();
        Deduction deduction = deductionRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("deduction", id));

        deduction.setActive(active);
        deduction.setUpdatedBy(currentActor());
        Deduction saved = deductionRepository.save(deduction);
        return DeductionResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Deduction deduction = deductionRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("deduction", id));

        deduction.setDeleted(true);
        deduction.setUpdatedBy(currentActor());
        deductionRepository.save(deduction);
    }

    private void validateDeductionFields(DeductionRequest request, Map<String, String> fieldErrors) {
        if (request.deductionType() == null || request.deductionType().isBlank()) {
            fieldErrors.put("deductionType", "Deduction type is required");
        } else if (request.deductionType().length() > 32) {
            fieldErrors.put("deductionType", "Deduction type must not exceed 32 characters");
        }
        if (request.emiType() != null && request.emiType().length() > 32) {
            fieldErrors.put("emiType", "EMI type must not exceed 32 characters");
        }
        if (request.perquisiteInterestRate() != null
                && request.perquisiteInterestRate().compareTo(BigDecimal.ZERO) < 0) {
            fieldErrors.put("perquisiteInterestRate", "Perquisite interest rate must not be negative");
        }
        if (request.emiInterestRate() != null && request.emiInterestRate().compareTo(BigDecimal.ZERO) < 0) {
            fieldErrors.put("emiInterestRate", "EMI interest rate must not be negative");
        }
    }

    private void populateFields(Deduction deduction, DeductionRequest request) {
        deduction.setCode(request.code().trim());
        deduction.setName(request.name().trim());
        deduction.setDisplayName(
                request.displayName() != null ? request.displayName().trim() : null);
        deduction.setDeductionType(request.deductionType().trim());
        deduction.setCalculationType(
                request.calculationType() != null ? request.calculationType() : CalculationType.FLAT);
        deduction.setDefaultValue(request.defaultValue());
        deduction.setPercentageOf(request.percentageOf());
        deduction.setMaxLimit(request.maxLimit());
        deduction.setRecurring(Boolean.TRUE.equals(request.recurring()));
        deduction.setPreTax(Boolean.TRUE.equals(request.preTax()));
        deduction.setEmiType(request.emiType());
        deduction.setPerquisiteInterestRate(request.perquisiteInterestRate());
        deduction.setEmiInterestRate(request.emiInterestRate());
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
