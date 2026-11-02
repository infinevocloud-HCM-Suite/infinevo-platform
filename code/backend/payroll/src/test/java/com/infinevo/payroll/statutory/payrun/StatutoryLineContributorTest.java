package com.infinevo.payroll.statutory.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
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
import java.util.Optional;
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

    /** EPF on, 12 / 12 / 8.33, ceiling 15,000, EDLI and admin in the salary structure. */
    private EpfSettingResponse defaultEpf(boolean prorateRestricted) {
        return epf(true, prorateRestricted, true, true, "12.0000", "12.0000");
    }

    private EpfSettingResponse epf(
            boolean enabled,
            boolean prorateRestricted,
            boolean edliInStructure,
            boolean adminInStructure,
            String employeeRate,
            String employerRate) {
        return new EpfSettingResponse(
                UUID.randomUUID(),
                tenantId,
                enabled,
                "KN/BLR/12345",
                LocalDate.of(2020, 1, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal(employeeRate),
                new BigDecimal(employerRate),
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
                true,
                false,
                edliInStructure,
                adminInStructure,
                false,
                SettingSource.PERSISTED);
    }

    private EsiSettingResponse defaultEsi() {
        return esi(true);
    }

    private EsiSettingResponse esi(boolean enabled) {
        return new EsiSettingResponse(
                UUID.randomUUID(),
                tenantId,
                enabled,
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
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(profile(true, true, true));

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

        assertThat(employerLines)
                .extracting(PayLine::componentCode)
                .containsExactlyInAnyOrder("EPS_EMPLOYER", "EPF_EMPLOYER", "EDLI", "EPF_ADMIN", "ESI_EMPLOYER");
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
    @DisplayName("no BASIC earning and no ESI tick -> no PF or ESI lines, PT still resolved")
    void noBasicNoEsiTick_ptStillResolved() {
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

    // ---- W-76.3: every run decides from the settings and ticks now, never from the saved rows ----

    @Test
    @DisplayName("W-76.3: Basic named 'Basic Salary' is found by name -> PF 1,800")
    void basicSalaryByName_paysPf() {
        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, false, false),
                List.of(earning("BASIC SALARY", "25000.0000", true)),
                List.of());

        assertThat(map.get("EPF_EMPLOYEE").amount()).isEqualTo(Money.of("1800.0000"));
    }

    @Test
    @DisplayName("W-76.3: Basic under its own code (BASIC_PAY) falls back to the saved PF wage base -> PF 1,800")
    void basicUnderOwnCode_fallsBackToSavedPfBase() {
        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, false, false),
                List.of(earning("BASIC_PAY", "25000.0000", true)),
                savedPfRows());

        assertThat(map.get("EPF_EMPLOYEE").amount()).isEqualTo(Money.of("1800.0000"));
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("1800.0000"));
    }

    @Test
    @DisplayName("W-76.3: company EPF off -> no PF, EPS, EDLI or admin lines though the rows were saved")
    void epfOff_noPfLinesThoughRowsSaved() {
        Map<String, PayLine> map = run(
                epf(false, false, true, true, "12.0000", "12.0000"),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map).doesNotContainKeys("EPF_EMPLOYEE", "EPS_EMPLOYER", "EPF_EMPLOYER", "EDLI", "EPF_ADMIN");
    }

    @Test
    @DisplayName("W-76.3: employee PF unticked -> no PF lines though the rows were saved")
    void pfUnticked_noPfLines() {
        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(false, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map).doesNotContainKeys("EPF_EMPLOYEE", "EPS_EMPLOYER", "EPF_EMPLOYER", "EDLI", "EPF_ADMIN");
    }

    @Test
    @DisplayName("W-76.3: PF ticked after the save (no saved rows) -> PF 1,800, EPS 1,250, PF 550, EDLI 75, admin 75")
    void pfTickedNoSavedRows_pfApplied() {
        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                List.of());

        assertThat(map.get("EPF_EMPLOYEE").amount()).isEqualTo(Money.of("1800.0000"));
        assertThat(map.get("EPS_EMPLOYER").amount()).isEqualTo(Money.of("1250.0000"));
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("550.0000"));
        assertThat(map.get("EDLI").amount()).isEqualTo(Money.of("75.0000"));
        assertThat(map.get("EPF_ADMIN").amount()).isEqualTo(Money.of("75.0000"));
    }

    @Test
    @DisplayName("W-76.3: EPS unticked after the salary was saved -> no EPS, whole 12% in EPF_EMPLOYER")
    void epsUntickedSinceSave_wholeEmployerShareToEpf() {
        EmployeePersonalService personal = mock(EmployeePersonalService.class);
        contributor = new StatutoryLineContributor(
                statutorySettingsService,
                professionalTaxService,
                workLocationService,
                employeeStatutoryProfileService,
                personal);

        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, false, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map).doesNotContainKey("EPS_EMPLOYER");
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("1800.0000"));
        // the date of birth is only read when the EPS tick could apply
        verify(personal, never()).find(employeeId);
    }

    @Test
    @DisplayName("W-76.3: EPS ticked, 58 on the period start -> no EPS, whole 12% in EPF_EMPLOYER")
    void epsTickedAtEpsAge_wholeEmployerShareToEpf() {
        withDateOfBirth(LocalDate.of(1968, 7, 1));

        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map).doesNotContainKey("EPS_EMPLOYER");
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("1800.0000"));
    }

    @Test
    @DisplayName("W-76.3: EPS ticked, 57 on the period start (58 the next day) -> EPS 1,250 and employer PF 550")
    void epsTickedUnderEpsAge_split() {
        withDateOfBirth(LocalDate.of(1968, 7, 2));

        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                List.of());

        assertThat(map.get("EPS_EMPLOYER").amount()).isEqualTo(Money.of("1250.0000"));
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("550.0000"));
    }

    @Test
    @DisplayName("W-76.3: date of birth unknown or unreadable -> treated as under the EPS age")
    void epsTickedDobUnknown_split() {
        EmployeePersonalService personal = withDateOfBirth(null);
        Map<String, PayLine> unknown = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                List.of());
        assertThat(unknown.get("EPS_EMPLOYER").amount()).isEqualTo(Money.of("1250.0000"));

        when(personal.find(employeeId)).thenThrow(new IllegalStateException("personal section unavailable"));
        Map<String, PayLine> failing = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                List.of());
        assertThat(failing.get("EPS_EMPLOYER").amount()).isEqualTo(Money.of("1250.0000"));
        assertThat(failing.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("550.0000"));
    }

    @Test
    @DisplayName("W-76.3: an EPS member still gets EPS 1,250 and employer PF 550")
    void epsMember_splitUnchanged() {
        Map<String, PayLine> map = run(
                defaultEpf(false),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map.get("EPS_EMPLOYER").amount()).isEqualTo(Money.of("1250.0000"));
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("550.0000"));
    }

    @Test
    @DisplayName("W-76.3: EDLI and admin 'include in salary structure' off -> neither line, PF still paid")
    void edliAndAdminStructureOff_noLines() {
        Map<String, PayLine> map = run(
                epf(true, false, false, false, "12.0000", "12.0000"),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map).doesNotContainKeys("EDLI", "EPF_ADMIN");
        assertThat(map.get("EPF_EMPLOYEE").amount()).isEqualTo(Money.of("1800.0000"));
    }

    @Test
    @DisplayName("W-76.3: EDLI on, admin off -> EDLI 75 only; admin on, EDLI off -> admin 75 only")
    void edliAndAdminEachFollowOwnSwitch() {
        Map<String, PayLine> edliOnly = run(
                epf(true, false, true, false, "12.0000", "12.0000"),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                List.of());
        assertThat(edliOnly.get("EDLI").amount()).isEqualTo(Money.of("75.0000"));
        assertThat(edliOnly).doesNotContainKey("EPF_ADMIN");

        Map<String, PayLine> adminOnly = run(
                epf(true, false, false, true, "12.0000", "12.0000"),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                List.of());
        assertThat(adminOnly.get("EPF_ADMIN").amount()).isEqualTo(Money.of("75.0000"));
        assertThat(adminOnly).doesNotContainKey("EDLI");
    }

    @Test
    @DisplayName("W-76.3: PF rates follow the settings now (10%), not the 12% saved with the salary")
    void pfRatesFollowSettingsNotSavedRow() {
        Map<String, PayLine> map = run(
                epf(true, false, true, true, "10.0000", "10.0000"),
                defaultEsi(),
                profile(true, true, false),
                List.of(earning("BASIC", "25000.0000", true)),
                savedPfRows());

        assertThat(map.get("EPF_EMPLOYEE").amount()).isEqualTo(Money.of("1500.0000"));
        assertThat(map.get("EPS_EMPLOYER").amount()).isEqualTo(Money.of("1250.0000"));
        assertThat(map.get("EPF_EMPLOYER").amount()).isEqualTo(Money.of("250.0000"));
    }

    @Test
    @DisplayName("W-76.3: ESI ticked, gross 20,000 under the 21,000 ceiling -> employee 150, employer 650"
            + " (employer 'include in structure' off does not stop it)")
    void esiTickedUnderCeiling_applied() {
        Map<String, PayLine> map = run(
                epf(false, false, false, false, "12.0000", "12.0000"),
                defaultEsi(),
                profile(false, false, true),
                List.of(earning("BASIC", "15000.0000", true), earning("HRA", "5000.0000", true)),
                List.of());

        assertThat(map.get("ESI_EMPLOYEE").kind()).isEqualTo(LineKind.DEDUCTION);
        assertThat(map.get("ESI_EMPLOYEE").amount()).isEqualTo(Money.of("150.0000"));
        assertThat(map.get("ESI_EMPLOYER").kind()).isEqualTo(LineKind.BENEFIT);
        assertThat(map.get("ESI_EMPLOYER").amount()).isEqualTo(Money.of("650.0000"));
    }

    @Test
    @DisplayName("W-76.3: a disabled earning does not count toward the ESI gross")
    void esiGrossIgnoresDisabledEarning() {
        Map<String, PayLine> map = run(
                epf(false, false, false, false, "12.0000", "12.0000"),
                defaultEsi(),
                profile(false, false, true),
                List.of(
                        earning("BASIC", "15000.0000", true),
                        earning("HRA", "5000.0000", true),
                        earning("BONUS", "5000.0000", false)),
                List.of());

        assertThat(map.get("ESI_EMPLOYEE").amount()).isEqualTo(Money.of("150.0000"));
    }

    @Test
    @DisplayName("W-76.3: ESI ticked, gross 22,000 over the 21,000 ceiling -> no ESI lines")
    void esiTickedOverCeiling_none() {
        Map<String, PayLine> map = run(
                epf(false, false, false, false, "12.0000", "12.0000"),
                defaultEsi(),
                profile(false, false, true),
                List.of(earning("BASIC", "15000.0000", true), earning("HRA", "7000.0000", true)),
                savedEsiRows());

        assertThat(map).doesNotContainKeys("ESI_EMPLOYEE", "ESI_EMPLOYER");
    }

    @Test
    @DisplayName("W-76.3: company ESI off -> no ESI lines though the rows were saved")
    void esiOff_none() {
        Map<String, PayLine> map = run(
                epf(false, false, false, false, "12.0000", "12.0000"),
                esi(false),
                profile(false, false, true),
                List.of(earning("BASIC", "15000.0000", true), earning("HRA", "5000.0000", true)),
                savedEsiRows());

        assertThat(map).doesNotContainKeys("ESI_EMPLOYEE", "ESI_EMPLOYER");
    }

    @Test
    @DisplayName("W-76.3: employee ESI unticked -> no ESI lines though the rows were saved")
    void esiUnticked_none() {
        Map<String, PayLine> map = run(
                epf(false, false, false, false, "12.0000", "12.0000"),
                defaultEsi(),
                profile(false, false, false),
                List.of(earning("BASIC", "15000.0000", true), earning("HRA", "5000.0000", true)),
                savedEsiRows());

        assertThat(map).doesNotContainKeys("ESI_EMPLOYEE", "ESI_EMPLOYER");
    }

    private EmployeePersonalService withDateOfBirth(LocalDate dateOfBirth) {
        EmployeePersonalService personal = mock(EmployeePersonalService.class);
        when(personal.find(employeeId))
                .thenReturn(Optional.of(new EmployeePersonalResponse(
                        UUID.randomUUID(),
                        tenantId,
                        employeeId,
                        dateOfBirth,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        Instant.now(),
                        Instant.now())));
        contributor = new StatutoryLineContributor(
                statutorySettingsService,
                professionalTaxService,
                workLocationService,
                employeeStatutoryProfileService,
                personal);
        return personal;
    }

    private StatutoryProfileResponse profile(boolean pf, boolean eps, boolean esi) {
        return new StatutoryProfileResponse(
                UUID.randomUUID(), employeeId, pf, false, false, esi, eps, false, false, "PF123", "UAN123", null);
    }

    private static SalaryComponentItemResponse earning(String code, String monthly, boolean enabled) {
        BigDecimal amount = new BigDecimal(monthly);
        return new SalaryComponentItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                code,
                code,
                CalculationType.FLAT,
                amount,
                null,
                amount,
                amount.multiply(new BigDecimal("12")),
                enabled,
                true);
    }

    /** The PF rows a salary saved at 12% carries; the run must not read them. */
    private static List<SalaryStatutoryItemResponse> savedPfRows() {
        return List.of(
                saved("EPF_EMPLOYEE", ContributionShare.EMPLOYEE, "15000.0000", "12.0000", "1800.0000"),
                saved("EPS_EMPLOYER", ContributionShare.EMPLOYER, "15000.0000", "8.3300", "1249.5000"),
                saved("EPF_EMPLOYER", ContributionShare.EMPLOYER, "15000.0000", "3.6700", "550.5000"),
                saved("EDLI", ContributionShare.EMPLOYER, "15000.0000", "0.5000", "75.0000"),
                saved("EPF_ADMIN", ContributionShare.EMPLOYER, "15000.0000", "0.5000", "75.0000"));
    }

    private static List<SalaryStatutoryItemResponse> savedEsiRows() {
        return List.of(
                saved("ESI_EMPLOYEE", ContributionShare.EMPLOYEE, "20000.0000", "0.7500", "150.0000"),
                saved("ESI_EMPLOYER", ContributionShare.EMPLOYER, "20000.0000", "3.2500", "650.0000"));
    }

    private static SalaryStatutoryItemResponse saved(
            String code, ContributionShare share, String wageBase, String rate, String monthly) {
        BigDecimal m = new BigDecimal(monthly);
        return new SalaryStatutoryItemResponse(
                UUID.randomUUID(),
                code,
                share,
                new BigDecimal(wageBase),
                new BigDecimal(rate),
                m,
                m.multiply(new BigDecimal("12")),
                !"EMPLOYEE".equals(share.name()));
    }

    /** A full July (31 of 31 paid) with these settings, ticks, earnings and saved statutory rows. */
    private Map<String, PayLine> run(
            EpfSettingResponse epf,
            EsiSettingResponse esi,
            StatutoryProfileResponse profile,
            List<SalaryComponentItemResponse> earnings,
            List<SalaryStatutoryItemResponse> statutory) {
        when(statutorySettingsService.epf(tenantId)).thenReturn(epf);
        when(statutorySettingsService.esi(tenantId)).thenReturn(esi);
        when(employeeStatutoryProfileService.get(employeeId)).thenReturn(profile);

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("300000.0000"),
                new BigDecimal("25000.0000"),
                false,
                null,
                null,
                BigDecimal.ZERO,
                earnings,
                List.of(),
                List.of(),
                statutory);
        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                tenantId,
                UUID.randomUUID(),
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
                List.of(),
                PayRunType.REGULAR);
        return contributor.contribute(ctx).stream().collect(Collectors.toMap(PayLine::componentCode, l -> l));
    }
}
