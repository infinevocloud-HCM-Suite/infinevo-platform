package com.infinevo.payroll.compliance.form24q;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.challan.TdsChallan;
import com.infinevo.payroll.challan.TdsChallanAllocation;
import com.infinevo.payroll.challan.TdsChallanAllocationRepository;
import com.infinevo.payroll.challan.TdsChallanRepository;
import com.infinevo.payroll.challan.TdsQuarter;
import com.infinevo.payroll.challan.TdsReturn;
import com.infinevo.payroll.challan.TdsReturnRepository;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Form24QAnnexure1ServiceImpl} (W-89.2). Runs, employee runs, TDS lines, challans and
 * allocations sit in lists behind the repository mocks; the mocks filter by the ids the service asks for, so which
 * runs count for the quarter is decided by the service, not by the test.
 */
class Form24QAnnexure1ServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String FY = "2026-2027";
    private static final String VALID_PAN = "ABCDE1234F";

    private TaxDeductorService taxDeductorService;
    private TdsChallanRepository challanRepository;
    private TdsChallanAllocationRepository allocationRepository;
    private PayRunRepository payRunRepository;
    private EmployeePayRunRepository employeePayRunRepository;
    private EmployeePayRunLineRepository lineRepository;
    private EmployeeService employeeService;
    private EmployeeIdentificationService identificationService;
    private Form138DeductorResolver deductorResolver;
    private TdsReturnRepository returnRepository;
    private Form24QAnnexure1ServiceImpl service;

    private final List<TdsChallan> challans = new ArrayList<>();
    private final List<TdsChallanAllocation> allocations = new ArrayList<>();
    private final List<PayRun> runs = new ArrayList<>();
    private final List<EmployeePayRun> employeeRuns = new ArrayList<>();
    private final List<EmployeePayRunLine> lines = new ArrayList<>();
    private final List<TdsReturn> filed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        taxDeductorService = mock(TaxDeductorService.class);
        challanRepository = mock(TdsChallanRepository.class);
        allocationRepository = mock(TdsChallanAllocationRepository.class);
        payRunRepository = mock(PayRunRepository.class);
        employeePayRunRepository = mock(EmployeePayRunRepository.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        employeeService = mock(EmployeeService.class);
        identificationService = mock(EmployeeIdentificationService.class);
        deductorResolver = mock(Form138DeductorResolver.class);
        returnRepository = mock(TdsReturnRepository.class);
        service = new Form24QAnnexure1ServiceImpl(
                taxDeductorService,
                challanRepository,
                allocationRepository,
                payRunRepository,
                employeePayRunRepository,
                lineRepository,
                employeeService,
                identificationService,
                deductorResolver,
                returnRepository);

        TaxDeductorResponse deductor = deductorResponse();
        when(taxDeductorService.current()).thenReturn(Optional.of(deductor));
        Form138DeductorResolver.Resolution complete =
                new Form138DeductorResolver.Resolution(form138Deductor(), List.of());
        when(deductorResolver.resolve()).thenReturn(complete);

        when(challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(
                        eq(TENANT_ID), any(), any()))
                .thenAnswer(inv -> challans.stream()
                        .filter(c -> c.getFinancialYear().equals(inv.getArgument(1)))
                        .filter(c -> c.getQuarter().equals(inv.getArgument(2)))
                        .toList());
        when(allocationRepository.findByTenantIdAndChallanIdIn(eq(TENANT_ID), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> ids = inv.getArgument(1);
                    return allocations.stream()
                            .filter(a -> ids.contains(a.getChallanId()))
                            .toList();
                });
        when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(eq(TENANT_ID), anyCollection(), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> periods = inv.getArgument(1);
                    Collection<?> statuses = inv.getArgument(2);
                    return runs.stream()
                            .filter(r -> periods.contains(r.getPeriod().toString()))
                            .filter(r -> statuses.contains(r.getStatus()))
                            .toList();
                });
        when(employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                        eq(TENANT_ID), anyCollection(), eq(InclusionStatus.INCLUDED)))
                .thenAnswer(inv -> {
                    Collection<?> ids = inv.getArgument(1);
                    return employeeRuns.stream()
                            .filter(e -> ids.contains(e.getPayrunId()))
                            .toList();
                });
        when(lineRepository.findTaxLinesByPayrunIds(eq(TENANT_ID), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> ids = inv.getArgument(1);
                    return lines.stream()
                            .filter(l -> ids.contains(l.getPayrunId()))
                            .toList();
                });
        when(returnRepository.findByTenantIdAndFinancialYearAndQuarter(eq(TENANT_ID), any(), any()))
                .thenAnswer(inv -> filed.stream()
                        .filter(r -> r.getFinancialYear().equals(inv.getArgument(1)))
                        .filter(r -> r.getQuarter().equals(inv.getArgument(2)))
                        .findFirst());
        when(returnRepository.findByTenantIdAndFinancialYearOrderByQuarterAsc(eq(TENANT_ID), any()))
                .thenAnswer(inv -> filed.stream()
                        .filter(r -> r.getFinancialYear().equals(inv.getArgument(1)))
                        .toList());

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------- fixtures

    private static TaxDeductorResponse deductorResponse() {
        return new TaxDeductorResponse(
                UUID.randomUUID(),
                TENANT_ID,
                "BLRA12345B",
                "AAACA1234A",
                null,
                null,
                "Asha Rao",
                null,
                "Director",
                "K",
                "tax@acme.example",
                "9876543210",
                null,
                "ABCPR1234K",
                "asha@acme.example",
                "9876500000",
                null,
                null,
                null,
                null);
    }

    static Form138Deductor form138Deductor() {
        return new Form138Deductor(
                "BLRA12345B",
                "AAACA1234A",
                "Acme Private Limited",
                List.of("12 MG Road", "Bengaluru"),
                15,
                "560001",
                "tax@acme.example",
                "9876543210",
                "K",
                null,
                "Asha Rao",
                "Director",
                "ABCPR1234K",
                "asha@acme.example",
                "9876500000");
    }

    private TdsChallan challan(String quarter, String tax, LocalDate tendered) {
        return challanIn(FY, quarter, tax, tendered);
    }

    private TdsChallan challanIn(String fy, String quarter, String tax, LocalDate tendered) {
        TdsChallan c = new TdsChallan();
        c.setId(UUID.randomUUID());
        c.setTenantId(TENANT_ID);
        c.setFinancialYear(fy);
        c.setQuarter(quarter);
        c.setBsrCode("0210045");
        c.setChallanSerialNo(String.valueOf(10 + challans.size()));
        c.setTenderDate(tendered);
        c.setTaxAmount(new BigDecimal(tax).setScale(2));
        c.setSurchargeAmount(BigDecimal.ZERO.setScale(2));
        c.setCessAmount(BigDecimal.ZERO.setScale(2));
        c.setInterestAmount(BigDecimal.ZERO.setScale(2));
        c.setFeePenaltyAmount(BigDecimal.ZERO.setScale(2));
        c.setTotalAmount(c.allocatable());
        c.setMinorHead("200");
        challans.add(c);
        return c;
    }

    private PayRun run(YearMonth period, LocalDate paidOn, PayRunStatus status) {
        PayRun r = mock(PayRun.class);
        UUID id = UUID.randomUUID();
        when(r.getId()).thenReturn(id);
        when(r.getPeriod()).thenReturn(period);
        when(r.getPaidOn()).thenReturn(paidOn);
        when(r.getStatus()).thenReturn(status);
        runs.add(r);
        return r;
    }

    /** An employee on a run, with gross pay and TDS; returns the employee id. */
    private UUID employee(PayRun run, String number, String pan, String gross, String tds) {
        UUID employeeId = UUID.randomUUID();
        UUID eprId = UUID.randomUUID();
        UUID runId = run.getId();
        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getId()).thenReturn(eprId);
        when(epr.getEmployeeId()).thenReturn(employeeId);
        when(epr.getPayrunId()).thenReturn(runId);
        when(epr.getGrossEarnings()).thenReturn(new BigDecimal(gross));
        employeeRuns.add(epr);

        EmployeePayRunLine line = mock(EmployeePayRunLine.class);
        when(line.getEmployeePayrunId()).thenReturn(eprId);
        when(line.getPayrunId()).thenReturn(runId);
        when(line.getAmount()).thenReturn(new BigDecimal(tds));
        lines.add(line);

        EmployeeResponse emp = mock(EmployeeResponse.class);
        when(emp.employeeNumber()).thenReturn(number);
        when(emp.firstName()).thenReturn("Emp");
        when(emp.lastName()).thenReturn(number);
        when(employeeService.get(employeeId)).thenReturn(emp);

        if (pan == null) {
            when(identificationService.find(employeeId)).thenReturn(Optional.empty());
        } else {
            EmployeeIdentificationResponse ident = mock(EmployeeIdentificationResponse.class);
            when(ident.panNumber()).thenReturn(pan);
            when(identificationService.find(employeeId)).thenReturn(Optional.of(ident));
        }
        return employeeId;
    }

    private void allocate(TdsChallan challan, PayRun run, UUID employeeId, String tax) {
        TdsChallanAllocation a = new TdsChallanAllocation();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT_ID);
        a.setChallanId(challan.getId());
        a.setPayRunId(run.getId());
        a.setEmployeeId(employeeId);
        a.setAllocatedTax(new BigDecimal(tax).setScale(2));
        a.setAllocatedSurcharge(BigDecimal.ZERO.setScale(2));
        a.setAllocatedCess(BigDecimal.ZERO.setScale(2));
        allocations.add(a);
    }

    private void markFiled(String quarter, String token) {
        filed.add(new TdsReturn(TENANT_ID, TdsQuarter.parse(FY, quarter), token, LocalDate.of(2026, 7, 25), "x"));
    }

    private void markFiledIn(String fy, String quarter, String token) {
        filed.add(new TdsReturn(TENANT_ID, TdsQuarter.parse(fy, quarter), token, LocalDate.of(2027, 4, 25), "x"));
    }

    private static List<String[]> records(byte[] file, String type) {
        List<String[]> out = new ArrayList<>();
        for (String line : new String(file, StandardCharsets.US_ASCII).split("\r\n")) {
            String[] f = line.split("\\^", -1);
            if (f.length > 1 && f[1].equals(type)) {
                out.add(f);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- tests

    @Test
    void missingDeductorIsDeductorNotSet() {
        // No deductor yet: the quarter still previews, and the resolver's "fill in" items say what is missing.
        when(taxDeductorService.current()).thenReturn(Optional.empty());
        when(deductorResolver.resolve())
                .thenReturn(new Form138DeductorResolver.Resolution(null, List.of("Deductor details")));
        Annexure1PreviewResponse p = service.previewAnnexure1(FY, "Q1");
        assertThat(p.tan()).isNull();
        assertThat(p.portalFileProblems()).contains("Fill in: Deductor details");
    }

    @Test
    void badInputIsAValidationError() {
        assertThatThrownBy(() -> service.previewAnnexure1(FY, "Q5")).isInstanceOf(Form24QValidationException.class);
        assertThatThrownBy(() -> service.previewAnnexure1("2026/27", "Q1"))
                .isInstanceOf(Form24QValidationException.class);
        assertThatThrownBy(() -> service.exportAnnexure1(FY, "Q1", "PDF"))
                .isInstanceOf(Form24QValidationException.class);
    }

    @Test
    @DisplayName("TDS split across two challans gives two rows whose gross shares add up to the run's gross")
    void splitAllocationGivesTwoRows() {
        TdsChallan c1 = challan("Q1", "3333", LocalDate.of(2026, 6, 7));
        TdsChallan c2 = challan("Q1", "1667", LocalDate.of(2026, 6, 9));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        UUID e = employee(may, "E001", VALID_PAN, "99999.99", "5000.00");
        allocate(c1, may, e, "3333");
        allocate(c2, may, e, "1667");

        Annexure1PreviewResponse p = service.previewAnnexure1(FY, "Q1");

        assertThat(p.deductees()).hasSize(2);
        assertThat(p.deductees()).allSatisfy(d -> {
            assertThat(d.grossAmount()).isPositive();
            assertThat(d.totalTaxDeducted()).isEqualByComparingTo(d.totalTaxDeposited());
            assertThat(d.paymentDate()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(d.pan()).isEqualTo(VALID_PAN);
            assertThat(d.reasonForNonDeduction()).isNull();
        });
        assertThat(p.deductees().stream()
                        .map(DeducteeDetailRecord::grossAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("99999.99");
        assertThat(p.deductees())
                .extracting(DeducteeDetailRecord::matchedChallanId)
                .containsExactlyInAnyOrder(c1.getId(), c2.getId());
        assertThat(p.deductees())
                .filteredOn(d -> d.matchedChallanId().equals(c1.getId()))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.totalTaxDeposited()).isEqualByComparingTo("3333");
                    assertThat(d.challanSerialNumberInBatch()).isEqualTo(1);
                });
        assertThat(p.totalGrossPaid()).isEqualByComparingTo("99999.99");
        assertThat(p.totalTdsDeducted()).isEqualByComparingTo("5000");
        assertThat(p.totalTdsDeposited()).isEqualByComparingTo("5000");
        assertThat(p.unallocatedTds()).isEqualByComparingTo("0");
        assertThat(p.isReconciled()).isTrue();
        assertThat(p.challans()).extracting(ChallanDetailRecord::deducteeCount).containsExactly(1, 1);
        assertThat(p.portalFileProblems()).isEmpty();
    }

    @Test
    @DisplayName("TDS no challan covers is its own row, deposited 0, and blocks the portal file")
    void uncoveredTdsIsItsOwnRow() {
        TdsChallan c1 = challan("Q1", "3000", LocalDate.of(2026, 6, 7));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        UUID e = employee(may, "E001", VALID_PAN, "100000.00", "5000.00");
        allocate(c1, may, e, "3000");

        Annexure1PreviewResponse p = service.previewAnnexure1(FY, "Q1");

        assertThat(p.deductees()).hasSize(2);
        DeducteeDetailRecord uncovered = p.deductees().stream()
                .filter(d -> d.matchedChallanId() == null)
                .findFirst()
                .orElseThrow();
        assertThat(uncovered.totalTaxDeposited()).isEqualByComparingTo("0");
        assertThat(uncovered.totalTaxDeducted()).isEqualByComparingTo("2000");
        assertThat(uncovered.grossAmount()).isPositive();
        assertThat(p.deductees().stream()
                        .map(DeducteeDetailRecord::grossAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100000");
        assertThat(p.unallocatedTds()).isEqualByComparingTo("2000");
        assertThat(p.totalTdsDeposited()).isEqualByComparingTo("3000");
        assertThat(p.isReconciled()).isFalse();
        assertThat(p.portalFileProblems()).anyMatch(s -> s.contains("2000.00") && s.contains("challan"));
    }

    @Test
    @DisplayName("An employee without a valid PAN is PANNOTAVBL with reason C; under 20% TDS is a problem")
    void missingPan() {
        TdsChallan c1 = challan("Q1", "25000", LocalDate.of(2026, 6, 7));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        UUID low = employee(may, "E010", null, "100000.00", "5000.00");
        UUID atRate = employee(may, "E011", "not-a-pan", "100000.00", "20000.00");
        allocate(c1, may, low, "5000");
        allocate(c1, may, atRate, "20000");

        Annexure1PreviewResponse p = service.previewAnnexure1(FY, "Q1");

        assertThat(p.deductees()).hasSize(2).allSatisfy(d -> {
            assertThat(d.pan()).isEqualTo("PANNOTAVBL");
            assertThat(d.reasonForNonDeduction()).isEqualTo("C");
        });
        assertThat(p.missingPanCount()).isEqualTo(2);
        assertThat(p.missingPanEmployeeIds()).containsExactlyInAnyOrder(low, atRate);
        // 5% of pay is below the 20% the higher rate needs; exactly 20% is not.
        assertThat(p.portalFileProblems())
                .anyMatch(s -> s.contains("E010") && s.contains("20%"))
                .noneMatch(s -> s.contains("E011"));
    }

    @Test
    @DisplayName("The portal file writes section code 1002 (section 392, private employer) on every DD row")
    void sectionCode1002InTheFile() {
        TdsChallan c1 = challan("Q1", "3000", LocalDate.of(2026, 6, 7));
        TdsChallan c2 = challan("Q1", "2000", LocalDate.of(2026, 6, 9));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        UUID e = employee(may, "E001", VALID_PAN, "100000.00", "5000.00");
        allocate(c1, may, e, "3000");
        allocate(c2, may, e, "2000");

        byte[] file = service.exportAnnexure1(FY, "Q1", "txt");

        List<String[]> dd = records(file, "DD");
        assertThat(dd).hasSize(2).allSatisfy(f -> assertThat(f[14]).isEqualTo("1002"));
        assertThat(records(file, "CD")).hasSize(2);
        assertThat(records(file, "BH")).singleElement().satisfies(bh -> {
            assertThat(bh[51]).isEqualTo("N");
            assertThat(bh[8]).isEmpty();
        });
    }

    @Test
    @DisplayName("Only runs whose TDS was deducted inside the quarter count")
    void runsPaidOutsideTheQuarterAreExcluded() {
        TdsChallan c1 = challan("Q1", "9000", LocalDate.of(2026, 6, 7));
        PayRun marchPaidInMarch = run(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 31), PayRunStatus.PAID);
        PayRun marchPaidInApril = run(YearMonth.of(2026, 3), LocalDate.of(2026, 4, 2), PayRunStatus.PAID);
        PayRun junePaidInJuly = run(YearMonth.of(2026, 6), LocalDate.of(2026, 7, 1), PayRunStatus.PAID);
        PayRun mayDraft = run(YearMonth.of(2026, 5), null, PayRunStatus.COMPUTED);
        employee(marchPaidInMarch, "E100", VALID_PAN, "50000.00", "1000.00");
        UUID inQ1 = employee(marchPaidInApril, "E101", VALID_PAN, "50000.00", "1000.00");
        employee(junePaidInJuly, "E102", VALID_PAN, "50000.00", "1000.00");
        employee(mayDraft, "E103", VALID_PAN, "50000.00", "1000.00");
        allocate(c1, marchPaidInApril, inQ1, "1000");

        Annexure1PreviewResponse p = service.previewAnnexure1(FY, "Q1");

        assertThat(p.deductees()).singleElement().satisfies(d -> {
            assertThat(d.employeeNumber()).isEqualTo("E101");
            assertThat(d.paymentDate()).isEqualTo(LocalDate.of(2026, 4, 2));
        });
        assertThat(p.totalTdsDeducted()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Employees with no TDS on the run are not listed")
    void zeroTdsNotListed() {
        challan("Q1", "1000", LocalDate.of(2026, 6, 7));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        employee(may, "E200", VALID_PAN, "30000.00", "0.00");
        assertThat(service.previewAnnexure1(FY, "Q1").deductees()).isEmpty();
    }

    @Test
    @DisplayName("Before tax year 2026-27 there is one problem: use the CSV and Form 24Q software")
    void beforeForm138Year() {
        Annexure1PreviewResponse p = service.previewAnnexure1("2025-2026", "Q1");
        assertThat(p.portalFileProblems())
                .singleElement()
                .satisfies(s -> assertThat(s).contains("2025-2026").contains("CSV"));
        assertThatThrownBy(() -> service.exportAnnexure1("2025-2026", "Q1", "TXT"))
                .isInstanceOf(Form24QValidationException.class);
    }

    @Test
    @DisplayName("Q4 says the Form 138 Q4 layout is not yet published")
    void q4LayoutUnpublished() {
        markFiled("Q1", "111111111111111");
        markFiled("Q2", "222222222222222");
        markFiled("Q3", "333333333333333");
        Annexure1PreviewResponse p = service.previewAnnexure1(FY, "Q4");
        assertThat(p.portalFileProblems()).anyMatch(s -> s.contains("Q4") && s.contains("layout"));
    }

    @Test
    @DisplayName("Q2 without an earlier quarter marked filed is a problem; with one, the file quotes its token")
    void laterQuarterNeedsAnEarlierFiledQuarter() {
        TdsChallan c = challan("Q2", "1000", LocalDate.of(2026, 9, 7));
        PayRun aug = run(YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31), PayRunStatus.PAID);
        UUID e = employee(aug, "E300", VALID_PAN, "60000.00", "1000.00");
        allocate(c, aug, e, "1000");

        assertThat(service.previewAnnexure1(FY, "Q2").portalFileProblems())
                .anyMatch(s -> s.contains("Mark Q1 2026-2027 filed"));

        markFiled("Q1", "123456789012345");

        assertThat(service.previewAnnexure1(FY, "Q2").portalFileProblems()).isEmpty();
        byte[] file = service.exportAnnexure1(FY, "Q2", "TXT");
        assertThat(records(file, "BH")).singleElement().satisfies(bh -> {
            assertThat(bh[51]).isEqualTo("Y");
            assertThat(bh[8]).isEqualTo("123456789012345");
            assertThat(bh[17]).isEqualTo("Q2");
        });
    }

    @Test
    @DisplayName("TDS with no gross pay on the run is a problem: the portal needs an amount paid above 0")
    void zeroGrossIsAProblem() {
        TdsChallan c = challan("Q1", "1000", LocalDate.of(2026, 6, 7));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        UUID e = employee(may, "E400", VALID_PAN, "0.00", "1000.00");
        allocate(c, may, e, "1000");
        assertThat(service.previewAnnexure1(FY, "Q1").portalFileProblems())
                .anyMatch(s -> s.contains("E400") && s.contains("no gross pay"));
    }

    @Test
    @DisplayName("Q1 of a later year quotes the previous year's Q4 token, and needs it marked filed")
    void q1QuotesLastYearsQ4() {
        String next = "2027-2028";
        TdsChallan c = challanIn(next, "Q1", "1000", LocalDate.of(2027, 6, 7));
        PayRun may = run(YearMonth.of(2027, 5), LocalDate.of(2027, 5, 31), PayRunStatus.PAID);
        UUID e = employee(may, "E500", VALID_PAN, "60000.00", "1000.00");
        allocate(c, may, e, "1000");
        assertThat(service.previewAnnexure1(next, "Q1").portalFileProblems())
                .anyMatch(s -> s.contains("Mark Q4 2026-2027 filed"));
        markFiledIn("2026-2027", "Q4", "998877665544332");
        assertThat(service.previewAnnexure1(next, "Q1").portalFileProblems()).isEmpty();
        assertThat(records(service.exportAnnexure1(next, "Q1", "TXT"), "BH"))
                .singleElement()
                .satisfies(bh -> assertThat(bh[8]).isEqualTo("998877665544332"));
    }

    @Test
    @DisplayName("Missing employer details from the resolver are listed as things to fill in")
    void resolverMissingItemsAreProblems() {
        Form138DeductorResolver.Resolution partial =
                new Form138DeductorResolver.Resolution(form138Deductor(), List.of("Deductor email"));
        when(deductorResolver.resolve()).thenReturn(partial);
        challan("Q1", "1000", LocalDate.of(2026, 6, 7));
        assertThat(service.previewAnnexure1(FY, "Q1").portalFileProblems())
                .contains("Fill in: Deductor email")
                .anyMatch(s -> s.contains("No employee had TDS"));
    }

    @Test
    @DisplayName("No challans recorded is a problem")
    void noChallansIsAProblem() {
        assertThat(service.previewAnnexure1(FY, "Q1").portalFileProblems()).anyMatch(s -> s.contains("challans"));
    }

    @Test
    @DisplayName("TXT export with any portal problem is refused")
    void txtWithProblemsRefused() {
        TdsChallan c1 = challan("Q1", "3000", LocalDate.of(2026, 6, 7));
        PayRun may = run(YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31), PayRunStatus.PAID);
        UUID e = employee(may, "E001", VALID_PAN, "100000.00", "5000.00");
        allocate(c1, may, e, "3000");

        assertThatThrownBy(() -> service.exportAnnexure1(FY, "Q1", "TXT"))
                .isInstanceOf(Form24QValidationException.class)
                .hasMessageContaining("2000.00");
        assertThat(service.exportAnnexure1(FY, "Q1", "CSV")).isNotEmpty();
    }
}
