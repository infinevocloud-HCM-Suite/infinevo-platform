package com.infinevo.payroll.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * D-145: {@code GET .../tds/{fy}} shows what the next regular pay run deducts. The pay month is the first
 * month of the year, not before the effective month, with no regular run computed, approved or paid
 * ({@link PayRunRepository#findCountedRegularPeriods} answers those months); "deducted so far" counts every
 * such run. The repository is stubbed as the query answers for each case; {@code EmployeeTdsIT} runs the
 * query itself on Postgres.
 */
class EmployeeTdsFiguresTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final String FY = "2026-2027";

    private EmployeeTdsRepository repository;
    private EmployeePayRunLineRepository lineRepository;
    private PriorPayrollTaxQuery priorTax;
    private PayRunRepository payRunRepository;
    private EmployeeTdsServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        repository = mock(EmployeeTdsRepository.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        priorTax = mock(PriorPayrollTaxQuery.class);
        payRunRepository = mock(PayRunRepository.class);
        when(priorTax.total(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        // Today is 15 November 2026 in India; the spread does not depend on it.
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T06:00:00Z"), ZoneOffset.UTC);
        service = new EmployeeTdsServiceImpl(
                repository, lineRepository, mock(EmployeeService.class), priorTax, payRunRepository, clock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("No run this month: April-October counted, 70,000 deducted ⇒ next November, 5 months, 10,000")
    void noRunThisMonth() {
        counted("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");

        TdsSpread spread = service.spread(record("2026-04"), new BigDecimal("70000.0000"));

        assertThat(spread.nextPayMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(spread.remaining()).isEqualByComparingTo("50000");
        assertThat(spread.monthsLeft()).isEqualTo(5);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("This month computed: November counted, 80,000 deducted ⇒ next December, 4 months, 10,000")
    void thisMonthComputed() {
        // November's run is COMPUTED, so the query counts it.
        counted("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");

        TdsSpread spread = service.spread(record("2026-04"), new BigDecimal("80000.0000"));

        assertThat(spread.nextPayMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(spread.monthsLeft()).isEqualTo(4);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("This month approved: counted as computed ⇒ next December, the share the December run deducts")
    void thisMonthApproved() {
        // November's run is APPROVED; the query counts it as it counts COMPUTED and PAID.
        counted("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");

        TdsSpread spread = service.spread(record("2026-04"), new BigDecimal("88000.0000"));

        assertThat(spread.nextPayMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(spread.monthsLeft()).isEqualTo(4);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("8000.0000");
    }

    @Test
    @DisplayName("An earlier month still open: September has no counted run ⇒ next September, 7 months")
    void earlierMonthStillOpen() {
        // September's run is DRAFT or LOCKED, so it is not counted although October is.
        counted("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-10");

        TdsSpread spread = service.spread(record("2026-04"), new BigDecimal("60000.0000"));

        assertThat(spread.nextPayMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(spread.monthsLeft()).isEqualTo(7);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("8571.4286");
    }

    @Test
    @DisplayName("The search starts at the effective month: runs before it do not matter")
    void startsAtEffectiveMonth() {
        when(payRunRepository.findCountedRegularPeriods(TENANT, "2027-01", "2027-03"))
                .thenReturn(List.of());

        TdsSpread spread = service.spread(record("2027-01"), BigDecimal.ZERO);

        assertThat(spread.nextPayMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(spread.monthsLeft()).isEqualTo(3);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("40000.0000");
    }

    @Test
    @DisplayName("Every month to March counted: no next pay month, 0 months left, share 0")
    void yearDone() {
        counted(
                "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11", "2026-12",
                "2027-01", "2027-02", "2027-03");

        TdsSpread spread = service.spread(record("2026-04"), new BigDecimal("110000.0000"));

        assertThat(spread.nextPayMonth()).isNull();
        assertThat(spread.monthsLeft()).isZero();
        assertThat(spread.monthlyShare().isZero()).isTrue();
        assertThat(spread.remaining()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("GET returns deducted_so_far, remaining, months_left, monthly_share and next_pay_month")
    void getCarriesFigures() throws Exception {
        when(repository.findActive(TENANT, EMPLOYEE, FY)).thenReturn(Optional.of(record("2026-04")));
        when(lineRepository.sumTaxLines(eq(TENANT), eq(EMPLOYEE), eq("2026-04"), eq("2027-03"), any()))
                .thenReturn(new BigDecimal("60000.0000"));
        when(priorTax.total(any(), any(), any())).thenReturn(new BigDecimal("20000.0000"));
        counted("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EmployeeTdsController(service, mock(EmployeeService.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        mvc.perform(get("/api/v1/payroll/employees/{id}/tds/{fy}", EMPLOYEE, FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annual_tax").value(120000.00))
                .andExpect(jsonPath("$.data.deducted_so_far").value(80000.0))
                .andExpect(jsonPath("$.data.year_to_date").value(80000.0))
                .andExpect(jsonPath("$.data.remaining").value(40000.0))
                .andExpect(jsonPath("$.data.months_left").value(4))
                .andExpect(jsonPath("$.data.monthly_share").value(10000.00))
                .andExpect(jsonPath("$.data.next_pay_month").value("2026-12"));
    }

    private void counted(String... periods) {
        when(payRunRepository.findCountedRegularPeriods(TENANT, "2026-04", "2027-03"))
                .thenReturn(List.of(periods));
    }

    private static EmployeeTds record(String effectiveFrom) {
        return new EmployeeTds(
                TENANT,
                EMPLOYEE,
                FY,
                TaxRegime.NEW,
                TdsSource.OFFICER,
                null,
                new BigDecimal("1200000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("120000.00"),
                effectiveFrom,
                "October figure",
                "officer",
                Instant.parse("2026-10-01T00:00:00Z"));
    }
}
