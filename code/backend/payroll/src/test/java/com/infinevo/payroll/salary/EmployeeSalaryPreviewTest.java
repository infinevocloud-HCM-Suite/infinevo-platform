package com.infinevo.payroll.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.component.BenefitRepository;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.PercentageOf;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.payroll.statutory.lines.CtcEpfComponentRepository;
import com.infinevo.payroll.statutory.lines.CtcEsiComponentRepository;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * W-75.1 §7 — preview runs the create calculation and writes nothing: an unsaved statutory profile overrides the
 * stored one, no repository saves, and PT is left out (with {@code ptEstimated=false}) without a work location.
 */
class EmployeeSalaryPreviewTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BASIC_ID = UUID.randomUUID();
    private static final UUID HRA_ID = UUID.randomUUID();
    private static final UUID SPECIAL_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);

    private CtcStructureRepository ctcStructureRepository;
    private EmployeeEarningRepository employeeEarningRepository;
    private EmployeeBenefitRepository employeeBenefitRepository;
    private EmployeeReimbursementRepository employeeReimbursementRepository;
    private EmployeeService employeeService;
    private EmployeeStatutoryProfileRepository profileRepository;
    private CtcEpfComponentRepository ctcEpfComponentRepository;
    private CtcEsiComponentRepository ctcEsiComponentRepository;
    private ApplicationEventPublisher publisher;
    private ProfessionalTaxService professionalTaxService;
    private WorkLocationService workLocationService;

    private EmployeeSalaryServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        ctcStructureRepository = mock(CtcStructureRepository.class);
        employeeEarningRepository = mock(EmployeeEarningRepository.class);
        employeeBenefitRepository = mock(EmployeeBenefitRepository.class);
        employeeReimbursementRepository = mock(EmployeeReimbursementRepository.class);
        EarningRepository earningRepository = mock(EarningRepository.class);
        employeeService = mock(EmployeeService.class);
        profileRepository = mock(EmployeeStatutoryProfileRepository.class);
        EpfSettingRepository epfSettingRepository = mock(EpfSettingRepository.class);
        EsiSettingRepository esiSettingRepository = mock(EsiSettingRepository.class);
        ctcEpfComponentRepository = mock(CtcEpfComponentRepository.class);
        ctcEsiComponentRepository = mock(CtcEsiComponentRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        professionalTaxService = mock(ProfessionalTaxService.class);
        workLocationService = mock(WorkLocationService.class);

        when(earningRepository.findByIdAndTenantIdAndDeletedFalse(BASIC_ID, TENANT))
                .thenReturn(Optional.of(earning("BASIC", "BASIC")));
        when(earningRepository.findByIdAndTenantIdAndDeletedFalse(HRA_ID, TENANT))
                .thenReturn(Optional.of(earning("HRA", "ALLOWANCE")));
        when(earningRepository.findByIdAndTenantIdAndDeletedFalse(SPECIAL_ID, TENANT))
                .thenReturn(Optional.of(earning("SPECIAL", "ALLOWANCE")));

        EpfSetting epf = new EpfSetting(TENANT);
        epf.setEnabled(true);
        epf.setRestrictEmployeeToCeiling(true);
        epf.setRestrictEmployerToCeiling(false);
        epf.setIncludeEmployerInCtc(true);
        epf.setIncludeEdliInCtc(false);
        epf.setIncludeAdminInCtc(false);
        when(epfSettingRepository.findByTenantId(TENANT)).thenReturn(Optional.of(epf));
        when(esiSettingRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        service = new EmployeeSalaryServiceImpl(
                ctcStructureRepository,
                employeeEarningRepository,
                employeeBenefitRepository,
                employeeReimbursementRepository,
                earningRepository,
                mock(BenefitRepository.class),
                mock(ReimbursementRepository.class),
                employeeService,
                null,
                null,
                epfSettingRepository,
                esiSettingRepository,
                profileRepository,
                null,
                ctcEpfComponentRepository,
                ctcEsiComponentRepository,
                publisher,
                professionalTaxService,
                workLocationService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("An unsaved statutory profile overrides the stored one")
    void unsavedProfileOverridesStored() {
        employeeWithLocation(null);
        // Stored: not PF-eligible, so Special would balance to 12,500.
        when(profileRepository.findByTenantIdAndEmployeeId(TENANT, EMPLOYEE))
                .thenReturn(Optional.of(new EmployeeStatutoryProfile(TENANT, EMPLOYEE, "system")));

        SalaryPreviewResponse stored = service.preview(EMPLOYEE, request(null));
        assertThat(stored.statutory()).isEmpty();
        assertThat(amountOf(stored, SPECIAL_ID)).isEqualByComparingTo("12500.00");

        // Unsaved: PF and EPS ticked, employer EPF in CTC with no ceiling -> Special 9,500.
        SalaryPreviewResponse unsaved = service.preview(EMPLOYEE, request(profile(true, false)));
        assertThat(amountOf(unsaved, SPECIAL_ID)).isEqualByComparingTo("9500.00");
        assertThat(unsaved.balancingComponentId()).isEqualTo(SPECIAL_ID);
        assertThat(unsaved.statutory()).extracting(s -> s.componentCode()).contains("EPF_EMPLOYEE", "EPF_EMPLOYER");
        assertThat(unsaved.employeeDeductionsMonthly()).singleElement().satisfies(d -> {
            assertThat(d.componentCode()).isEqualTo("EPF_EMPLOYEE");
            assertThat(d.monthlyAmount()).isEqualByComparingTo("1800.00"); // 12% of the 15,000 ceiling
        });
        assertThat(unsaved.grossMonthly()).isEqualByComparingTo("47000.00");
        assertThat(unsaved.takeHomeMonthlyEstimate()).isEqualByComparingTo("45200.00");
    }

    @Test
    @DisplayName("Preview calls no repository save, deletes nothing and publishes no event")
    void previewWritesNothing() {
        employeeWithLocation(null);
        when(profileRepository.findByTenantIdAndEmployeeId(TENANT, EMPLOYEE)).thenReturn(Optional.empty());

        service.preview(EMPLOYEE, request(profile(true, true)));

        verify(ctcStructureRepository, never()).save(any());
        verify(employeeEarningRepository, never()).save(any());
        verify(employeeBenefitRepository, never()).save(any());
        verify(employeeReimbursementRepository, never()).save(any());
        verify(ctcEpfComponentRepository, never()).save(any());
        verify(ctcEsiComponentRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(employeeEarningRepository, never()).deleteAllByTenantIdAndCtcStructureId(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("PT eligible but no work location: PT is omitted and ptEstimated is false")
    void ptOmittedWithoutWorkLocation() {
        employeeWithLocation(null);

        SalaryPreviewResponse response = service.preview(EMPLOYEE, request(profile(false, true)));

        assertThat(response.ptEstimated()).isFalse();
        assertThat(response.employeeDeductionsMonthly()).isEmpty();
        assertThat(response.takeHomeMonthlyEstimate()).isEqualByComparingTo(response.grossMonthly());
        verifyNoInteractions(professionalTaxService);
    }

    @Test
    @DisplayName("With a work location, PT is resolved for its state and the employee's gender and is deducted")
    void ptResolvedFromWorkLocation() {
        employeeWithLocation(LOCATION_ID);
        when(workLocationService.get(LOCATION_ID)).thenReturn(location("MH"));
        when(professionalTaxService.resolve(
                        eq(TENANT), eq("MH"), any(Money.class), eq("MALE"), eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(Money.of("200"));

        SalaryPreviewResponse response = service.preview(EMPLOYEE, request(profile(false, true)));

        assertThat(response.ptEstimated()).isTrue();
        assertThat(response.employeeDeductionsMonthly()).singleElement().satisfies(d -> {
            assertThat(d.componentCode()).isEqualTo("PT");
            assertThat(d.monthlyAmount()).isEqualByComparingTo("200");
        });
        assertThat(response.takeHomeMonthlyEstimate()).isEqualByComparingTo("49800.00");
    }

    private static SalaryPreviewRequest request(StatutoryProfileRequest profile) {
        return new SalaryPreviewRequest(
                new BigDecimal("600000.00"),
                FROM,
                null,
                List.of(
                        new SalaryComponentItemRequest(
                                BASIC_ID,
                                CalculationType.PERCENTAGE,
                                new BigDecimal("50"),
                                PercentageOf.CTC,
                                true,
                                "MONTHLY",
                                null),
                        new SalaryComponentItemRequest(
                                HRA_ID,
                                CalculationType.PERCENTAGE,
                                new BigDecimal("50"),
                                PercentageOf.BASIC,
                                true,
                                "MONTHLY",
                                null),
                        new SalaryComponentItemRequest(
                                SPECIAL_ID, CalculationType.FLAT, null, null, true, "MONTHLY", null, true)),
                List.of(),
                List.of(),
                profile);
    }

    private static StatutoryProfileRequest profile(boolean pf, boolean pt) {
        return new StatutoryProfileRequest(pf, pt, false, false, pf, false, false, null, null, null);
    }

    private static Earning earning(String code, String type) {
        Earning e = new Earning(TENANT, "system");
        e.setCode(code);
        e.setName(code);
        e.setEarningType(type);
        e.setIncludedInCtc(true);
        return e;
    }

    private void employeeWithLocation(UUID workLocationId) {
        when(employeeService.get(EMPLOYEE))
                .thenReturn(new EmployeeResponse(
                        EMPLOYEE,
                        TENANT,
                        "EMP-001",
                        "John",
                        null,
                        "Doe",
                        "MALE",
                        LocalDate.of(2025, 1, 1),
                        null,
                        null,
                        "john@example.com",
                        null,
                        false,
                        null,
                        null,
                        null,
                        workLocationId,
                        Instant.now(),
                        Instant.now()));
    }

    private static WorkLocationResponse location(String stateCode) {
        return new WorkLocationResponse(
                LOCATION_ID,
                TENANT,
                "HQ",
                "Head office",
                "1 Road",
                null,
                "Mumbai",
                "Maharashtra",
                stateCode,
                "400001",
                "IN",
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    private static BigDecimal amountOf(SalaryPreviewResponse response, UUID componentId) {
        return response.earnings().stream()
                .filter(e -> componentId.equals(e.componentId()))
                .findFirst()
                .orElseThrow()
                .monthlyAmount();
    }
}
