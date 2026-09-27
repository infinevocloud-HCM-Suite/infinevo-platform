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
 * Implementation of {@link EarningService} (W-26.1).
 */
@Service
@Transactional
public class EarningServiceImpl implements EarningService {

    private static final int MAX_ACTOR_LEN = 100;
    private final EarningRepository earningRepository;

    public EarningServiceImpl(EarningRepository earningRepository) {
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
    }

    @Override
    public EarningResponse create(EarningRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();

        boolean isCodeDuplicate = request.code() != null
                && earningRepository.existsByTenantIdAndCode(
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

        if (request.earningType() == null || request.earningType().isBlank()) {
            fieldErrors.put("earningType", "Earning type is required");
        } else if (request.earningType().length() > 32) {
            fieldErrors.put("earningType", "Earning type must not exceed 32 characters");
        }

        if (request.earningFrequency() != null && request.earningFrequency().length() > 16) {
            fieldErrors.put("earningFrequency", "Earning frequency must not exceed 16 characters");
        }
        if (request.epfInclusionType() != null && request.epfInclusionType().length() > 32) {
            fieldErrors.put("epfInclusionType", "EPF inclusion type must not exceed 32 characters");
        }

        if (request.parentEarningId() != null) {
            boolean parentExists = earningRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.parentEarningId(), tenantId)
                    .isPresent();
            if (!parentExists) {
                fieldErrors.put("parentEarningId", "Parent earning does not exist in this tenant");
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        Earning earning = new Earning(tenantId, currentActor());
        populateFields(earning, request);
        Earning saved = earningRepository.save(earning);
        return EarningResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EarningResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Earning earning = earningRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("earning", id));
        return EarningResponse.from(earning);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EarningResponse> list(boolean activeOnly) {
        UUID tenantId = TenantContext.require();
        List<Earning> list = activeOnly
                ? earningRepository.findAllByTenantIdAndActiveAndDeletedFalse(tenantId, true)
                : earningRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        return list.stream().map(EarningResponse::from).toList();
    }

    @Override
    public EarningResponse update(UUID id, EarningRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();
        Earning earning = earningRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("earning", id));

        boolean isCodeDuplicate = request.code() != null
                && earningRepository.existsByTenantIdAndCodeAndIdNot(
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

        if (request.earningType() == null || request.earningType().isBlank()) {
            fieldErrors.put("earningType", "Earning type is required");
        } else if (request.earningType().length() > 32) {
            fieldErrors.put("earningType", "Earning type must not exceed 32 characters");
        }

        if (request.earningFrequency() != null && request.earningFrequency().length() > 16) {
            fieldErrors.put("earningFrequency", "Earning frequency must not exceed 16 characters");
        }
        if (request.epfInclusionType() != null && request.epfInclusionType().length() > 32) {
            fieldErrors.put("epfInclusionType", "EPF inclusion type must not exceed 32 characters");
        }

        if (request.parentEarningId() != null) {
            if (request.parentEarningId().equals(id)) {
                fieldErrors.put("parentEarningId", "An earning cannot be its own parent");
            } else {
                boolean parentExists = earningRepository
                        .findByIdAndTenantIdAndDeletedFalse(request.parentEarningId(), tenantId)
                        .isPresent();
                if (!parentExists) {
                    fieldErrors.put("parentEarningId", "Parent earning does not exist in this tenant");
                }
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        populateFields(earning, request);
        earning.setUpdatedBy(currentActor());
        Earning saved = earningRepository.save(earning);
        return EarningResponse.from(saved);
    }

    @Override
    public EarningResponse updateActive(UUID id, boolean active) {
        UUID tenantId = TenantContext.require();
        Earning earning = earningRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("earning", id));

        earning.setActive(active);
        earning.setUpdatedBy(currentActor());
        Earning saved = earningRepository.save(earning);
        return EarningResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Earning earning = earningRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("earning", id));

        if (earningRepository.existsByTenantIdAndParentEarningIdAndDeletedFalse(tenantId, id)) {
            throw new ComponentValidationException(
                    "id", "Cannot delete earning component that is referenced as parent by another component");
        }

        earning.setDeleted(true);
        earning.setUpdatedBy(currentActor());
        earningRepository.save(earning);
    }

    private void populateFields(Earning earning, EarningRequest request) {
        earning.setCode(request.code().trim());
        earning.setName(request.name().trim());
        earning.setDisplayName(
                request.displayName() != null ? request.displayName().trim() : null);
        earning.setEarningType(request.earningType().trim());
        earning.setCalculationType(
                request.calculationType() != null ? request.calculationType() : CalculationType.FLAT);
        earning.setDefaultValue(request.defaultValue());
        earning.setPercentageOf(request.percentageOf());
        earning.setMaxLimit(request.maxLimit());
        earning.setEarningFrequency(request.earningFrequency());
        earning.setParentEarningId(request.parentEarningId());
        earning.setProRata(Boolean.TRUE.equals(request.proRata()));
        earning.setIncludedInCtc(Boolean.TRUE.equals(request.includedInCtc()));
        earning.setIncludedInSalaryStructure(Boolean.TRUE.equals(request.includedInSalaryStructure()));
        earning.setTaxable(Boolean.TRUE.equals(request.taxable()));
        earning.setVariable(Boolean.TRUE.equals(request.variable()));
        earning.setOneTime(Boolean.TRUE.equals(request.oneTime()));
        earning.setFbpComponent(Boolean.TRUE.equals(request.fbpComponent()));
        earning.setIncludedInEpf(Boolean.TRUE.equals(request.includedInEpf()));
        earning.setEpfInclusionType(request.epfInclusionType());
        earning.setIncludedInEsi(Boolean.TRUE.equals(request.includedInEsi()));
        earning.setShowInPayslip(request.showInPayslip() == null || request.showInPayslip());
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
