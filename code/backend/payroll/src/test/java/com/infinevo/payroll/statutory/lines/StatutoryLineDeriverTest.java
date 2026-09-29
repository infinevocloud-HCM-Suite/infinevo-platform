package com.infinevo.payroll.statutory.lines;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.salary.EmployeeStatutoryProfile;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatutoryLineDeriverTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    private EpfSetting createDefaultEpfSetting() {
        EpfSetting epf = new EpfSetting(tenantId, "test-user");
        epf.setEnabled(true);
        epf.setWageCeiling(new BigDecimal("15000.0000"));
        epf.setRestrictEmployeeToCeiling(true);
        epf.setRestrictEmployerToCeiling(true);
        epf.setEmployeeRate(new BigDecimal("12.0000"));
        epf.setEmployerRate(new BigDecimal("12.0000"));
        epf.setEpsRate(new BigDecimal("8.3300"));
        epf.setEdliRate(new BigDecimal("0.5000"));
        epf.setAdminChargeRate(new BigDecimal("0.5000"));
        epf.setEpsSeniorAge((short) 58);
        epf.setIncludeEmployerInCtc(true);
        epf.setIncludeEdliAdminInCtc(true);
        return epf;
    }

    private EsiSetting createDefaultEsiSetting() {
        EsiSetting esi = new EsiSetting(tenantId, "test-user");
        esi.setEnabled(true);
        esi.setWageCeiling(new BigDecimal("21000.0000"));
        esi.setEmployeeRate(new BigDecimal("0.7500"));
        esi.setEmployerRate(new BigDecimal("3.2500"));
        esi.setIncludeEmployerInCtc(true);
        return esi;
    }

    private EmployeeStatutoryProfile createProfile(boolean eligiblePf, boolean eligibleEps, boolean eligibleEsi) {
        EmployeeStatutoryProfile profile = new EmployeeStatutoryProfile(tenantId, employeeId, "test-user");
        profile.setEligibleForPf(eligiblePf);
        profile.setEligibleForEps(eligibleEps);
        profile.setEligibleForEsi(eligibleEsi);
        return profile;
    }

    @Test
    @DisplayName("Hand calculation §8: Basic 25000, Gross 45000, restricted ceiling, age 30 matches table exactly")
    void workedExample_allEligible_restrictedCeiling() {
        Money basic = Money.of(new BigDecimal("25000.0000"));
        Money gross = Money.of(new BigDecimal("45000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = LocalDate.of(1996, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        // Gross 45,000 > 21,000 ESI ceiling -> no ESI rows
        assertThat(lines).hasSize(5);
        Map<StatutoryComponentCode, DerivedStatutoryLine> map =
                lines.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));

        // 1. EPF_EMPLOYEE
        DerivedStatutoryLine eePf = map.get(StatutoryComponentCode.EPF_EMPLOYEE);
        assertThat(eePf).isNotNull();
        assertThat(eePf.share()).isEqualTo(ContributionShare.EMPLOYEE);
        assertThat(eePf.wageBase().raw()).isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(eePf.rate()).isEqualByComparingTo(new BigDecimal("12.0000"));
        assertThat(eePf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("1800.0000"));
        assertThat(eePf.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("21600.0000"));
        assertThat(eePf.includedInCtc()).isFalse();

        // 2. EPS_EMPLOYER
        DerivedStatutoryLine eps = map.get(StatutoryComponentCode.EPS_EMPLOYER);
        assertThat(eps).isNotNull();
        assertThat(eps.share()).isEqualTo(ContributionShare.EMPLOYER);
        assertThat(eps.wageBase().raw()).isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(eps.rate()).isEqualByComparingTo(new BigDecimal("8.3300"));
        assertThat(eps.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("1249.5000"));
        assertThat(eps.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("14994.0000"));
        assertThat(eps.includedInCtc()).isTrue();

        // 3. EPF_EMPLOYER
        DerivedStatutoryLine erPf = map.get(StatutoryComponentCode.EPF_EMPLOYER);
        assertThat(erPf).isNotNull();
        assertThat(erPf.share()).isEqualTo(ContributionShare.EMPLOYER);
        assertThat(erPf.wageBase().raw()).isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(erPf.rate()).isEqualByComparingTo(new BigDecimal("3.6700"));
        assertThat(erPf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("550.5000"));
        assertThat(erPf.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("6606.0000"));
        assertThat(erPf.includedInCtc()).isTrue();

        // 4. EDLI
        DerivedStatutoryLine edli = map.get(StatutoryComponentCode.EDLI);
        assertThat(edli).isNotNull();
        assertThat(edli.share()).isEqualTo(ContributionShare.EMPLOYER);
        assertThat(edli.wageBase().raw()).isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(edli.rate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(edli.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("75.0000"));
        assertThat(edli.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("900.0000"));
        assertThat(edli.includedInCtc()).isTrue();

        // 5. EPF_ADMIN
        DerivedStatutoryLine admin = map.get(StatutoryComponentCode.EPF_ADMIN);
        assertThat(admin).isNotNull();
        assertThat(admin.share()).isEqualTo(ContributionShare.EMPLOYER);
        assertThat(admin.wageBase().raw()).isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(admin.rate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(admin.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("75.0000"));
        assertThat(admin.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("900.0000"));
        assertThat(admin.includedInCtc()).isTrue();
    }

    @Test
    @DisplayName("Unrestricted employee share on basic 25,000 gives 3,000 while employer rows stay capped")
    void unrestrictedEmployeeShare_basic25000() {
        Money basic = Money.of(new BigDecimal("25000.0000"));
        Money gross = Money.of(new BigDecimal("45000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        epf.setRestrictEmployeeToCeiling(false);
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = LocalDate.of(1996, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        Map<StatutoryComponentCode, DerivedStatutoryLine> map =
                lines.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));

        DerivedStatutoryLine eePf = map.get(StatutoryComponentCode.EPF_EMPLOYEE);
        assertThat(eePf.wageBase().raw()).isEqualByComparingTo(new BigDecimal("25000.0000"));
        assertThat(eePf.rate()).isEqualByComparingTo(new BigDecimal("12.0000"));
        assertThat(eePf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("3000.0000"));

        // Employer rows unchanged
        assertThat(map.get(StatutoryComponentCode.EPS_EMPLOYER).monthlyAmount().raw())
                .isEqualByComparingTo(new BigDecimal("1249.5000"));
        assertThat(map.get(StatutoryComponentCode.EPF_EMPLOYER).monthlyAmount().raw())
                .isEqualByComparingTo(new BigDecimal("550.5000"));
        assertThat(map.get(StatutoryComponentCode.EDLI).monthlyAmount().raw())
                .isEqualByComparingTo(new BigDecimal("75.0000"));
        assertThat(map.get(StatutoryComponentCode.EPF_ADMIN).monthlyAmount().raw())
                .isEqualByComparingTo(new BigDecimal("75.0000"));
    }

    @Test
    @DisplayName("Not eligible for EPS puts the whole 12% in EPF_EMPLOYER")
    void notEligibleForEps_putsWhole12PercentInEpfEmployer() {
        Money basic = Money.of(new BigDecimal("25000.0000"));
        Money gross = Money.of(new BigDecimal("45000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, false, true); // not eligible for EPS
        EpfSetting epf = createDefaultEpfSetting();
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = LocalDate.of(1996, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        Map<StatutoryComponentCode, DerivedStatutoryLine> map =
                lines.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));

        DerivedStatutoryLine eps = map.get(StatutoryComponentCode.EPS_EMPLOYER);
        assertThat(eps.monthlyAmount().raw()).isEqualByComparingTo(BigDecimal.ZERO);

        DerivedStatutoryLine erPf = map.get(StatutoryComponentCode.EPF_EMPLOYER);
        assertThat(erPf.rate()).isEqualByComparingTo(new BigDecimal("12.0000"));
        assertThat(erPf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("1800.0000"));
    }

    @Test
    @DisplayName("Age 58 on effective_from puts the whole 12% in EPF_EMPLOYER (senior age exemption)")
    void age58OnEffectiveFrom_putsWhole12PercentInEpfEmployer() {
        Money basic = Money.of(new BigDecimal("25000.0000"));
        Money gross = Money.of(new BigDecimal("45000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = LocalDate.of(1968, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1); // exactly 58 years

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        Map<StatutoryComponentCode, DerivedStatutoryLine> map =
                lines.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));

        DerivedStatutoryLine eps = map.get(StatutoryComponentCode.EPS_EMPLOYER);
        assertThat(eps.monthlyAmount().raw()).isEqualByComparingTo(BigDecimal.ZERO);

        DerivedStatutoryLine erPf = map.get(StatutoryComponentCode.EPF_EMPLOYER);
        assertThat(erPf.rate()).isEqualByComparingTo(new BigDecimal("12.0000"));
        assertThat(erPf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("1800.0000"));
    }

    @Test
    @DisplayName("Gross 21,000 gets ESI and 21,000.01 does not")
    void gross21000GetsEsi_andGross21000Point01DoesNot() {
        Money basic = Money.of(new BigDecimal("10000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        EsiSetting esi = createDefaultEsiSetting(); // ceiling 21,000.0000
        LocalDate dob = LocalDate.of(1996, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        // Gross 21,000.0000 -> ESI rows present
        Money gross21k = Money.of(new BigDecimal("21000.0000"));
        List<DerivedStatutoryLine> linesWithEsi =
                StatutoryLineDeriver.derive(basic, gross21k, profile, epf, esi, dob, effectiveFrom);

        Map<StatutoryComponentCode, DerivedStatutoryLine> mapWithEsi =
                linesWithEsi.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));
        assertThat(mapWithEsi).containsKey(StatutoryComponentCode.ESI_EMPLOYEE);
        assertThat(mapWithEsi).containsKey(StatutoryComponentCode.ESI_EMPLOYER);

        DerivedStatutoryLine eeEsi = mapWithEsi.get(StatutoryComponentCode.ESI_EMPLOYEE);
        assertThat(eeEsi.wageBase().raw()).isEqualByComparingTo(new BigDecimal("21000.0000"));
        assertThat(eeEsi.rate()).isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(eeEsi.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("157.5000"));
        assertThat(eeEsi.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("1890.0000"));
        assertThat(eeEsi.includedInCtc()).isFalse();

        DerivedStatutoryLine erEsi = mapWithEsi.get(StatutoryComponentCode.ESI_EMPLOYER);
        assertThat(erEsi.wageBase().raw()).isEqualByComparingTo(new BigDecimal("21000.0000"));
        assertThat(erEsi.rate()).isEqualByComparingTo(new BigDecimal("3.2500"));
        assertThat(erEsi.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("682.5000"));
        assertThat(erEsi.annualAmount().raw()).isEqualByComparingTo(new BigDecimal("8190.0000"));
        assertThat(erEsi.includedInCtc()).isTrue();

        // Gross 21,000.0100 -> Above ceiling, no ESI rows
        Money grossOver = Money.of(new BigDecimal("21000.0100"));
        List<DerivedStatutoryLine> linesWithoutEsi =
                StatutoryLineDeriver.derive(basic, grossOver, profile, epf, esi, dob, effectiveFrom);

        Map<StatutoryComponentCode, DerivedStatutoryLine> mapWithoutEsi =
                linesWithoutEsi.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));
        assertThat(mapWithoutEsi).doesNotContainKey(StatutoryComponentCode.ESI_EMPLOYEE);
        assertThat(mapWithoutEsi).doesNotContainKey(StatutoryComponentCode.ESI_EMPLOYER);
    }

    @Test
    @DisplayName("Disabled EPF setting gives no PF rows")
    void disabledEpfSetting_givesNoPfRows() {
        Money basic = Money.of(new BigDecimal("25000.0000"));
        Money gross = Money.of(new BigDecimal("15000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        epf.setEnabled(false);
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = LocalDate.of(1996, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        // Only ESI rows present, no EPF rows
        assertThat(lines).hasSize(2);
        assertThat(lines.stream()
                        .noneMatch(l -> l.code().name().startsWith("EPF")
                                || l.code().name().startsWith("EPS")))
                .isTrue();
    }

    @Test
    @DisplayName("No rounding — 12% of 15,000.5000 is 1,800.0600")
    void noRounding_scale4Preserved() {
        Money basic = Money.of(new BigDecimal("15000.5000"));
        Money gross = Money.of(new BigDecimal("30000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        epf.setWageCeiling(new BigDecimal("20000.0000")); // so 15,000.5000 is within ceiling
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = LocalDate.of(1996, 1, 1);
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        Map<StatutoryComponentCode, DerivedStatutoryLine> map =
                lines.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));

        DerivedStatutoryLine eePf = map.get(StatutoryComponentCode.EPF_EMPLOYEE);
        assertThat(eePf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("1800.0600"));
    }

    @Test
    @DisplayName("Null date of birth treats employee as non-senior; EPS applies normally")
    void nullDateOfBirth_epsAppliesNormally() {
        Money basic = Money.of(new BigDecimal("25000.0000"));
        Money gross = Money.of(new BigDecimal("45000.0000"));
        EmployeeStatutoryProfile profile = createProfile(true, true, true);
        EpfSetting epf = createDefaultEpfSetting();
        EsiSetting esi = createDefaultEsiSetting();
        LocalDate dob = null;
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        List<DerivedStatutoryLine> lines =
                StatutoryLineDeriver.derive(basic, gross, profile, epf, esi, dob, effectiveFrom);

        assertThat(lines).hasSize(5);
        Map<StatutoryComponentCode, DerivedStatutoryLine> map =
                lines.stream().collect(Collectors.toMap(DerivedStatutoryLine::code, l -> l));

        DerivedStatutoryLine eps = map.get(StatutoryComponentCode.EPS_EMPLOYER);
        assertThat(eps).isNotNull();
        assertThat(eps.rate()).isEqualByComparingTo(new BigDecimal("8.3300"));
        assertThat(eps.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("1249.5000"));

        DerivedStatutoryLine erPf = map.get(StatutoryComponentCode.EPF_EMPLOYER);
        assertThat(erPf).isNotNull();
        assertThat(erPf.rate()).isEqualByComparingTo(new BigDecimal("3.6700"));
        assertThat(erPf.monthlyAmount().raw()).isEqualByComparingTo(new BigDecimal("550.5000"));
    }
}
