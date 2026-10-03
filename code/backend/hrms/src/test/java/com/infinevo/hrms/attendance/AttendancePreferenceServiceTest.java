package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AttendancePreferenceServiceImpl} (W-40.1, spec section 4 & 7).
 */
class AttendancePreferenceServiceTest {

    private AttendancePreferenceRepository repository;
    private AttendancePreferenceServiceImpl service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        repository = mock(AttendancePreferenceRepository.class);
        service = new AttendancePreferenceServiceImpl(repository);
        tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("current() returns system defaults with isDefault = true when no row exists")
    void currentReturnsDefaultsWhenEmpty() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        AttendancePreferenceResponse response = service.current();

        assertThat(response.isDefault()).isTrue();
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.hoursCalculation()).isEqualTo(HoursCalculation.EVERY_SESSION);
        assertThat(response.fullDayMinimumHours()).isEqualByComparingTo(BigDecimal.valueOf(9.00));
        assertThat(response.halfDayMinimumHours()).isEqualByComparingTo(BigDecimal.valueOf(4.50));
        assertThat(response.regularizationWindowDays()).isNull();
        assertThat(response.maxRegularizationsPerMonth()).isNull();
        assertThat(response.allowRegularizationWithoutSession()).isTrue();
        assertThat(response.id()).isNull();
    }

    @Test
    @DisplayName("current() returns persisted row with isDefault = false when entity exists")
    void currentReturnsPersistedRow() {
        AttendancePreference pref = new AttendancePreference(
                tenantId,
                HoursCalculation.FIRST_IN_LAST_OUT,
                BigDecimal.valueOf(8.00).setScale(2),
                BigDecimal.valueOf(4.00).setScale(2),
                7,
                5,
                false);
        UUID prefId = UUID.randomUUID();
        pref.setId(prefId);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(pref));

        AttendancePreferenceResponse response = service.current();

        assertThat(response.isDefault()).isFalse();
        assertThat(response.id()).isEqualTo(prefId);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.hoursCalculation()).isEqualTo(HoursCalculation.FIRST_IN_LAST_OUT);
        assertThat(response.fullDayMinimumHours()).isEqualByComparingTo(BigDecimal.valueOf(8.00));
        assertThat(response.halfDayMinimumHours()).isEqualByComparingTo(BigDecimal.valueOf(4.00));
        assertThat(response.regularizationWindowDays()).isEqualTo(7);
        assertThat(response.maxRegularizationsPerMonth()).isEqualTo(5);
        assertThat(response.allowRegularizationWithoutSession()).isFalse();
    }

    @Test
    @DisplayName("save() creates new entity when tenant has no prior row")
    void saveCreatesNewEntity() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(AttendancePreference.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.FIRST_IN_LAST_OUT, BigDecimal.valueOf(8.5), BigDecimal.valueOf(4.25), 14, 3, true);

        AttendancePreferenceResponse response = service.save(request);

        assertThat(response.isDefault()).isFalse();
        assertThat(response.hoursCalculation()).isEqualTo(HoursCalculation.FIRST_IN_LAST_OUT);
        assertThat(response.fullDayMinimumHours()).isEqualTo(new BigDecimal("8.50"));
        assertThat(response.halfDayMinimumHours()).isEqualTo(new BigDecimal("4.25"));
        assertThat(response.regularizationWindowDays()).isEqualTo(14);
        assertThat(response.maxRegularizationsPerMonth()).isEqualTo(3);
        assertThat(response.allowRegularizationWithoutSession()).isTrue();

        ArgumentCaptor<AttendancePreference> captor = ArgumentCaptor.forClass(AttendancePreference.class);
        verify(repository).saveAndFlush(captor.capture());
        AttendancePreference saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getHoursCalculation()).isEqualTo(HoursCalculation.FIRST_IN_LAST_OUT);
        assertThat(saved.getFullDayMinimumHours()).isEqualTo(new BigDecimal("8.50"));
        assertThat(saved.getHalfDayMinimumHours()).isEqualTo(new BigDecimal("4.25"));
    }

    @Test
    @DisplayName("save() updates existing entity when tenant already has a row")
    void saveUpdatesExistingEntity() {
        AttendancePreference existing = new AttendancePreference(
                tenantId,
                HoursCalculation.EVERY_SESSION,
                BigDecimal.valueOf(9.00),
                BigDecimal.valueOf(4.50),
                null,
                null,
                true);
        UUID existingId = UUID.randomUUID();
        existing.setId(existingId);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(AttendancePreference.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.FIRST_IN_LAST_OUT, BigDecimal.valueOf(8.00), BigDecimal.valueOf(4.00), 30, 10, false);

        AttendancePreferenceResponse response = service.save(request);

        assertThat(response.isDefault()).isFalse();
        assertThat(response.id()).isEqualTo(existingId);
        assertThat(response.hoursCalculation()).isEqualTo(HoursCalculation.FIRST_IN_LAST_OUT);
        assertThat(response.fullDayMinimumHours()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(response.halfDayMinimumHours()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(response.regularizationWindowDays()).isEqualTo(30);
        assertThat(response.maxRegularizationsPerMonth()).isEqualTo(10);
        assertThat(response.allowRegularizationWithoutSession()).isFalse();
    }

    @Test
    @DisplayName("Validation: request body must not be null")
    void validationNullRequest() {
        assertThatThrownBy(() -> service.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request body must not be null");
    }

    @Test
    @DisplayName("Validation: hoursCalculation must not be null")
    void validationNullHoursCalculation() {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                null, BigDecimal.valueOf(9.00), BigDecimal.valueOf(4.50), null, null, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hoursCalculation must not be null");
    }

    @Test
    @DisplayName("Validation: fullDayMinimumHours must not be null")
    void validationNullFullDayHours() {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, null, BigDecimal.valueOf(4.50), null, null, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullDayMinimumHours must not be null");
    }

    @Test
    @DisplayName("Validation: halfDayMinimumHours must not be null")
    void validationNullHalfDayHours() {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), null, null, null, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("halfDayMinimumHours must not be null");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, 24.01, 30.0})
    @DisplayName("Validation: fullDayMinimumHours <= 0 or > 24 is rejected")
    void validationFullDayHoursRange(double hours) {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(hours), BigDecimal.valueOf(2.0), null, null, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullDayMinimumHours must be greater than 0 and at most 24");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5, 24.5})
    @DisplayName("Validation: halfDayMinimumHours <= 0 or > 24 is rejected")
    void validationHalfDayHoursRange(double hours) {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), BigDecimal.valueOf(hours), null, null, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("halfDayMinimumHours must be greater than 0 and at most 24");
    }

    @Test
    @DisplayName("Validation: halfDayMinimumHours >= fullDayMinimumHours is rejected")
    void validationHalfDayGreaterOrEqualToFullDay() {
        AttendancePreferenceRequest equalHours = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(6.00), BigDecimal.valueOf(6.00), null, null, true);

        assertThatThrownBy(() -> service.save(equalHours))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("halfDayMinimumHours must be strictly less than fullDayMinimumHours");

        AttendancePreferenceRequest halfGreaterThanFull = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(6.00), BigDecimal.valueOf(7.00), null, null, true);

        assertThatThrownBy(() -> service.save(halfGreaterThanFull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("halfDayMinimumHours must be strictly less than fullDayMinimumHours");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 367, 500})
    @DisplayName("Validation: regularizationWindowDays < 0 or > 366 is rejected")
    void validationRegularizationWindowDays(int days) {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), BigDecimal.valueOf(4.50), days, null, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regularizationWindowDays must be between 0 and 366 if specified");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 32, 100})
    @DisplayName("Validation: maxRegularizationsPerMonth < 1 or > 31 is rejected")
    void validationMaxRegularizationsPerMonth(int max) {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), BigDecimal.valueOf(4.50), null, max, true);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRegularizationsPerMonth must be between 1 and 31 if specified");
    }

    @Test
    @DisplayName("save() defaults allowRegularizationWithoutSession to true when null in request")
    void saveDefaultsAllowWithoutSessionToTrue() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(AttendancePreference.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), BigDecimal.valueOf(4.50), null, null, null);

        AttendancePreferenceResponse response = service.save(request);
        assertThat(response.allowRegularizationWithoutSession()).isTrue();
    }
}
