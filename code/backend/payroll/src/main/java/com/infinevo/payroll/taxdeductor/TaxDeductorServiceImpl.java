package com.infinevo.payroll.taxdeductor;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaxDeductorService} (W-36.3).
 */
@Service
public class TaxDeductorServiceImpl implements TaxDeductorService {

    private static final Pattern TAN_PATTERN = Pattern.compile("^[A-Z]{4}[0-9]{5}[A-Z]$");
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern TDS_CIRCLE_PATTERN = Pattern.compile("^[A-Z]{3}/[A-Z]{2}/[0-9]{3}/[0-9]{2}$");

    private final TaxDeductorRepository repository;
    private final EmployeeService employeeService;

    public TaxDeductorServiceImpl(TaxDeductorRepository repository, EmployeeService employeeService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional
    public TaxDeductorResponse save(TaxDeductorRequest request) {
        if (request == null) {
            throw new TaxDeductorValidationException("Request body must not be null");
        }

        UUID tenantId = TenantContext.require();

        // 1. TAN validation
        if (request.tan() == null || request.tan().isBlank()) {
            throw new TaxDeductorValidationException("TAN is required");
        }
        String tan = request.tan().trim().toUpperCase(Locale.ROOT);
        if (!TAN_PATTERN.matcher(tan).matches()) {
            throw new TaxDeductorValidationException("Invalid TAN format: " + tan);
        }

        // 2. PAN validation
        if (request.pan() == null || request.pan().isBlank()) {
            throw new TaxDeductorValidationException("PAN is required");
        }
        String pan = request.pan().trim().toUpperCase(Locale.ROOT);
        if (!PAN_PATTERN.matcher(pan).matches()) {
            throw new TaxDeductorValidationException("Invalid PAN format: " + pan);
        }

        // 3. TDS Circle validation (optional)
        String tdsCircle = null;
        if (request.tdsCircle() != null && !request.tdsCircle().isBlank()) {
            tdsCircle = request.tdsCircle().trim();
            if (!TDS_CIRCLE_PATTERN.matcher(tdsCircle).matches()) {
                throw new TaxDeductorValidationException("Invalid TDS circle format: " + tdsCircle);
            }
        }

        // 4. Signatory Employee validation (optional, must belong to bound tenant)
        UUID signatoryEmployeeId = request.signatoryEmployeeId();
        if (signatoryEmployeeId != null) {
            try {
                var emp = employeeService.get(signatoryEmployeeId);
                if (emp == null) {
                    throw new TaxDeductorValidationException(
                            "Signatory employee not found in bound tenant: " + signatoryEmployeeId);
                }
            } catch (Exception e) {
                throw new TaxDeductorValidationException(
                        "Signatory employee not found in bound tenant: " + signatoryEmployeeId);
            }
        }

        // 5. Signatory Name (1-120 chars)
        if (request.signatoryName() == null || request.signatoryName().isBlank()) {
            throw new TaxDeductorValidationException("Signatory name is required");
        }
        String signatoryName = request.signatoryName().trim();
        if (signatoryName.length() > 120) {
            throw new TaxDeductorValidationException("Signatory name must be between 1 and 120 characters");
        }

        // 6. Signatory Designation (1-120 chars)
        if (request.signatoryDesignation() == null
                || request.signatoryDesignation().isBlank()) {
            throw new TaxDeductorValidationException("Signatory designation is required");
        }
        String signatoryDesignation = request.signatoryDesignation().trim();
        if (signatoryDesignation.length() > 120) {
            throw new TaxDeductorValidationException("Signatory designation must be between 1 and 120 characters");
        }

        // 7. Signatory Parent Name (0-120 chars)
        String signatoryParentName = null;
        if (request.signatoryParentName() != null) {
            signatoryParentName = request.signatoryParentName().trim();
            if (signatoryParentName.length() > 120) {
                throw new TaxDeductorValidationException("Signatory parent name must not exceed 120 characters");
            }
            if (signatoryParentName.isEmpty()) {
                signatoryParentName = null;
            }
        }

        String actor = currentActor();
        Instant now = Instant.now();

        TaxDeductor entity = repository.findByTenantId(tenantId).orElseGet(() -> {
            TaxDeductor created = new TaxDeductor(tenantId, actor);
            created.setCreatedAt(now);
            created.setCreatedBy(actor);
            return created;
        });

        entity.setTan(tan);
        entity.setPan(pan);
        entity.setTdsCircle(tdsCircle);
        entity.setSignatoryEmployeeId(signatoryEmployeeId);
        entity.setSignatoryName(signatoryName);
        entity.setSignatoryParentName(signatoryParentName);
        entity.setSignatoryDesignation(signatoryDesignation);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);

        TaxDeductor saved = repository.saveAndFlush(entity);
        return TaxDeductorResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaxDeductorResponse> current() {
        UUID tenantId = TenantContext.require();
        return repository.findByTenantId(tenantId).map(TaxDeductorResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaxDeductor> currentEntity() {
        UUID tenantId = TenantContext.require();
        return repository.findByTenantId(tenantId);
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "system";
    }
}
