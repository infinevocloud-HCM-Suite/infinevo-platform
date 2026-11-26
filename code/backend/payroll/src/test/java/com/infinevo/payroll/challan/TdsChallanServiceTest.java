package com.infinevo.payroll.challan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.challan.ChallanAllocationRequest.EmployeeAllocationItem;
import com.infinevo.payroll.challan.TdsChallanResponse.AllocationStatus;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TdsChallanServiceImpl} (W-89.1). Challans and allocations live in small in-memory stores
 * behind the repository mocks, so the service's own reads see its own writes.
 */
class TdsChallanServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String FY = "2026-2027";

    private TdsChallanRepository challanRepository;
    private TdsChallanAllocationRepository allocationRepository;
    private PayRunRepository payRunRepository;
    private EmployeePayRunRepository employeePayRunRepository;
    private EmployeePayRunLineRepository lineRepository;
    private EmployeeRepository employeeRepository;
    private TdsReturnRepository returnRepository;
    private TdsChallanServiceImpl service;

    private final Map<UUID, TdsChallan> challans = new HashMap<>();
    private final List<TdsChallanAllocation> allocations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        challanRepository = mock(TdsChallanRepository.class);
        allocationRepository = mock(TdsChallanAllocationRepository.class);
        payRunRepository = mock(PayRunRepository.class);
        employeePayRunRepository = mock(EmployeePayRunRepository.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        returnRepository = mock(TdsReturnRepository.class);
        service = new TdsChallanServiceImpl(
                challanRepository,
                allocationRepository,
                payRunRepository,
                employeePayRunRepository,
                lineRepository,
                employeeRepository,
                returnRepository);

        when(challanRepository.saveAndFlush(any(TdsChallan.class))).thenAnswer(inv -> {
            TdsChallan c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            challans.put(c.getId(), c);
            return c;
        });
        when(challanRepository.lockByTenantIdAndId(any(), any()))
                .thenAnswer(inv -> find(inv.getArgument(0), inv.getArgument(1)));
        when(challanRepository.findByTenantIdAndId(any(), any()))
                .thenAnswer(inv -> find(inv.getArgument(0), inv.getArgument(1)));
        when(challanRepository.findQuarterKey(any(), any()))
                .thenAnswer(inv -> find(inv.getArgument(0), inv.getArgument(1))
                        .map(c -> (TdsChallanRepository.QuarterKey) new TdsChallanRepository.QuarterKey() {
                            @Override
                            public String getFinancialYear() {
                                return c.getFinancialYear();
                            }

                            @Override
                            public String getQuarter() {
                                return c.getQuarter();
                            }
                        }));
        when(challanRepository.lockQuarter(any(), any(), any())).thenAnswer(inv -> challans.values().stream()
                .filter(c -> c.getTenantId().equals(inv.getArgument(0))
                        && c.getFinancialYear().equals(inv.getArgument(1))
                        && c.getQuarter().equals(inv.getArgument(2)))
                .sorted(java.util.Comparator.comparing(TdsChallan::getId))
                .toList());

        when(allocationRepository.save(any(TdsChallanAllocation.class))).thenAnswer(inv -> {
            TdsChallanAllocation a = inv.getArgument(0);
            if (allocations.stream().noneMatch(x -> x == a)) {
                a.setId(UUID.randomUUID());
                allocations.add(a);
            }
            return a;
        });
        org.mockito.Mockito.doAnswer(inv -> {
                    TdsChallanAllocation a = inv.getArgument(0);
                    allocations.removeIf(x -> x == a);
                    return null;
                })
                .when(allocationRepository)
                .delete(any(TdsChallanAllocation.class));
        when(allocationRepository.findByTenantIdAndChallanId(any(), any())).thenAnswer(inv -> allocations.stream()
                .filter(a -> a.getTenantId().equals(inv.getArgument(0)))
                .filter(a -> a.getChallanId().equals(inv.getArgument(1)))
                .toList());
        when(allocationRepository.findByTenantIdAndPayRunIdIn(any(), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> runIds = inv.getArgument(1);
                    return allocations.stream()
                            .filter(a -> a.getTenantId().equals(inv.getArgument(0)))
                            .filter(a -> runIds.contains(a.getPayRunId()))
                            .toList();
                });
        when(allocationRepository.findByTenantIdAndChallanIdIn(any(), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<?> ids = inv.getArgument(1);
                    return allocations.stream()
                            .filter(a -> ids.contains(a.getChallanId()))
                            .toList();
                });

        when(returnRepository.saveAndFlush(any(TdsReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Optional<TdsChallan> find(UUID tenantId, UUID id) {
        TdsChallan c = challans.get(id);
        return c != null && c.getTenantId().equals(tenantId) ? Optional.of(c) : Optional.empty();
    }

    // ---------------------------------------------------------------- fixtures

    private static TdsChallanRequest request(String fy, String quarter, String bsr, String serial, String tax) {
        return new TdsChallanRequest(
                fy,
                quarter,
                bsr,
                serial,
                LocalDate.of(2026, 7, 7),
                tax == null ? null : new BigDecimal(tax),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                null);
    }

    private static TdsChallanRequest valid() {
        return request(FY, "Q1", "0210045", "12345", "1000");
    }

    /** A challan already recorded: tax, surcharge, cess, interest in whole rupees. */
    private TdsChallan stored(String quarter, String tax, String interest) {
        TdsChallan c = new TdsChallan();
        c.setId(UUID.randomUUID());
        c.setTenantId(TENANT_ID);
        c.setFinancialYear(FY);
        c.setQuarter(quarter);
        c.setBsrCode("0210045");
        c.setChallanSerialNo("00042");
        c.setTenderDate(LocalDate.of(2026, 6, 7));
        c.setTaxAmount(new BigDecimal(tax));
        c.setSurchargeAmount(BigDecimal.ZERO.setScale(2));
        c.setCessAmount(BigDecimal.ZERO.setScale(2));
        c.setInterestAmount(new BigDecimal(interest));
        c.setFeePenaltyAmount(BigDecimal.ZERO.setScale(2));
        c.setTotalAmount(c.allocatable().add(c.getInterestAmount()));
        challans.put(c.getId(), c);
        return c;
    }

    private PayRun run(PayRunStatus status, YearMonth period, LocalDate paidOn) {
        UUID id = UUID.randomUUID();
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getTenantId()).thenReturn(TENANT_ID);
        when(run.getStatus()).thenReturn(status);
        when(run.getPeriod()).thenReturn(period);
        when(run.getPaidOn()).thenReturn(paidOn);
        when(payRunRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(run));
        return run;
    }

    /** Puts employees on the run as INCLUDED with the given TDS each. */
    private void included(PayRun run, Map<UUID, String> tdsByEmployee) {
        List<EmployeePayRun> eprs = new ArrayList<>();
        List<EmployeePayRunLine> lines = new ArrayList<>();
        UUID runId = run.getId();
        for (Map.Entry<UUID, String> e : tdsByEmployee.entrySet()) {
            UUID eprId = UUID.randomUUID();
            EmployeePayRun epr = mock(EmployeePayRun.class);
            when(epr.getId()).thenReturn(eprId);
            when(epr.getEmployeeId()).thenReturn(e.getKey());
            when(epr.getPayrunId()).thenReturn(runId);
            eprs.add(epr);
            EmployeePayRunLine line = mock(EmployeePayRunLine.class);
            when(line.getEmployeePayrunId()).thenReturn(eprId);
            when(line.getPayrunId()).thenReturn(runId);
            when(line.getAmount()).thenReturn(new BigDecimal(e.getValue()));
            lines.add(line);
        }
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, run.getId(), InclusionStatus.INCLUDED))
                .thenReturn(eprs);
        when(lineRepository.findTaxLinesByPayrunIds(TENANT_ID, List.of(run.getId())))
                .thenReturn(lines);
    }

    private static ChallanAllocationRequest alloc(PayRun run, UUID employeeId, String tax) {
        return new ChallanAllocationRequest(
                run.getId(),
                List.of(new EmployeeAllocationItem(employeeId, new BigDecimal(tax), BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private void markQuarterFiled(String quarter) {
        when(returnRepository.existsByTenantIdAndFinancialYearAndQuarter(TENANT_ID, FY, quarter))
                .thenReturn(true);
    }

    // ---------------------------------------------------------------- create

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Total is tax + surcharge + cess + interest + fee; allocatable leaves out interest and fee")
        void totals() {
            TdsChallanRequest req = new TdsChallanRequest(
                    FY,
                    "Q1",
                    "0210045",
                    "12345",
                    LocalDate.of(2026, 7, 7),
                    new BigDecimal("1000"),
                    new BigDecimal("100"),
                    new BigDecimal("44"),
                    new BigDecimal("50"),
                    new BigDecimal("200"),
                    null,
                    null);

            TdsChallanResponse r = service.create(req);

            assertThat(r.totalAmount()).isEqualByComparingTo("1394");
            assertThat(r.allocatableAmount()).isEqualByComparingTo("1144");
            assertThat(r.allocatedAmount()).isEqualByComparingTo("0");
            assertThat(r.unallocatedAmount()).isEqualByComparingTo("1144");
            assertThat(r.allocationStatus()).isEqualTo(AllocationStatus.UNALLOCATED);
            assertThat(r.minorHead()).isEqualTo("200");
            assertThat(r.filed()).isFalse();
            assertThat(challans.get(r.id()).getTenantId()).isEqualTo(TENANT_ID);
        }

        @Test
        void rejectsNegative() {
            assertThatThrownBy(() -> service.create(request(FY, "Q1", "0210045", "12345", "-1")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void rejectsMoreThanTwoDecimals() {
            assertThatThrownBy(() -> service.create(request(FY, "Q1", "0210045", "12345", "100.555")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("two decimals");
        }

        @Test
        @DisplayName("Challan amounts are whole rupees: 100.50 is refused, 100.00 accepted")
        void rejectsPaise() {
            assertThatThrownBy(() -> service.create(request(FY, "Q1", "0210045", "12345", "100.50")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("whole rupees");
            assertThat(service.create(request(FY, "Q1", "0210045", "12345", "100.00"))
                            .taxAmount())
                    .isEqualByComparingTo("100");
        }

        @Test
        void rejectsBadBsrCode() {
            for (String bsr : new String[] {"021004", "02100450", "02100A5", null}) {
                assertThatThrownBy(() -> service.create(request(FY, "Q1", bsr, "12345", "1000")))
                        .as("BSR %s", bsr)
                        .isInstanceOf(TdsChallanValidationException.class)
                        .hasMessageContaining("BSR");
            }
        }

        @Test
        void rejectsBadSerial() {
            for (String serial : new String[] {"123456", "12a", "", null}) {
                assertThatThrownBy(() -> service.create(request(FY, "Q1", "0210045", serial, "1000")))
                        .as("serial %s", serial)
                        .isInstanceOf(TdsChallanValidationException.class)
                        .hasMessageContaining("serial");
            }
        }

        @Test
        void rejectsBadFinancialYearOrQuarter() {
            for (String[] fq :
                    new String[][] {{"2026-27", "Q1"}, {"2026-2028", "Q1"}, {FY, "Q5"}, {FY, "q1"}, {null, "Q1"}}) {
                assertThatThrownBy(() -> service.create(request(fq[0], fq[1], "0210045", "12345", "1000")))
                        .as("%s %s", fq[0], fq[1])
                        .isInstanceOf(TdsChallanValidationException.class);
            }
            verify(challanRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsDuplicate() {
            when(challanRepository.existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDate(
                            TENANT_ID, "0210045", "12345", LocalDate.of(2026, 7, 7)))
                    .thenReturn(true);
            assertThatThrownBy(() -> service.create(valid()))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("already exists");
            verify(challanRepository, never()).saveAndFlush(any());
        }

        @Test
        void refusedInAFiledQuarter() {
            markQuarterFiled("Q1");
            assertThatThrownBy(() -> service.create(valid()))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("filed");
            verify(challanRepository, never()).saveAndFlush(any());
        }
    }

    // ---------------------------------------------------------------- update / delete in a filed quarter

    @Test
    void updateRefusedInAFiledQuarter() {
        TdsChallan c = stored("Q1", "1000", "0");
        markQuarterFiled("Q1");
        assertThatThrownBy(() -> service.update(c.getId(), valid()))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("filed");
        verify(challanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A challan cannot be moved into a filed quarter")
    void updateRefusedIntoAFiledQuarter() {
        TdsChallan c = stored("Q2", "1000", "0");
        markQuarterFiled("Q1");
        assertThatThrownBy(() -> service.update(c.getId(), valid()))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("filed");
    }

    @Test
    void deleteRefusedInAFiledQuarter() {
        TdsChallan c = stored("Q1", "1000", "0");
        markQuarterFiled("Q1");
        assertThatThrownBy(() -> service.delete(c.getId()))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("filed");
        verify(challanRepository, never()).delete(any(TdsChallan.class));
        verify(allocationRepository, never()).deleteByTenantIdAndChallanId(any(), any());
    }

    @Test
    void deleteRemovesItsAllocations() {
        TdsChallan c = stored("Q1", "1000", "0");
        service.delete(c.getId());
        verify(allocationRepository).deleteByTenantIdAndChallanId(TENANT_ID, c.getId());
        verify(challanRepository).delete(c);
    }

    @Test
    @DisplayName("Another tenant's challan is not found")
    void otherTenantsChallanIsNotFound() {
        TdsChallan c = stored("Q1", "1000", "0");
        c.setTenantId(OTHER_TENANT);
        assertThatThrownBy(() -> service.delete(c.getId())).isInstanceOf(TdsChallanNotFoundException.class);
        assertThat(service.findById(c.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- allocate

    @Nested
    @DisplayName("allocate")
    class Allocate {

        private final UUID emp = UUID.randomUUID();

        @Test
        void nullRunIsAValidationError() {
            TdsChallan c = stored("Q1", "1000", "0");
            assertThatThrownBy(() -> service.allocate(c.getId(), new ChallanAllocationRequest(null, List.of())))
                    .isInstanceOf(TdsChallanValidationException.class);
            assertThatThrownBy(() -> service.allocate(c.getId(), null))
                    .isInstanceOf(TdsChallanValidationException.class);
        }

        @Test
        @DisplayName("A run of another tenant, or no run at all, is not found")
        void runOfAnotherTenantIsNotFound() {
            TdsChallan c = stored("Q1", "1000", "0");
            UUID runId = UUID.randomUUID();
            PayRun foreign = mock(PayRun.class);
            when(foreign.getId()).thenReturn(runId);
            when(foreign.getStatus()).thenReturn(PayRunStatus.PAID);
            when(payRunRepository.findByIdAndTenantId(runId, OTHER_TENANT)).thenReturn(Optional.of(foreign));

            ChallanAllocationRequest req = new ChallanAllocationRequest(
                    runId, List.of(new EmployeeAllocationItem(emp, new BigDecimal("100"), null, null)));

            assertThatThrownBy(() -> service.allocate(c.getId(), req))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("Pay run not found");
            verify(payRunRepository).findByIdAndTenantId(runId, TENANT_ID);
            assertThat(allocations).isEmpty();
        }

        @Test
        void draftOrComputedRunRefused() {
            TdsChallan c = stored("Q1", "1000", "0");
            for (PayRunStatus status : new PayRunStatus[] {PayRunStatus.DRAFT, PayRunStatus.COMPUTED}) {
                PayRun run = run(status, YearMonth.of(2026, 5), null);
                included(run, Map.of(emp, "500"));
                assertThatThrownBy(() -> service.allocate(c.getId(), alloc(run, emp, "100")))
                        .as(status.name())
                        .isInstanceOf(TdsChallanValidationException.class)
                        .hasMessageContaining(status.name());
            }
            assertThat(allocations).isEmpty();
        }

        @Test
        @DisplayName("A June run paid on 1 July is Q2, so a Q1 challan cannot cover it")
        void runInAnotherQuarterRefused() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 6), LocalDate.of(2026, 7, 1));
            included(run, Map.of(emp, "500"));
            assertThatThrownBy(() -> service.allocate(c.getId(), alloc(run, emp, "100")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("Q2");
            assertThat(allocations).isEmpty();
        }

        @Test
        @DisplayName("A March run paid on 2 April can be covered by a Q1 challan")
        void marchRunPaidInAprilIsQ1() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 3), LocalDate.of(2026, 4, 2));
            included(run, Map.of(emp, "500"));
            TdsChallanResponse r = service.allocate(c.getId(), alloc(run, emp, "500"));
            assertThat(r.allocatedAmount()).isEqualByComparingTo("500");
        }

        @Test
        void employeeNotIncludedRefused() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31));
            included(run, Map.of(emp, "500"));
            UUID stranger = UUID.randomUUID();
            assertThatThrownBy(() -> service.allocate(c.getId(), alloc(run, stranger, "100")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("not in this pay run");
            assertThat(allocations).isEmpty();
        }

        @Test
        @DisplayName("An approved but unpaid run is refused: TDS is deducted when salary is paid")
        void approvedUnpaidRunRefused() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.APPROVED, YearMonth.of(2026, 5), null);
            included(run, Map.of(emp, "500"));
            assertThatThrownBy(() -> service.allocate(c.getId(), alloc(run, emp, "100")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("paid");
            assertThat(allocations).isEmpty();
        }

        @Test
        @DisplayName("Cannot cover more than the employee's TDS on the run less what other challans cover")
        void aboveRemainingTdsRefused() {
            TdsChallan first = stored("Q1", "1000", "0");
            TdsChallan second = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31));
            included(run, Map.of(emp, "800"));

            service.allocate(first.getId(), alloc(run, emp, "500"));

            assertThatThrownBy(() -> service.allocate(second.getId(), alloc(run, emp, "300.01")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("300.00");
            TdsChallanResponse ok = service.allocate(second.getId(), alloc(run, emp, "300"));
            assertThat(ok.allocatedAmount()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("Cannot allocate more than the challan's tax + surcharge + cess")
        void challanCapRefused() {
            TdsChallan c = stored("Q1", "1000", "500");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), null);
            UUID other = UUID.randomUUID();
            Map<UUID, String> tds = new HashMap<>();
            tds.put(emp, "700");
            tds.put(other, "700");
            included(run, tds);

            service.allocate(c.getId(), alloc(run, emp, "700"));

            // 700 + 301 = 1001 > 1000 allocatable; the 500 interest does not count towards it.
            assertThatThrownBy(() -> service.allocate(c.getId(), alloc(run, other, "301")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("1000");
        }

        @Test
        @DisplayName("A second allocation for the same employee replaces the first; zero removes it")
        void upsertAndRemove() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), null);
            included(run, Map.of(emp, "900"));

            service.allocate(c.getId(), alloc(run, emp, "400"));
            TdsChallanResponse second = service.allocate(c.getId(), alloc(run, emp, "600"));

            assertThat(allocations).hasSize(1);
            assertThat(allocations.get(0).getAllocatedTax()).isEqualByComparingTo("600");
            assertThat(second.allocatedAmount()).isEqualByComparingTo("600");

            TdsChallanResponse removed = service.allocate(c.getId(), alloc(run, emp, "0"));
            assertThat(allocations).isEmpty();
            assertThat(removed.allocatedAmount()).isEqualByComparingTo("0");
        }

        @Test
        void sameEmployeeTwiceInOneRequestRefused() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), null);
            included(run, Map.of(emp, "900"));
            ChallanAllocationRequest req = new ChallanAllocationRequest(
                    run.getId(),
                    List.of(
                            new EmployeeAllocationItem(emp, new BigDecimal("100"), null, null),
                            new EmployeeAllocationItem(emp, new BigDecimal("100"), null, null)));
            assertThatThrownBy(() -> service.allocate(c.getId(), req))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("twice");
        }

        @Test
        @DisplayName("Status: UNALLOCATED, PARTIAL, FULL — interest is paid on the challan but never allocated")
        void allocationStatus() {
            TdsChallan c = stored("Q1", "1000", "250");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), null);
            included(run, Map.of(emp, "1000"));

            assertThat(service.findById(c.getId()).orElseThrow().allocationStatus())
                    .isEqualTo(AllocationStatus.UNALLOCATED);

            TdsChallanResponse partial = service.allocate(c.getId(), alloc(run, emp, "400"));
            assertThat(partial.allocationStatus()).isEqualTo(AllocationStatus.PARTIAL);
            assertThat(partial.unallocatedAmount()).isEqualByComparingTo("600");

            TdsChallanResponse full = service.allocate(c.getId(), alloc(run, emp, "1000"));
            assertThat(full.allocationStatus()).isEqualTo(AllocationStatus.FULL);
            assertThat(full.totalAmount()).isEqualByComparingTo("1250");
            assertThat(full.unallocatedAmount()).isEqualByComparingTo("0");

            List<TdsChallan> quarterChallans = List.of(c);
            when(challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(TENANT_ID, FY, "Q1"))
                    .thenReturn(quarterChallans);
            assertThat(service.list(FY, "q1"))
                    .singleElement()
                    .extracting(TdsChallanResponse::allocationStatus)
                    .isEqualTo(AllocationStatus.FULL);
        }

        @Test
        void refusedInAFiledQuarter() {
            TdsChallan c = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), null);
            included(run, Map.of(emp, "1000"));
            markQuarterFiled("Q1");
            assertThatThrownBy(() -> service.allocate(c.getId(), alloc(run, emp, "100")))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("filed");
            assertThat(allocations).isEmpty();
        }

        @Test
        @DisplayName("Candidates show the TDS, what other challans cover, and what is left")
        void candidates() {
            TdsChallan here = stored("Q1", "1000", "0");
            TdsChallan elsewhere = stored("Q1", "1000", "0");
            PayRun run = run(PayRunStatus.PAID, YearMonth.of(2026, 5), LocalDate.of(2026, 5, 31));
            when(run.getRunType()).thenReturn(com.infinevo.payroll.payrun.PayRunType.REGULAR);
            included(run, Map.of(emp, "900"));
            service.allocate(here.getId(), alloc(run, emp, "200"));
            service.allocate(elsewhere.getId(), alloc(run, emp, "300"));

            PayRun q2Run = mock(PayRun.class);
            when(q2Run.getId()).thenReturn(UUID.randomUUID());
            when(q2Run.getPeriod()).thenReturn(YearMonth.of(2026, 6));
            when(q2Run.getPaidOn()).thenReturn(LocalDate.of(2026, 7, 1));
            List<PayRun> runs = List.of(run, q2Run);
            when(payRunRepository.findByTenantIdAndPeriodInAndStatusIn(eq(TENANT_ID), anyCollection(), anyCollection()))
                    .thenReturn(runs);
            List<EmployeePayRun> eprs = employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                    TENANT_ID, run.getId(), InclusionStatus.INCLUDED);
            when(employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                            TENANT_ID, List.of(run.getId()), InclusionStatus.INCLUDED))
                    .thenReturn(eprs);

            List<AllocationCandidate> out = service.candidates(here.getId());

            assertThat(out).singleElement().satisfies(a -> {
                assertThat(a.employeeId()).isEqualTo(emp);
                assertThat(a.period()).isEqualTo("2026-05");
                assertThat(a.tdsDeducted()).isEqualByComparingTo("900");
                assertThat(a.allocatedHere()).isEqualByComparingTo("200");
                assertThat(a.allocatedElsewhere()).isEqualByComparingTo("300");
                assertThat(a.remaining()).isEqualByComparingTo("400");
            });
        }
    }

    // ---------------------------------------------------------------- mark filed

    @Nested
    @DisplayName("markFiled")
    class MarkFiled {

        @Test
        void savesTheToken() {
            LocalDate filedOn = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(3);
            TdsReturnResponse r = service.markFiled(FY, "q2", new TdsReturnRequest(" 123456789012345 ", filedOn));
            assertThat(r.financialYear()).isEqualTo(FY);
            assertThat(r.quarter()).isEqualTo("Q2");
            assertThat(r.tokenNumber()).isEqualTo("123456789012345");
            assertThat(r.filedOn()).isEqualTo(filedOn);
        }

        @Test
        void rejectsTokenThatIsNotFifteenDigits() {
            for (String token : new String[] {"12345678901234", "1234567890123456", "12345678901234A", null}) {
                assertThatThrownBy(() -> service.markFiled(FY, "Q1", new TdsReturnRequest(token, null)))
                        .as("token %s", token)
                        .isInstanceOf(TdsChallanValidationException.class)
                        .hasMessageContaining("15 digits");
            }
            verify(returnRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsFutureDate() {
            LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
            assertThatThrownBy(() -> service.markFiled(FY, "Q1", new TdsReturnRequest("123456789012345", tomorrow)))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("future");
        }

        @Test
        void rejectsAlreadyFiled() {
            markQuarterFiled("Q1");
            assertThatThrownBy(() -> service.markFiled(FY, "Q1", new TdsReturnRequest("123456789012345", null)))
                    .isInstanceOf(TdsChallanValidationException.class)
                    .hasMessageContaining("already");
            verify(returnRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsBadQuarter() {
            assertThatThrownBy(() -> service.markFiled(FY, "Q7", new TdsReturnRequest("123456789012345", null)))
                    .isInstanceOf(TdsChallanValidationException.class);
            assertThatThrownBy(() -> service.reopen("2026", "Q1")).isInstanceOf(TdsChallanValidationException.class);
        }

        @Test
        void reopenDeletesTheFiledQuarter() {
            TdsReturn filed = new TdsReturn(
                    TENANT_ID, TdsQuarter.parse(FY, "Q1"), "123456789012345", LocalDate.of(2026, 7, 20), "x");
            when(returnRepository.findByTenantIdAndFinancialYearAndQuarter(TENANT_ID, FY, "Q1"))
                    .thenReturn(Optional.of(filed));
            service.reopen(FY, "q1");
            verify(returnRepository).delete(filed);
        }
    }

    @Test
    void returnsListsTheYearsFiledQuarters() {
        TdsReturn q1 =
                new TdsReturn(TENANT_ID, TdsQuarter.parse(FY, "Q1"), "123456789012345", LocalDate.of(2026, 7, 20), "x");
        when(returnRepository.findByTenantIdAndFinancialYearOrderByQuarterAsc(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(q1));
        assertThat(service.returns(FY))
                .singleElement()
                .extracting(TdsReturnResponse::tokenNumber)
                .isEqualTo("123456789012345");
        verify(returnRepository).findByTenantIdAndFinancialYearOrderByQuarterAsc(TENANT_ID, FY);
    }
}
