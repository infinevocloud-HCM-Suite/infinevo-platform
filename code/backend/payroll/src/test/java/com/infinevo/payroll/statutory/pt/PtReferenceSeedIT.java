package com.infinevo.payroll.statutory.pt;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test asserting statutory professional tax slabs for all 21 states seeded in reference.pt_slab (W-31.2).
 *
 * <p>Each test assertion validates a known gross on a specific date against the statutory state Act.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PtReferenceSeedIT extends AbstractIntegrationTest {

    @Autowired
    private ProfessionalTaxService professionalTaxService;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @Test
    @DisplayName(
            "1. Andhra Pradesh (AP) — AP Tax on Professions, Trades, Callings and Employments Act, 1987, First Schedule")
    void andhraPradeshStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "AP", Money.of("25000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("2. Assam (AS) — Assam Professions, Trades, Callings and Employments Taxation Act, 1947, Schedule")
    void assamStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "AS", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("208.0000"));
    }

    @Test
    @DisplayName("3. Bihar (BR) — Bihar State Tax on Professions, Trades, Callings and Employments Act, 2011, Schedule")
    void biharStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "BR", Money.of("50000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("166.6600"));
    }

    @Test
    @DisplayName("4. Chhattisgarh (CG) — Chhattisgarh State Tax on Professions Act, 1995, Schedule")
    void chhattisgarhStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "CG", Money.of("40000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("5. Gujarat (GJ) — Gujarat State Tax on Professions Act, 1976, Schedule")
    void gujaratStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "GJ", Money.of("20000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("6. Jharkhand (JH) — Jharkhand Tax on Professions Act, 2011, Schedule")
    void jharkhandStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "JH", Money.of("50000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("150.0000"));
    }

    @Test
    @DisplayName("7. Karnataka (KA) — Karnataka Tax on Professions Act, 1976, Schedule (amended 2023)")
    void karnatakaStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("8. Kerala (KL) — Kerala Municipality Act, 1994, Section 245 (Half-yearly: Sep)")
    void keralaStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "KL", Money.of("50000.0000"), "male", LocalDate.of(2025, 9, 30));
        assertThat(pt).isEqualTo(Money.of("450.0000"));
    }

    @Test
    @DisplayName("9. Madhya Pradesh (MP) — MP Vritti Kar Adhiniyam, 1995, Schedule (Feb balancing)")
    void madhyaPradeshStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "MP", Money.of("40000.0000"), "male", LocalDate.of(2025, 2, 28));
        assertThat(pt).isEqualTo(Money.of("212.0000"));
    }

    @Test
    @DisplayName("10. Maharashtra (MH) — Maharashtra State Tax on Professions Act, 1975, Schedule I (Feb balancing)")
    void maharashtraStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "MH", Money.of("30000.0000"), "male", LocalDate.of(2025, 2, 28));
        assertThat(pt).isEqualTo(Money.of("300.0000"));
    }

    @Test
    @DisplayName("11. Manipur (MN) — Manipur Professions Taxation Act, 1981, Schedule")
    void manipurStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "MN", Money.of("20000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("208.0000"));
    }

    @Test
    @DisplayName("12. Mizoram (MZ) — Mizoram Professions Taxation Act, 1995, Schedule")
    void mizoramStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "MZ", Money.of("25000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("208.0000"));
    }

    @Test
    @DisplayName("13. Nagaland (NL) — Nagaland Professions Taxation Act, 1968, Schedule")
    void nagalandStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "NL", Money.of("15000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("208.0000"));
    }

    @Test
    @DisplayName("14. Odisha (OD) — Odisha State Tax on Professions Act, 2000, Schedule (Dec balancing)")
    void odishaStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "OD", Money.of("30000.0000"), "male", LocalDate.of(2025, 12, 31));
        assertThat(pt).isEqualTo(Money.of("300.0000"));
    }

    @Test
    @DisplayName("15. Punjab (PB) — Punjab State Development Tax Act, 2018")
    void punjabStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "PB", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("16. Puducherry (PY) — Puducherry Municipalities Act, 1973 (Half-yearly: Mar)")
    void puducherryStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "PY", Money.of("250000.0000"), "male", LocalDate.of(2025, 3, 31));
        assertThat(pt).isEqualTo(Money.of("500.0000"));
    }

    @Test
    @DisplayName("17. Sikkim (SK) — Sikkim Tax on Professions Act, 2006, Schedule")
    void sikkimStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "SK", Money.of("35000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("150.0000"));
    }

    @Test
    @DisplayName("18. Tamil Nadu (TN) — Tamil Nadu Municipal Laws Act, 1998 (Half-yearly: Mar)")
    void tamilNaduStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "TN", Money.of("50000.0000"), "male", LocalDate.of(2025, 3, 31));
        assertThat(pt).isEqualTo(Money.of("690.0000"));
    }

    @Test
    @DisplayName("19. Telangana (TS) — Telangana Tax on Professions Act, 1987, First Schedule")
    void telanganaStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "TS", Money.of("25000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("20. Tripura (TR) — Tripura Professions Taxation Act, 1997, Schedule")
    void tripuraStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "TR", Money.of("20000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("21. West Bengal (WB) — West Bengal State Tax on Professions Act, 1979, Schedule")
    void westBengalStatutorySlab() {
        Money pt = professionalTaxService.resolve(
                TENANT_A, "WB", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(pt).isEqualTo(Money.of("150.0000"));
    }
}
