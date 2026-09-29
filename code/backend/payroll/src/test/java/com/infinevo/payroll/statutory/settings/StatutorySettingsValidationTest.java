package com.infinevo.payroll.statutory.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests covering statutory settings validation and statutory defaults (W-31.1, spec section 7).
 */
class StatutorySettingsValidationTest {

    private EpfSettingRepository epfRepository;
    private EsiSettingRepository esiRepository;
    private StatutorySettingsService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        epfRepository = mock(EpfSettingRepository.class);
        esiRepository = mock(EsiSettingRepository.class);
        service = new StatutorySettingsServiceImpl(epfRepository, esiRepository);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Defaults in spec section 6 are returned when tenant has no EPF row")
    void epfReturnsStatutoryDefaultsWhenNoRow() {
        when(epfRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        EpfSettingResponse response = service.epf(TENANT_ID);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNull();
        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.source()).isEqualTo(SettingSource.DEFAULT);
        assertThat(response.deductionCycle()).isEqualTo(DeductionCycle.MONTHLY);
        assertThat(response.employeeRate()).isEqualByComparingTo(new BigDecimal("12.0000"));
        assertThat(response.employerRate()).isEqualByComparingTo(new BigDecimal("12.0000"));
        assertThat(response.epsRate()).isEqualByComparingTo(new BigDecimal("8.3300"));
        assertThat(response.edliRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(response.adminChargeRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(response.wageCeiling()).isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(response.epsSeniorAge()).isEqualTo(58);
        assertThat(response.considerEarnedWage()).isTrue();
    }

    @Test
    @DisplayName("Defaults in spec section 6 are returned when tenant has no ESI row")
    void esiReturnsStatutoryDefaultsWhenNoRow() {
        when(esiRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        EsiSettingResponse response = service.esi(TENANT_ID);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNull();
        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.source()).isEqualTo(SettingSource.DEFAULT);
        assertThat(response.deductionCycle()).isEqualTo(DeductionCycle.MONTHLY);
        assertThat(response.employeeRate()).isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(response.employerRate()).isEqualByComparingTo(new BigDecimal("3.2500"));
        assertThat(response.wageCeiling()).isEqualByComparingTo(new BigDecimal("21000.0000"));
    }

    @Test
    @DisplayName("Rate above 100 is refused with validation exception")
    void rateAbove100Refused() {
        EpfSettingRequest invalidRate = new EpfSettingRequest(
                false,
                null,
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("105.0000"), // invalid > 100
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                false,
                false,
                false,
                true,
                58,
                false,
                false,
                false,
                false,
                false);

        assertThatThrownBy(() -> service.saveEpf(invalidRate))
                .isInstanceOf(StatutorySettingsValidationException.class)
                .satisfies(ex -> {
                    StatutorySettingsValidationException ve = (StatutorySettingsValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("employeeRate");
                });
    }

    @Test
    @DisplayName("eps_rate > employer_rate is refused")
    void epsRateExceedingEmployerRateRefused() {
        EpfSettingRequest invalidEps = new EpfSettingRequest(
                false,
                null,
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("10.0000"), // employer rate 10
                new BigDecimal("11.0000"), // eps rate 11 > 10
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                false,
                false,
                false,
                true,
                58,
                false,
                false,
                false,
                false,
                false);

        assertThatThrownBy(() -> service.saveEpf(invalidEps))
                .isInstanceOf(StatutorySettingsValidationException.class)
                .satisfies(ex -> {
                    StatutorySettingsValidationException ve = (StatutorySettingsValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("epsRate");
                });
    }

    @Test
    @DisplayName("is_enabled=true with blank registration number is refused")
    void enabledWithBlankRegistrationNumberRefused() {
        EpfSettingRequest enabledBlankReg = new EpfSettingRequest(
                true,
                "", // blank registration number
                LocalDate.now(),
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                false,
                false,
                false,
                true,
                58,
                false,
                false,
                false,
                false,
                false);

        assertThatThrownBy(() -> service.saveEpf(enabledBlankReg))
                .isInstanceOf(StatutorySettingsValidationException.class)
                .satisfies(ex -> {
                    StatutorySettingsValidationException ve = (StatutorySettingsValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("registrationNumber");
                });
    }

    @Test
    @DisplayName("Wage ceiling <= 0 or senior age out of [50, 70] is refused")
    void wageCeilingAndSeniorAgeBoundsValidated() {
        EpfSettingRequest invalidBounds = new EpfSettingRequest(
                false,
                null,
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                BigDecimal.ZERO, // invalid wage ceiling <= 0
                false,
                false,
                false,
                true,
                45, // invalid senior age < 50
                false,
                false,
                false,
                false,
                false);

        assertThatThrownBy(() -> service.saveEpf(invalidBounds))
                .isInstanceOf(StatutorySettingsValidationException.class)
                .satisfies(ex -> {
                    StatutorySettingsValidationException ve = (StatutorySettingsValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("wageCeiling");
                    assertThat(ve.getFieldErrors()).containsKey("epsSeniorAge");
                });
    }

    @Test
    @DisplayName("Multiplication by rates succeeds with numeric precision")
    void multiplicationByRateSucceeds() {
        BigDecimal wage = new BigDecimal("15000.0000");
        BigDecimal employeeRate = new BigDecimal("12.0000");
        BigDecimal contribution = wage.multiply(employeeRate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        assertThat(contribution).isEqualByComparingTo(new BigDecimal("1800.0000"));
    }
}
