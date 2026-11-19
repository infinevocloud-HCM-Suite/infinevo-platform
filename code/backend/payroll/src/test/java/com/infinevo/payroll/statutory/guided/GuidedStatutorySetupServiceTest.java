package com.infinevo.payroll.statutory.guided;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.statutory.pt.OrgPtOverride;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideSlab;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideSlabRepository;
import com.infinevo.payroll.statutory.pt.PtHistory;
import com.infinevo.payroll.statutory.pt.PtHistoryRepository;
import com.infinevo.payroll.statutory.pt.PtSlab;
import com.infinevo.payroll.statutory.pt.PtSlabRepository;
import com.infinevo.payroll.statutory.pt.PtState;
import com.infinevo.payroll.statutory.pt.PtStateRepository;
import com.infinevo.payroll.statutory.pt.ReferenceState;
import com.infinevo.payroll.statutory.pt.ReferenceStateRepository;
import com.infinevo.payroll.statutory.settings.DeductionCycle;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EpfSettingRequest;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSettingRequest;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import com.infinevo.payroll.statutory.settings.SettingSource;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import com.infinevo.payroll.statutory.settings.StatutorySettingsValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GuidedStatutorySetupServiceTest {

    private StatutorySettingsService statutorySettingsService;
    private EpfSettingRepository epfSettingRepository;
    private EsiSettingRepository esiSettingRepository;
    private WorkLocationService workLocationService;
    private ReferenceStateRepository referenceStateRepository;
    private PtStateRepository ptStateRepository;
    private PtSlabRepository ptSlabRepository;
    private OrgPtOverrideRepository orgPtOverrideRepository;
    private OrgPtOverrideSlabRepository orgPtOverrideSlabRepository;
    private PtHistoryRepository ptHistoryRepository;

    private GuidedStatutorySetupServiceImpl service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        statutorySettingsService = mock(StatutorySettingsService.class);
        epfSettingRepository = mock(EpfSettingRepository.class);
        esiSettingRepository = mock(EsiSettingRepository.class);
        workLocationService = mock(WorkLocationService.class);
        referenceStateRepository = mock(ReferenceStateRepository.class);
        ptStateRepository = mock(PtStateRepository.class);
        ptSlabRepository = mock(PtSlabRepository.class);
        orgPtOverrideRepository = mock(OrgPtOverrideRepository.class);
        orgPtOverrideSlabRepository = mock(OrgPtOverrideSlabRepository.class);
        ptHistoryRepository = mock(PtHistoryRepository.class);

        service = new GuidedStatutorySetupServiceImpl(
                statutorySettingsService,
                epfSettingRepository,
                esiSettingRepository,
                workLocationService,
                referenceStateRepository,
                ptStateRepository,
                ptSlabRepository,
                orgPtOverrideRepository,
                orgPtOverrideSlabRepository,
                ptHistoryRepository);

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getReadiness detects unconfigured settings, active work locations, and legal defaults")
    void getReadinessUnconfigured() {
        when(epfSettingRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(esiSettingRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        WorkLocationResponse locMh = mock(WorkLocationResponse.class);
        when(locMh.stateCode()).thenReturn("MH");
        WorkLocationResponse locDl = mock(WorkLocationResponse.class);
        when(locDl.stateCode()).thenReturn("DL");
        when(workLocationService.list(true)).thenReturn(List.of(locMh, locDl));

        ReferenceState refMh = mock(ReferenceState.class);
        when(refMh.getName()).thenReturn("Maharashtra");
        when(referenceStateRepository.findByCode("MH")).thenReturn(Optional.of(refMh));

        ReferenceState refDl = mock(ReferenceState.class);
        when(refDl.getName()).thenReturn("Delhi");
        when(referenceStateRepository.findByCode("DL")).thenReturn(Optional.of(refDl));

        PtState ptMh = mock(PtState.class);
        when(ptMh.isLeviesPt()).thenReturn(true);
        when(ptStateRepository.findByStateCode("MH")).thenReturn(Optional.of(ptMh));

        PtState ptDl = mock(PtState.class);
        when(ptDl.isLeviesPt()).thenReturn(false);
        when(ptStateRepository.findByStateCode("DL")).thenReturn(Optional.of(ptDl));

        when(orgPtOverrideRepository.findByTenantIdAndStateCode(TENANT_ID, "MH"))
                .thenReturn(Optional.empty());
        when(orgPtOverrideRepository.findByTenantIdAndStateCode(TENANT_ID, "DL"))
                .thenReturn(Optional.empty());

        GuidedStatutoryReadinessResponse readiness = service.getReadiness();

        assertThat(readiness.epfConfigured()).isFalse();
        assertThat(readiness.esiConfigured()).isFalse();
        assertThat(readiness.operatingStates()).hasSize(2);

        PtStateReadiness mhState = readiness.operatingStates().stream()
                .filter(s -> "MH".equals(s.stateCode()))
                .findFirst()
                .orElseThrow();
        assertThat(mhState.stateName()).isEqualTo("Maharashtra");
        assertThat(mhState.leviesPt()).isTrue();
        assertThat(mhState.configured()).isFalse();

        PtStateReadiness dlState = readiness.operatingStates().stream()
                .filter(s -> "DL".equals(s.stateCode()))
                .findFirst()
                .orElseThrow();
        assertThat(dlState.stateName()).isEqualTo("Delhi");
        assertThat(dlState.leviesPt()).isFalse();
        assertThat(dlState.configured()).isTrue();

        assertThat(readiness.defaults().epfEmployeeRate()).isEqualByComparingTo("12.0000");
        assertThat(readiness.defaults().epfWageCeiling()).isEqualByComparingTo("15000.0000");
        assertThat(readiness.defaults().esiWageCeiling()).isEqualByComparingTo("21000.0000");
    }

    @Test
    @DisplayName("applySetup validates registration numbers when registered is true")
    void applySetupValidatesRegistrationNumbers() {
        GuidedStatutorySetupRequest badEpf = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "", null, true, true, false, false),
                new EsiSetupRequest(false, null, null, false),
                List.of());

        assertThatThrownBy(() -> service.applySetup(badEpf))
                .isInstanceOf(StatutorySettingsValidationException.class)
                .hasMessageContaining("Registration number is required when EPF is registered");

        GuidedStatutorySetupRequest badEsi = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(false, null, null, false, false, false, false),
                new EsiSetupRequest(true, "   ", null, false),
                List.of());

        assertThatThrownBy(() -> service.applySetup(badEsi))
                .isInstanceOf(StatutorySettingsValidationException.class)
                .hasMessageContaining("Registration number is required when ESI is registered");
    }

    @Test
    @DisplayName("applySetup derives EPF and ESI defaults and saves state PT override")
    void applySetupSavesCorrectly() {
        EpfSettingResponse epfMock = new EpfSettingResponse(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                "DLCPM1234567000",
                LocalDate.of(2026, 4, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                true,
                true,
                true,
                true,
                58,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                SettingSource.PERSISTED);

        EsiSettingResponse esiMock = new EsiSettingResponse(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                "31000123450000101",
                LocalDate.of(2026, 4, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"),
                true,
                true,
                SettingSource.PERSISTED);

        when(statutorySettingsService.saveEpf(any(EpfSettingRequest.class))).thenReturn(epfMock);
        when(statutorySettingsService.saveEsi(any(EsiSettingRequest.class))).thenReturn(esiMock);

        ReferenceState refMh = mock(ReferenceState.class);
        when(refMh.getName()).thenReturn("Maharashtra");
        when(referenceStateRepository.findByCode("MH")).thenReturn(Optional.of(refMh));

        when(orgPtOverrideRepository.findByTenantIdAndStateCode(TENANT_ID, "MH"))
                .thenReturn(Optional.empty());

        OrgPtOverride savedOverride =
                new OrgPtOverride(TENANT_ID, "MH", "27123456789P", LocalDate.of(2026, 4, 1), "system");
        when(orgPtOverrideRepository.save(any(OrgPtOverride.class))).thenReturn(savedOverride);

        PtSlab slab1 = mock(PtSlab.class);
        when(slab1.getFromAmount()).thenReturn(BigDecimal.ZERO);
        when(slab1.getToAmount()).thenReturn(new BigDecimal("7500"));
        when(slab1.getAmount()).thenReturn(BigDecimal.ZERO);
        when(slab1.isFemaleExempt()).thenReturn(false);
        when(slab1.getDeductionMonths()).thenReturn(null);

        when(ptSlabRepository.inForce(eq("MH"), any(LocalDate.class))).thenReturn(List.of(slab1));

        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "DLCPM1234567000", LocalDate.of(2026, 4, 1), true, true, false, false),
                new EsiSetupRequest(true, "31000123450000101", LocalDate.of(2026, 4, 1), true),
                List.of(new PtStateSetupRequest("MH", "27123456789P", LocalDate.of(2026, 4, 1))));

        GuidedStatutorySetupResponse response = service.applySetup(request);

        assertThat(response.allStatutoryConfigured()).isTrue();
        assertThat(response.epf().isEnabled()).isTrue();
        assertThat(response.epf().registrationNumber()).isEqualTo("DLCPM1234567000");
        assertThat(response.esi().isEnabled()).isTrue();
        assertThat(response.esi().registrationNumber()).isEqualTo("31000123450000101");
        assertThat(response.ptStates()).hasSize(1);
        assertThat(response.ptStates().get(0).stateCode()).isEqualTo("MH");
        assertThat(response.ptStates().get(0).registrationNumber()).isEqualTo("27123456789P");
        assertThat(response.ptStates().get(0).source()).isEqualTo("OVERRIDE");

        ArgumentCaptor<EpfSettingRequest> epfCaptor = ArgumentCaptor.forClass(EpfSettingRequest.class);
        verify(statutorySettingsService).saveEpf(epfCaptor.capture());
        assertThat(epfCaptor.getValue().employeeRate()).isEqualByComparingTo("12.0000");
        assertThat(epfCaptor.getValue().restrictEmployeeToCeiling()).isTrue();

        ArgumentCaptor<EsiSettingRequest> esiCaptor = ArgumentCaptor.forClass(EsiSettingRequest.class);
        verify(statutorySettingsService).saveEsi(esiCaptor.capture());
        assertThat(esiCaptor.getValue().employeeRate()).isEqualByComparingTo("0.7500");
        assertThat(esiCaptor.getValue().employerRate()).isEqualByComparingTo("3.2500");

        verify(orgPtOverrideRepository).save(any(OrgPtOverride.class));
        verify(orgPtOverrideSlabRepository).save(any(OrgPtOverrideSlab.class));
        verify(ptHistoryRepository).save(any(PtHistory.class));
    }

    @Test
    @DisplayName("applySetup preserves existing company PF and ESI settings and registration dates when re-run")
    void applySetup_preservesExistingSettingsAndRegistrationDates() {
        EpfSetting existingEpf = new EpfSetting(TENANT_ID);
        existingEpf.setEnabled(true);
        existingEpf.setRegistrationNumber("DLCPM9999999000");
        existingEpf.setRegistrationDate(LocalDate.of(2020, 1, 15));
        existingEpf.setEmployeeRate(new BigDecimal("10.0000"));
        existingEpf.setEmployerRate(new BigDecimal("10.0000"));
        existingEpf.setEpsSeniorAge((short) 60);
        existingEpf.setWageCeiling(new BigDecimal("20000.0000"));
        when(epfSettingRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existingEpf));

        EsiSetting existingEsi = new EsiSetting(TENANT_ID);
        existingEsi.setEnabled(true);
        existingEsi.setRegistrationNumber("31000999990000101");
        existingEsi.setRegistrationDate(LocalDate.of(2019, 5, 20));
        existingEsi.setEmployeeRate(new BigDecimal("1.0000"));
        existingEsi.setEmployerRate(new BigDecimal("4.0000"));
        existingEsi.setWageCeiling(new BigDecimal("25000.0000"));
        when(esiSettingRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existingEsi));

        EpfSettingResponse epfMock = mock(EpfSettingResponse.class);
        when(epfMock.isEnabled()).thenReturn(true);
        when(epfMock.registrationNumber()).thenReturn("DLCPM9999999000");
        EsiSettingResponse esiMock = mock(EsiSettingResponse.class);
        when(esiMock.isEnabled()).thenReturn(true);
        when(esiMock.registrationNumber()).thenReturn("31000999990000101");

        when(statutorySettingsService.saveEpf(any(EpfSettingRequest.class))).thenReturn(epfMock);
        when(statutorySettingsService.saveEsi(any(EsiSettingRequest.class))).thenReturn(esiMock);

        // Re-run wizard with null registrationDate in request
        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "DLCPM9999999000", null, true, true, false, false),
                new EsiSetupRequest(true, "31000999990000101", null, true),
                List.of());

        service.applySetup(request);

        ArgumentCaptor<EpfSettingRequest> epfCaptor = ArgumentCaptor.forClass(EpfSettingRequest.class);
        verify(statutorySettingsService).saveEpf(epfCaptor.capture());
        // Verify custom rates and registration date were preserved
        assertThat(epfCaptor.getValue().registrationDate()).isEqualTo(LocalDate.of(2020, 1, 15));
        assertThat(epfCaptor.getValue().employeeRate()).isEqualByComparingTo("10.0000");
        assertThat(epfCaptor.getValue().epsSeniorAge()).isEqualTo(60);
        assertThat(epfCaptor.getValue().wageCeiling()).isEqualByComparingTo("20000.0000");

        ArgumentCaptor<EsiSettingRequest> esiCaptor = ArgumentCaptor.forClass(EsiSettingRequest.class);
        verify(statutorySettingsService).saveEsi(esiCaptor.capture());
        // Verify custom rates and registration date were preserved
        assertThat(esiCaptor.getValue().registrationDate()).isEqualTo(LocalDate.of(2019, 5, 20));
        assertThat(esiCaptor.getValue().employeeRate()).isEqualByComparingTo("1.0000");
        assertThat(esiCaptor.getValue().employerRate()).isEqualByComparingTo("4.0000");
        assertThat(esiCaptor.getValue().wageCeiling()).isEqualByComparingTo("25000.0000");
    }
}
