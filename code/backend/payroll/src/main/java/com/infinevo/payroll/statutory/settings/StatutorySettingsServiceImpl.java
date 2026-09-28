package com.infinevo.payroll.statutory.settings;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for tenant-scoped statutory settings (W-31.1).
 */
@Service
@Transactional
public class StatutorySettingsServiceImpl implements StatutorySettingsService {

    private final EpfSettingRepository epfSettingRepository;
    private final EsiSettingRepository esiSettingRepository;

    public StatutorySettingsServiceImpl(
            EpfSettingRepository epfSettingRepository, EsiSettingRepository esiSettingRepository) {
        this.epfSettingRepository =
                Objects.requireNonNull(epfSettingRepository, "epfSettingRepository must not be null");
        this.esiSettingRepository =
                Objects.requireNonNull(esiSettingRepository, "esiSettingRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public EpfSettingResponse epf(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return epfSettingRepository
                .findByTenantId(tenantId)
                .map(EpfSettingResponse::from)
                .orElseGet(() -> EpfSettingResponse.defaultSettings(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public EsiSettingResponse esi(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return esiSettingRepository
                .findByTenantId(tenantId)
                .map(EsiSettingResponse::from)
                .orElseGet(() -> EsiSettingResponse.defaultSettings(tenantId));
    }

    @Override
    public EpfSettingResponse saveEpf(EpfSettingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();

        validateEpfRequest(request);

        EpfSetting entity = epfSettingRepository.findByTenantId(tenantId).orElseGet(() -> new EpfSetting(tenantId));

        boolean enabled = Boolean.TRUE.equals(request.isEnabled());
        entity.setEnabled(enabled);
        entity.setRegistrationNumber(request.registrationNumber());
        entity.setRegistrationDate(request.registrationDate());
        entity.setDeductionCycle(request.deductionCycle() != null ? request.deductionCycle() : DeductionCycle.MONTHLY);
        entity.setEmployeeRate(request.employeeRate());
        entity.setEmployerRate(request.employerRate());
        entity.setEpsRate(request.epsRate());
        entity.setEdliRate(request.edliRate());
        entity.setAdminChargeRate(request.adminChargeRate());
        entity.setWageCeiling(request.wageCeiling());
        entity.setRestrictEmployeeToCeiling(Boolean.TRUE.equals(request.restrictEmployeeToCeiling()));
        entity.setRestrictEmployerToCeiling(Boolean.TRUE.equals(request.restrictEmployerToCeiling()));
        entity.setProrateRestrictedWage(Boolean.TRUE.equals(request.prorateRestrictedWage()));
        entity.setConsiderEarnedWage(
                request.considerEarnedWage() == null || Boolean.TRUE.equals(request.considerEarnedWage()));
        entity.setEpsSeniorAge((short) (request.epsSeniorAge() != null ? request.epsSeniorAge() : 58));
        entity.setIncludeEmployerInCtc(Boolean.TRUE.equals(request.includeEmployerInCtc()));
        entity.setIncludeEdliAdminInCtc(Boolean.TRUE.equals(request.includeEdliAdminInCtc()));
        entity.setIncludeEmployerInStructure(Boolean.TRUE.equals(request.includeEmployerInStructure()));
        entity.setIncludeEdliAdminInStructure(Boolean.TRUE.equals(request.includeEdliAdminInStructure()));
        entity.setAbryScheme(Boolean.TRUE.equals(request.abryScheme()));

        EpfSetting saved = epfSettingRepository.save(entity);
        return EpfSettingResponse.from(saved);
    }

    @Override
    public EsiSettingResponse saveEsi(EsiSettingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();

        validateEsiRequest(request);

        EsiSetting entity = esiSettingRepository.findByTenantId(tenantId).orElseGet(() -> new EsiSetting(tenantId));

        boolean enabled = Boolean.TRUE.equals(request.isEnabled());
        entity.setEnabled(enabled);
        entity.setRegistrationNumber(request.registrationNumber());
        entity.setRegistrationDate(request.registrationDate());
        entity.setDeductionCycle(request.deductionCycle() != null ? request.deductionCycle() : DeductionCycle.MONTHLY);
        entity.setEmployeeRate(request.employeeRate());
        entity.setEmployerRate(request.employerRate());
        entity.setWageCeiling(request.wageCeiling());
        entity.setIncludeEmployerInCtc(Boolean.TRUE.equals(request.includeEmployerInCtc()));
        entity.setIncludeInStructure(Boolean.TRUE.equals(request.includeInStructure()));

        EsiSetting saved = esiSettingRepository.save(entity);
        return EsiSettingResponse.from(saved);
    }

    private void validateEpfRequest(EpfSettingRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        validateRate("employeeRate", request.employeeRate(), errors);
        validateRate("employerRate", request.employerRate(), errors);
        validateRate("epsRate", request.epsRate(), errors);
        validateRate("edliRate", request.edliRate(), errors);
        validateRate("adminChargeRate", request.adminChargeRate(), errors);

        if (request.epsRate() != null && request.employerRate() != null) {
            if (request.epsRate().compareTo(request.employerRate()) > 0) {
                errors.put("epsRate", "eps_rate must not exceed employer_rate");
            }
        }

        if (request.wageCeiling() == null) {
            errors.put("wageCeiling", "wage_ceiling is required");
        } else if (request.wageCeiling().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("wageCeiling", "wage_ceiling must be greater than 0");
        }

        if (request.epsSeniorAge() != null) {
            if (request.epsSeniorAge() < 50 || request.epsSeniorAge() > 70) {
                errors.put("epsSeniorAge", "eps_senior_age must be between 50 and 70");
            }
        }

        if (request.registrationNumber() != null && request.registrationNumber().length() > 32) {
            errors.put("registrationNumber", "registration_number must not exceed 32 characters");
        }

        if (Boolean.TRUE.equals(request.isEnabled())) {
            if (request.registrationNumber() == null
                    || request.registrationNumber().isBlank()) {
                errors.put("registrationNumber", "registration_number is required when is_enabled is true");
            }
        }

        if (!errors.isEmpty()) {
            throw new StatutorySettingsValidationException(errors);
        }
    }

    private void validateEsiRequest(EsiSettingRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        validateRate("employeeRate", request.employeeRate(), errors);
        validateRate("employerRate", request.employerRate(), errors);

        if (request.wageCeiling() == null) {
            errors.put("wageCeiling", "wage_ceiling is required");
        } else if (request.wageCeiling().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("wageCeiling", "wage_ceiling must be greater than 0");
        }

        if (request.registrationNumber() != null && request.registrationNumber().length() > 32) {
            errors.put("registrationNumber", "registration_number must not exceed 32 characters");
        }

        if (Boolean.TRUE.equals(request.isEnabled())) {
            if (request.registrationNumber() == null
                    || request.registrationNumber().isBlank()) {
                errors.put("registrationNumber", "registration_number is required when is_enabled is true");
            }
        }

        if (!errors.isEmpty()) {
            throw new StatutorySettingsValidationException(errors);
        }
    }

    private void validateRate(String fieldName, BigDecimal rate, Map<String, String> errors) {
        if (rate == null) {
            errors.put(fieldName, fieldName + " is required");
            return;
        }
        if (rate.stripTrailingZeros().scale() > 4) {
            errors.put(fieldName, fieldName + " scale must not exceed 4 decimal places");
            return;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            errors.put(fieldName, fieldName + " must be between 0 and 100");
        }
    }
}
