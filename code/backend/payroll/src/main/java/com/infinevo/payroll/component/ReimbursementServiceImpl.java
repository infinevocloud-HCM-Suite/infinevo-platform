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
 * Implementation of {@link ReimbursementService} (W-26.1).
 */
@Service
@Transactional
public class ReimbursementServiceImpl implements ReimbursementService {

    private static final int MAX_ACTOR_LEN = 100;
    private final ReimbursementRepository reimbursementRepository;

    public ReimbursementServiceImpl(ReimbursementRepository reimbursementRepository) {
        this.reimbursementRepository =
                Objects.requireNonNull(reimbursementRepository, "reimbursementRepository must not be null");
    }

    @Override
    public ReimbursementResponse create(ReimbursementRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();

        boolean isCodeDuplicate = request.code() != null
                && reimbursementRepository.existsByTenantIdAndCode(
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

        validateReimbursementFields(request, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        Reimbursement reimbursement = new Reimbursement(tenantId, currentActor());
        populateFields(reimbursement, request);
        Reimbursement saved = reimbursementRepository.save(reimbursement);
        return ReimbursementResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReimbursementResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Reimbursement reimbursement = reimbursementRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("reimbursement", id));
        return ReimbursementResponse.from(reimbursement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReimbursementResponse> list(boolean activeOnly) {
        UUID tenantId = TenantContext.require();
        List<Reimbursement> list = activeOnly
                ? reimbursementRepository.findAllByTenantIdAndActiveAndDeletedFalse(tenantId, true)
                : reimbursementRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        return list.stream().map(ReimbursementResponse::from).toList();
    }

    @Override
    public ReimbursementResponse update(UUID id, ReimbursementRequest request) {
        if (request == null) {
            throw new ComponentValidationException("request", "Request body must not be null");
        }
        UUID tenantId = TenantContext.require();
        Reimbursement reimbursement = reimbursementRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("reimbursement", id));

        boolean isCodeDuplicate = request.code() != null
                && reimbursementRepository.existsByTenantIdAndCodeAndIdNot(
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

        validateReimbursementFields(request, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new ComponentValidationException(fieldErrors);
        }

        populateFields(reimbursement, request);
        reimbursement.setUpdatedBy(currentActor());
        Reimbursement saved = reimbursementRepository.save(reimbursement);
        return ReimbursementResponse.from(saved);
    }

    @Override
    public ReimbursementResponse updateActive(UUID id, boolean active) {
        UUID tenantId = TenantContext.require();
        Reimbursement reimbursement = reimbursementRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("reimbursement", id));

        reimbursement.setActive(active);
        reimbursement.setUpdatedBy(currentActor());
        Reimbursement saved = reimbursementRepository.save(reimbursement);
        return ReimbursementResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Reimbursement reimbursement = reimbursementRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ComponentNotFoundException("reimbursement", id));

        reimbursement.setDeleted(true);
        reimbursement.setUpdatedBy(currentActor());
        reimbursementRepository.save(reimbursement);
    }

    private void validateReimbursementFields(ReimbursementRequest request, Map<String, String> fieldErrors) {
        if (request.reimbursementType() == null || request.reimbursementType().isBlank()) {
            fieldErrors.put("reimbursementType", "Reimbursement type is required");
        } else if (request.reimbursementType().length() > 32) {
            fieldErrors.put("reimbursementType", "Reimbursement type must not exceed 32 characters");
        }
        if (request.carryForwardOption() != null && request.carryForwardOption().length() > 32) {
            fieldErrors.put("carryForwardOption", "Carry forward option must not exceed 32 characters");
        }
    }

    private void populateFields(Reimbursement reimbursement, ReimbursementRequest request) {
        reimbursement.setCode(request.code().trim());
        reimbursement.setName(request.name().trim());
        reimbursement.setDisplayName(
                request.displayName() != null ? request.displayName().trim() : null);
        reimbursement.setReimbursementType(request.reimbursementType().trim());
        reimbursement.setCalculationType(
                request.calculationType() != null ? request.calculationType() : CalculationType.FLAT);
        reimbursement.setDefaultValue(request.defaultValue());
        reimbursement.setPercentageOf(request.percentageOf());
        reimbursement.setMaxLimit(request.maxLimit());
        reimbursement.setCarryForwardOption(request.carryForwardOption());
        reimbursement.setIncludedInCtc(Boolean.TRUE.equals(request.includedInCtc()));
        reimbursement.setIncludedInSalaryStructure(Boolean.TRUE.equals(request.includedInSalaryStructure()));
        reimbursement.setFbpComponent(Boolean.TRUE.equals(request.fbpComponent()));
        reimbursement.setOptIn(Boolean.TRUE.equals(request.optIn()));
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
