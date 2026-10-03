package com.infinevo.hrms.attendance;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AttendancePreferenceService} (W-40.1).
 */
@Service
@Transactional
public class AttendancePreferenceServiceImpl implements AttendancePreferenceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(24);

    private final AttendancePreferenceRepository repository;

    public AttendancePreferenceServiceImpl(AttendancePreferenceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public AttendancePreferenceResponse current() {
        UUID tenantId = TenantContext.require();
        return repository
                .findByTenantId(tenantId)
                .map(AttendancePreferenceResponse::from)
                .orElseGet(() -> AttendancePreferenceResponse.defaults(tenantId));
    }

    @Override
    public AttendancePreferenceResponse save(AttendancePreferenceRequest request) {
        UUID tenantId = TenantContext.require();
        validate(request);

        BigDecimal fullDayHours = request.fullDayMinimumHours().setScale(2, RoundingMode.HALF_UP);
        BigDecimal halfDayHours = request.halfDayMinimumHours().setScale(2, RoundingMode.HALF_UP);
        boolean allowWithoutSession = request.allowRegularizationWithoutSession() != null
                ? request.allowRegularizationWithoutSession()
                : true;

        AttendancePreference preference = repository
                .findByTenantId(tenantId)
                .orElseGet(() -> new AttendancePreference(
                        tenantId,
                        request.hoursCalculation(),
                        fullDayHours,
                        halfDayHours,
                        request.regularizationWindowDays(),
                        request.maxRegularizationsPerMonth(),
                        allowWithoutSession));

        preference.setHoursCalculation(request.hoursCalculation());
        preference.setFullDayMinimumHours(fullDayHours);
        preference.setHalfDayMinimumHours(halfDayHours);
        preference.setRegularizationWindowDays(request.regularizationWindowDays());
        preference.setMaxRegularizationsPerMonth(request.maxRegularizationsPerMonth());
        preference.setAllowRegularizationWithoutSession(allowWithoutSession);

        AttendancePreference saved = repository.saveAndFlush(preference);
        return AttendancePreferenceResponse.from(saved);
    }

    private void validate(AttendancePreferenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (request.hoursCalculation() == null) {
            throw new IllegalArgumentException("hoursCalculation must not be null");
        }
        if (request.fullDayMinimumHours() == null) {
            throw new IllegalArgumentException("fullDayMinimumHours must not be null");
        }
        if (request.halfDayMinimumHours() == null) {
            throw new IllegalArgumentException("halfDayMinimumHours must not be null");
        }
        if (request.fullDayMinimumHours().compareTo(ZERO) <= 0
                || request.fullDayMinimumHours().compareTo(MAX_HOURS) > 0) {
            throw new IllegalArgumentException("fullDayMinimumHours must be greater than 0 and at most 24");
        }
        if (request.halfDayMinimumHours().compareTo(ZERO) <= 0
                || request.halfDayMinimumHours().compareTo(MAX_HOURS) > 0) {
            throw new IllegalArgumentException("halfDayMinimumHours must be greater than 0 and at most 24");
        }
        if (request.halfDayMinimumHours().compareTo(request.fullDayMinimumHours()) >= 0) {
            throw new IllegalArgumentException("halfDayMinimumHours must be strictly less than fullDayMinimumHours");
        }
        if (request.regularizationWindowDays() != null) {
            if (request.regularizationWindowDays() < 0 || request.regularizationWindowDays() > 366) {
                throw new IllegalArgumentException("regularizationWindowDays must be between 0 and 366 if specified");
            }
        }
        if (request.maxRegularizationsPerMonth() != null) {
            if (request.maxRegularizationsPerMonth() < 1 || request.maxRegularizationsPerMonth() > 31) {
                throw new IllegalArgumentException("maxRegularizationsPerMonth must be between 1 and 31 if specified");
            }
        }
    }
}
