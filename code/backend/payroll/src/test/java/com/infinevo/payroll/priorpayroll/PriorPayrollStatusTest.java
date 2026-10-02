package com.infinevo.payroll.priorpayroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.setup.TenantSetupStep;
import com.infinevo.core.setup.TenantSetupStepRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * W-38.1 §7: Unit test for PriorPayrollService status endpoint.
 * Covers:
 * - fy 2026-2027: first run 2026-10, imports for 04–07 => missing 08, 09
 * - first run 2026-04 => none
 * - no run, today 2026-10-15 => 04–09 less imported
 */
class PriorPayrollStatusTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String FY = "2026-2027";
    private static final Instant OCT_15_2026 =
            LocalDate.of(2026, 10, 15).atStartOfDay(ZoneOffset.UTC).toInstant();

    private PriorPayrollMonthRepository monthRepository;
    private PayRunRepository payRunRepository;
    private EmployeeRepository employeeRepository;
    private TenantSetupStepRepository setupStepRepository;
    private PriorPayrollRowValidator rowValidator;
    private PriorPayrollServiceImpl service;

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantSetupStepRepository> provider(TenantSetupStepRepository repo) {
        ObjectProvider<TenantSetupStepRepository> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(repo);
        return p;
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        monthRepository = mock(PriorPayrollMonthRepository.class);
        payRunRepository = mock(PayRunRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        setupStepRepository = mock(TenantSetupStepRepository.class);

        Clock clock = Clock.fixed(OCT_15_2026, ZoneOffset.UTC);
        rowValidator = new PriorPayrollRowValidator(employeeRepository, payRunRepository, null, clock);
        service = new PriorPayrollServiceImpl(
                monthRepository, employeeRepository, payRunRepository, rowValidator, provider(setupStepRepository));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("fy 2026-2027 with first regular run 2026-10 and imports for 04-07 reports missing 08 and 09")
    void firstRunInOctoberMissingAugustAndSeptember() {
        when(monthRepository.findDistinctPeriods(TENANT_ID, "2026-04", "2027-03"))
                .thenReturn(List.of("2026-04", "2026-05", "2026-06", "2026-07"));
        when(payRunRepository.findEarliestPeriod(
                        TENANT_ID, PayRunType.REGULAR, PayRunStatus.CANCELLED, "2026-04", "2027-03"))
                .thenReturn(Optional.of("2026-10"));

        PriorPayrollStatusResponse response = service.status(FY);

        assertThat(response.financialYear()).isEqualTo(FY);
        assertThat(response.firstRegularRunPeriod()).isEqualTo("2026-10");
        assertThat(response.importedPeriods()).containsExactly("2026-04", "2026-05", "2026-06", "2026-07");
        assertThat(response.missingPeriods()).containsExactly("2026-08", "2026-09");
        assertThat(response.setupStepSkipped()).isFalse();
    }

    @Test
    @DisplayName("first regular run in April 2026-04 reports no missing periods")
    void firstRunInAprilReportsNone() {
        when(monthRepository.findDistinctPeriods(TENANT_ID, "2026-04", "2027-03"))
                .thenReturn(List.of());
        when(payRunRepository.findEarliestPeriod(
                        TENANT_ID, PayRunType.REGULAR, PayRunStatus.CANCELLED, "2026-04", "2027-03"))
                .thenReturn(Optional.of("2026-04"));

        PriorPayrollStatusResponse response = service.status(FY);

        assertThat(response.financialYear()).isEqualTo(FY);
        assertThat(response.firstRegularRunPeriod()).isEqualTo("2026-04");
        assertThat(response.importedPeriods()).isEmpty();
        assertThat(response.missingPeriods()).isEmpty();
        assertThat(response.setupStepSkipped()).isFalse();
    }

    @Test
    @DisplayName("no run, today 2026-10-15 reports 04-09 less imported")
    void noRunCutoffIsCurrentMonth() {
        // Assume April and May were imported
        when(monthRepository.findDistinctPeriods(TENANT_ID, "2026-04", "2027-03"))
                .thenReturn(List.of("2026-04", "2026-05"));
        when(payRunRepository.findEarliestPeriod(
                        TENANT_ID, PayRunType.REGULAR, PayRunStatus.CANCELLED, "2026-04", "2027-03"))
                .thenReturn(Optional.empty());

        PriorPayrollStatusResponse response = service.status(FY);

        assertThat(response.financialYear()).isEqualTo(FY);
        assertThat(response.firstRegularRunPeriod()).isNull();
        assertThat(response.importedPeriods()).containsExactly("2026-04", "2026-05");
        // 04-09 less 04 and 05 => 06, 07, 08, 09
        assertThat(response.missingPeriods()).containsExactly("2026-06", "2026-07", "2026-08", "2026-09");
        assertThat(response.setupStepSkipped()).isFalse();
    }

    @Test
    @DisplayName("a skipped PRIOR_PAYROLL row reports setup_step_skipped")
    void skippedPriorPayrollRowReportsSetupStepSkipped() {
        TenantSetupStep step =
                new TenantSetupStep(TENANT_ID, "PRIOR_PAYROLL", PlatformModule.PAYROLL, 4, Instant.now());
        step.setSkipped(true);
        when(setupStepRepository.findByTenantIdAndStepCode(TENANT_ID, "PRIOR_PAYROLL"))
                .thenReturn(Optional.of(step));

        when(monthRepository.findDistinctPeriods(TENANT_ID, "2026-04", "2027-03"))
                .thenReturn(List.of());
        when(payRunRepository.findEarliestPeriod(
                        TENANT_ID, PayRunType.REGULAR, PayRunStatus.CANCELLED, "2026-04", "2027-03"))
                .thenReturn(Optional.of("2026-04"));

        PriorPayrollStatusResponse response = service.status(FY);

        assertThat(response.setupStepSkipped()).isTrue();
    }

    @Test
    @DisplayName("no setup-step repository in the context reports false")
    void noSetupStepRepositoryReportsFalse() {
        PriorPayrollServiceImpl serviceWithoutRepo = new PriorPayrollServiceImpl(
                monthRepository, employeeRepository, payRunRepository, rowValidator, provider(null));

        when(monthRepository.findDistinctPeriods(TENANT_ID, "2026-04", "2027-03"))
                .thenReturn(List.of());
        when(payRunRepository.findEarliestPeriod(
                        TENANT_ID, PayRunType.REGULAR, PayRunStatus.CANCELLED, "2026-04", "2027-03"))
                .thenReturn(Optional.of("2026-04"));

        PriorPayrollStatusResponse response = serviceWithoutRepo.status(FY);

        assertThat(response.setupStepSkipped()).isFalse();
    }
}
