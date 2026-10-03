package com.infinevo.payroll.statutory.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayRunDays;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.salary.StatutoryProfileResponse;
import com.infinevo.payroll.statutory.lines.ContributionShare;
import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.statutory.settings.DeductionCycle;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import com.infinevo.payroll.statutory.settings.SettingSource;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatutoryLineContributorTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID payrunId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    private StatutorySettingsService statutorySettingsService;
    private ProfessionalTaxService professionalTaxService;
    private WorkLocationService workLocationService;
    private EmployeeStatutoryProfileService employeeStatutoryProfileService;

    private StatutoryLineContributor contributor;

    @BeforeEach
    void setUp() {
        statutorySettingsService = mock(StatutorySettingsService.class);
        professionalTaxService = mock(ProfessionalTaxService.class);
        workLocationService = mock(WorkLocationService.class);
        employeeStatutoryProfileService = mock(EmployeeStatutoryProfileService.class);

        contributor = new StatutoryLineContributor(
                statutorySettingsService, professionalTaxService, workLocationService, employeeStatutoryProfileService);

        WorkLocationResponse loc = new WorkLocationResponse(
                locationId,
                tenantId,
                "BLR",
                "Bangalore HQ",
                "Line 1",
                null,
                "Bangalore",
                "Karnataka",
                "KA",
                "560001",
                "IN",
                true,
                true,
                Instant.now(),
                Instant.now());
        when(workLocationService.get(locationId)).thenReturn(loc);
    }

    private EpfSettingResponse defaultEpf(boolean prorateRestricted) {
        return new EpfSettingResponse(
                UUID.randomUUID(),
                tenantId,
                true,
                "KN/BLR/12345",
                LocalDate.of(2020, 1, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                true,
                true,
                prorateRestricted,
                true,
                58,
                true,
                true,
                false,
                false,
                false,
                SettingSource.PERSISTED);
    }

    private EsiSettingResponse defaultEsi() {
        return new EsiSettingResponse(
                UUID.randomUUID(),
                tenantId,
                true,
                "ESI123",
                LocalDate.of(2020, 1, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"),
                true,
                false,
                SettingSource.PERSISTED);
    }

    private StatutoryProfileResponse defaultProfile(boolean eligiblePt) {
        return new StatutoryProfileResponse(
                UUID.randomUUID(),
                employeeId,
                true,
                eligiblePt,
                false,
                false,
                true,
                false,
                false,
                "PF123",
                "UAN123",
                null);
    }

    private EmployeeResponse createEmployee() {
        return new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP01",
                "John",
                null,
                "Doe",
                "MALE",
                LocalDate.of(2025, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "john@example.com",
                "9999999999",
                true,
                null,
                null,
                null,
                locationId,
                Instant.now(),
                Instant.now());
    }

    @Test
    @DisplayName("Hand calculation §8: July, Karnataka, 45k gross, 31 days paid -> matches every row of §8 table")
    void handCalculation_matchesSection8() {
        when(statutorySettingsService.epf(tenantId)).thenReturn(defaultEpf(false));
        when(statutorySettingsService.esi(tenantId)).thenReturn(defaultEsi());
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(defaultProfile(true));

        when(professionalTaxService.resolve(
                        eq(tenantId), eq("KA"), eq(Money.of("45000.0000")), eq("MALE"), eq(LocalDate.of(2026, 7, 31))))
                .thenReturn(Money.of("200.0000"));

        List<SalaryComponentItemResponse> earnings = List.of(
                new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BASIC",
                        "Basic Salary",
                        CalculationType.FLAT,
                        new BigDecimal("25000.0000"),
                        null,
                        new BigDecimal("25000.0000"),
                        new BigDecimal("300000.0000"),
                        true,
                        true),
                new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "HRA",
                        "House Rent Allowance",
                        CalculationType.FLAT,
                        new BigDecimal("10000.0000"),
                        null,
                        new BigDecimal("10000.0000"),
                        new BigDecimal("120000.0000"),
                        true,
                        true),
                new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "SPECIAL",
                        "Special Allowance",
                        CalculationType.FLAT,
                        new BigDecimal("7500.0000"),
                        null,
                        new BigDecimal("7500.0000"),
                        new BigDecimal("90000.0000"),
                        true,
                        true),
                new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "MEAL",
                        "Meal Allowance",
                        CalculationType.FLAT,
                        new BigDecimal("2500.0000"),
                        null,
                        new BigDecimal("2500.0000"),
                        new BigDecimal("30000.0000"),
                        true,
                        true));

        List<SalaryStatutoryItemResponse> statutory = List.of(
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPF_EMPLOYEE",
                        ContributionShare.EMPLOYEE,
                        new BigDecimal("15000.0000"),
                        new BigDecimal("12.0000"),
                        new BigDecimal("1800.0000"),
                        new BigDecimal("21600.0000"),
                        false),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPS_EMPLOYER",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000.0000"),
                        new BigDecimal("8.3300"),
                        new BigDecimal("1249.5000"),
                        new BigDecimal("14994.0000"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPF_EMPLOYER",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000.0000"),
                        new BigDecimal("3.6700"),
                        new BigDecimal("550.5000"),
                        new BigDecimal("6606.0000"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EDLI",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000.0000"),
                        new BigDecimal("0.5000"),
                        new BigDecimal("75.0000"),
                        new BigDecimal("900.0000"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPF_ADMIN",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000.0000"),
                        new BigDecimal("0.5000"),
                        new BigDecimal("75.0000"),
                        new BigDecimal("900.0000"),
                        true));

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("540000.0000"),
                new BigDecimal("45000.0000"),
                false,
                null,
                "Structure in force",
                BigDecimal.ZERO,
                earnings,
                List.of(),
                List.of(),
                statutory);

        List<PayLine> priorLines = List.of(
                new PayLine(
                        LineKind.EARNING,
                        LineSource.STRUCTURE,
                        UUID.randomUUID(),
                        "BASIC",
                        "Basic",
                        Money.of("25000.0000"),
                        true),
                new PayLine(
                        LineKind.EARNING,
                        LineSource.STRUCTURE,
                        UUID.randomUUID(),
                        "HRA",
                        "HRA",
                        Money.of("10000.0000"),
                        true),
                new PayLine(
                        LineKind.EARNING,
                        LineSource.STRUCTURE,
                        UUID.randomUUID(),
                        "SPECIAL",
                        "Special",
                        Money.of("7500.0000"),
                        true),
                new PayLine(
                        LineKind.EARNING,
                        LineSource.STRUCTURE,
                        UUID.randomUUID(),
                        "MEAL",
                        "Meal",
                        Money.of("2500.0000"),
                        true));

        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                createEmployee(),
                version,
                new WorkingDayBasisResponse(
                        new BigDecimal("31.00"), new BigDecimal("31.00"), UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                new PayRunDays(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("31.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),
                Set.of(),
                priorLines,
                PayRunType.REGULAR);

        List<PayLine> lines = contributor.contribute(ctx);

        Map<String, PayLine> map = lines.stream().collect(Collectors.toMap(PayLine::componentCode, l -> l));

        assertThat(map)
                .containsOnlyKeys(
                        "EPF_EMPLOYEE", "PROFESSIONAL_TAX", "EPS_EMPLOYER", "EPF_EMPLOYER", "EDLI", "EPF_ADMIN");

        // 1. EPF_EMPLOYEE: DEDUCTION, 1,800
        PayLine eePf = map.get("EPF_EMPLOYEE");
        assertThat(eePf.kind()).isEqualTo(LineKind.DEDUCTION);
        assertThat(eePf.source()).isEqualTo(LineSource.STATUTORY);
        assertThat(eePf.amount()).isEqualTo(Money.of("1800.0000"));

        // 2. PROFESSIONAL_TAX: DEDUCTION, 200
        PayLine pt = map.get("PROFESSIONAL_TAX");
        assertThat(pt.kind()).isEqualTo(LineKind.DEDUCTION);
        assertThat(pt.source()).isEqualTo(LineSource.STATUTORY);
        assertThat(pt.amount()).isEqualTo(Money.of("200.0000"));

        // 3. EPS_EMPLOYER: BENEFIT, 1,250
        PayLine eps = map.get("EPS_EMPLOYER");
        assertThat(eps.kind()).isEqualTo(LineKind.BENEFIT);
        assertThat(eps.source()).isEqualTo(LineSource.STATUTORY);
        assertThat(eps.amount()).isEqualTo(Money.of("1250.0000"));

        // 4. EPF_EMPLOYER: BENEFIT, 550
        PayLine erPf = map.get("EPF_EMPLOYER");
        assertThat(erPf.kind()).isEqualTo(LineKind.BENEFIT);
        assertThat(erPf.source()).isEqualTo(LineSource.STATUTORY);
        assertThat(erPf.amount()).isEqualTo(Money.of("550.0000"));

        // 5. EDLI: BENEFIT, 75
        PayLine edli = map.get("EDLI");
        assertThat(edli.kind()).isEqualTo(LineKind.BENEFIT);
        assertThat(edli.source()).isEqualTo(LineSource.STATUTORY);
        assertThat(edli.amount()).isEqualTo(Money.of("75.0000"));

        // 6. EPF_ADMIN: BENEFIT, 75
        PayLine admin = map.get("EPF_ADMIN");
        assertThat(admin.kind()).isEqualTo(LineKind.BENEFIT);
        assertThat(admin.source()).isEqualTo(LineSource.STATUTORY);
        assertThat(admin.amount()).isEqualTo(Money.of("75.0000"));

        // Totals matching §8:
        Money totalDeductions = lines.stream()
                .filter(l -> l.kind() == LineKind.DEDUCTION)
                .map(PayLine::amount)
                .reduce(Money.ZERO, Money::add);
        assertThat(totalDeductions).isEqualTo(Money.of("2000.0000"));

        Money totalBenefits = lines.stream()
                .filter(l -> l.kind() == LineKind.BENEFIT)
                .map(PayLine::amount)
                .reduce(Money.ZERO, Money::add);
        assertThat(totalBenefits).isEqualTo(Money.of("1950.0000"));

        Money netPay = Money.of("45000.0000").subtract(totalDeductions);
        assertThat(netPay).isEqualTo(Money.of("43000.0000"));
    }

    @Test
    @DisplayName("Employer rows are all BENEFIT kind, never DEDUCTION")
    void employerRows_areBenefitKind() {
        when(statutorySettingsService.epf(tenantId)).thenReturn(defaultEpf(false));
        when(statutorySettingsService.esi(tenantId)).thenReturn(defaultEsi());
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(defaultProfile(false));

        List<SalaryStatutoryItemResponse> statutory = List.of(
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPF_EMPLOYEE",
                        ContributionShare.EMPLOYEE,
                        new BigDecimal("15000"),
                        new BigDecimal("12"),
                        new BigDecimal("1800"),
                        new BigDecimal("21600"),
                        false),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPS_EMPLOYER",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000"),
                        new BigDecimal("8.33"),
                        new BigDecimal("1249.50"),
                        new BigDecimal("14994"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPF_EMPLOYER",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000"),
                        new BigDecimal("3.67"),
                        new BigDecimal("550.50"),
                        new BigDecimal("6606"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EDLI",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000"),
                        new BigDecimal("0.5"),
                        new BigDecimal("75"),
                        new BigDecimal("900"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "EPF_ADMIN",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000"),
                        new BigDecimal("0.5"),
                        new BigDecimal("75"),
                        new BigDecimal("900"),
                        true),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "ESI_EMPLOYEE",
                        ContributionShare.EMPLOYEE,
                        new BigDecimal("15000"),
                        new BigDecimal("0.75"),
                        new BigDecimal("112.50"),
                        new BigDecimal("1350"),
                        false),
                new SalaryStatutoryItemResponse(
                        UUID.randomUUID(),
                        "ESI_EMPLOYER",
                        ContributionShare.EMPLOYER,
                        new BigDecimal("15000"),
                        new BigDecimal("3.25"),
                        new BigDecimal("487.50"),
                        new BigDecimal("5850"),
                        true));

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("180000"),
                new BigDecimal("15000"),
                false,
                null,
                null,
                BigDecimal.ZERO,
                List.of(new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BASIC",
                        "Basic",
                        CalculationType.FLAT,
                        new BigDecimal("15000"),
                        null,
                        new BigDecimal("15000"),
                        new BigDecimal("180000"),
                        true,
                        true)),
                List.of(),
                List.of(),
                statutory);

        PayRunDays days = new PayRunDays(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("31"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                createEmployee(),
                version,
                new WorkingDayBasisResponse(
                        new BigDecimal("31"), new BigDecimal("31"), UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                days,
                Set.of(),
                List.of(),
                PayRunType.REGULAR);

        List<PayLine> lines = contributor.contribute(ctx);

        List<PayLine> employerLines = lines.stream()
                .filter(l -> List.of("EPS_EMPLOYER", "EPF_EMPLOYER", "EDLI", "EPF_ADMIN", "ESI_EMPLOYER")
                        .contains(l.componentCode()))
                .toList();

        assertThat(employerLines).isNotEmpty();
        assertThat(employerLines).allMatch(l -> l.kind() == LineKind.BENEFIT);
    }

    @Test
    @DisplayName("Employee not eligible for PT gets no PT line")
    void notEligibleForPt_getsNoPtLine() {
        when(statutorySettingsService.epf(tenantId)).thenReturn(defaultEpf(false));
        when(statutorySettingsService.esi(tenantId)).thenReturn(defaultEsi());
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(defaultProfile(false));

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("540000"),
                new BigDecimal("45000"),
                false,
                null,
                null,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of(),
                List.of());

        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                createEmployee(),
                version,
                new WorkingDayBasisResponse(
                        new BigDecimal("31"), new BigDecimal("31"), UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                PayRunDays.zero(),
                Set.of(),
                List.of(new PayLine(
                        LineKind.EARNING,
                        LineSource.STRUCTURE,
                        UUID.randomUUID(),
                        "BASIC",
                        "Basic",
                        Money.of("45000"),
                        true)),
                PayRunType.REGULAR);

        List<PayLine> lines = contributor.contribute(ctx);
        assertThat(lines).noneMatch(l -> "PROFESSIONAL_TAX".equals(l.componentCode()));
    }

    @Test
    @DisplayName("consider_earned_wage with 20 of 30 paid days scales the base by two thirds before the cap")
    void considerEarnedWage_scalesBaseBeforeCap() {
        when(statutorySettingsService.epf(tenantId)).thenReturn(defaultEpf(false));
        when(statutorySettingsService.esi(tenantId)).thenReturn(defaultEsi());
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(defaultProfile(false));

        // Basic 30,000, 20 of 30 days -> earned basic is 30,000 * 20 / 30 = 20,000
        // Cap is 15,000 (prorateRestrictedWage=false) -> capped at 15,000 -> 12% = 1,800
        List<SalaryComponentItemResponse> earnings = List.of(new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BASIC",
                "Basic",
                CalculationType.FLAT,
                new BigDecimal("30000"),
                null,
                new BigDecimal("30000"),
                new BigDecimal("360000"),
                true,
                true));

        List<SalaryStatutoryItemResponse> statutory = List.of(new SalaryStatutoryItemResponse(
                UUID.randomUUID(),
                "EPF_EMPLOYEE",
                ContributionShare.EMPLOYEE,
                new BigDecimal("15000"),
                new BigDecimal("12"),
                new BigDecimal("1800"),
                new BigDecimal("21600"),
                false));

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("360000"),
                new BigDecimal("30000"),
                false,
                null,
                null,
                BigDecimal.ZERO,
                earnings,
                List.of(),
                List.of(),
                statutory);

        PayRunDays days = new PayRunDays(
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"));

        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                YearMonth.of(2026, 6),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                createEmployee(),
                version,
                new WorkingDayBasisResponse(
                        new BigDecimal("30.00"), new BigDecimal("30.00"), UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                days,
                Set.of(),
                List.of(),
                PayRunType.REGULAR);

        List<PayLine> lines = contributor.contribute(ctx);
        PayLine pf = lines.stream()
                .filter(l -> "EPF_EMPLOYEE".equals(l.componentCode()))
                .findFirst()
                .orElseThrow();
        assertThat(pf.amount()).isEqualTo(Money.of("1800.0000"));
    }

    @Test
    @DisplayName("prorate_restricted_wage scales the cap too: 15,000 cap becomes 10,000 -> 12% = 1,200")
    void prorateRestrictedWage_scalesCapToo() {
        when(statutorySettingsService.epf(tenantId)).thenReturn(defaultEpf(true));
        when(statutorySettingsService.esi(tenantId)).thenReturn(defaultEsi());
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(defaultProfile(false));

        // Basic 30,000, 20 of 30 days -> earned basic is 20,000
        // Cap is 15,000 * 20 / 30 = 10,000 -> capped at 10,000 -> 12% = 1,200
        List<SalaryComponentItemResponse> earnings = List.of(new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BASIC",
                "Basic",
                CalculationType.FLAT,
                new BigDecimal("30000"),
                null,
                new BigDecimal("30000"),
                new BigDecimal("360000"),
                true,
                true));

        List<SalaryStatutoryItemResponse> statutory = List.of(new SalaryStatutoryItemResponse(
                UUID.randomUUID(),
                "EPF_EMPLOYEE",
                ContributionShare.EMPLOYEE,
                new BigDecimal("15000"),
                new BigDecimal("12"),
                new BigDecimal("1800"),
                new BigDecimal("21600"),
                false));

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("360000"),
                new BigDecimal("30000"),
                false,
                null,
                null,
                BigDecimal.ZERO,
                earnings,
                List.of(),
                List.of(),
                statutory);

        PayRunDays days = new PayRunDays(
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"));

        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                YearMonth.of(2026, 6),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                createEmployee(),
                version,
                new WorkingDayBasisResponse(
                        new BigDecimal("30.00"), new BigDecimal("30.00"), UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                days,
                Set.of(),
                List.of(),
                PayRunType.REGULAR);

        List<PayLine> lines = contributor.contribute(ctx);
        PayLine pf = lines.stream()
                .filter(l -> "EPF_EMPLOYEE".equals(l.componentCode()))
                .findFirst()
                .orElseThrow();
        assertThat(pf.amount()).isEqualTo(Money.of("1200.0000"));
    }

    @Test
    @DisplayName("no version statutory rows -> no PF or ESI lines, PT still resolved")
    void noVersionStatutoryRows_ptStillResolved() {
        when(statutorySettingsService.epf(tenantId)).thenReturn(defaultEpf(false));
        when(statutorySettingsService.esi(tenantId)).thenReturn(defaultEsi());
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(defaultProfile(true));

        when(professionalTaxService.resolve(
                        eq(tenantId), eq("KA"), eq(Money.of("50000.0000")), eq("MALE"), eq(LocalDate.of(2026, 7, 31))))
                .thenReturn(Money.of("200.0000"));

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("600000"),
                new BigDecimal("50000"),
                false,
                null,
                null,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of(),
                List.of()); // empty statutory rows

        List<PayLine> priorLines = List.of(new PayLine(
                LineKind.EARNING,
                LineSource.STRUCTURE,
                UUID.randomUUID(),
                "GROSS",
                "Gross",
                Money.of("50000.0000"),
                true));

        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                createEmployee(),
                version,
                new WorkingDayBasisResponse(
                        new BigDecimal("31"), new BigDecimal("31"), UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                PayRunDays.zero(),
                Set.of(),
                priorLines,
                PayRunType.REGULAR);

        List<PayLine> lines = contributor.contribute(ctx);
        assertThat(lines).hasSize(1);
        PayLine pt = lines.get(0);
        assertThat(pt.componentCode()).isEqualTo("PROFESSIONAL_TAX");
        assertThat(pt.amount()).isEqualTo(Money.of("200.0000"));
    }
}
