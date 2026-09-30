package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.lop.LopPolicy;
import com.infinevo.core.lop.LopPolicyService;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasis;
import com.infinevo.core.lop.WorkingDayBasisCalculator;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.payinput.PayInputListResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.component.BenefitRepository;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * W-29.4 §7 — resume by attempt: five rows, two already at attempt 2. {@code compute(id, 2, …)} computes
 * the other three, reports {@code 5/5}, and never touches the two. A message for an attempt the run no
 * longer expects is refused.
 */
class PayRunResumeTest {

    private static final UUID TENANT = StructureFixtures.TENANT;
    private static final UUID RUN = UUID.randomUUID();
    private static final UUID POLICY = UUID.randomUUID();
    private static final YearMonth JULY = StructureFixtures.JULY;

    private PayRunRepository payRuns;
    private EmployeePayRunRepository employeePayRuns;
    private EmployeePayRunLineRepository lines;
    private EmployeeSalaryService salaryService;
    private PayRunComputationServiceImpl service;
    private PayRun run;
    private final List<EmployeePayRun> rows = new ArrayList<>();
    private final Map<UUID, EmployeePayRun> rowsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        payRuns = mock(PayRunRepository.class);
        employeePayRuns = mock(EmployeePayRunRepository.class);
        lines = mock(EmployeePayRunLineRepository.class);
        salaryService = mock(EmployeeSalaryService.class);
        EmployeeService employeeService = mock(EmployeeService.class);
        WorkingDayBasisCalculator basisCalculator = mock(WorkingDayBasisCalculator.class);
        LopPolicyService lopPolicyService = mock(LopPolicyService.class);
        PayInputService payInputService = mock(PayInputService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());

        service = new PayRunComputationServiceImpl(
                payRuns,
                employeePayRuns,
                lines,
                salaryService,
                employeeService,
                basisCalculator,
                lopPolicyService,
                payInputService,
                mock(EarningRepository.class),
                mock(BenefitRepository.class),
                List.of(),
                transactionManager);
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.unwrap(Session.class)).thenReturn(mock(Session.class));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        run = new PayRun(
                TENANT, JULY, JULY.atDay(1), JULY.atEndOfMonth(), JULY.atDay(25), JULY.atEndOfMonth(), 5, 0, "officer");
        ReflectionTestUtils.setField(run, "id", RUN);
        run.lock("officer", Instant.now());
        run.startComputing("officer");
        run.beginAttempt("payrun-" + RUN + "-", 0, Instant.now());
        run.resumeComputing("officer");
        run.beginAttempt("payrun-" + RUN + "-", 2, Instant.now());
        when(payRuns.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(run));
        when(payRuns.findForUpdate(RUN, TENANT)).thenReturn(Optional.of(run));
        when(payRuns.saveAndFlush(any(PayRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<EmployeeResponse> employees = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID employeeId = UUID.randomUUID();
            EmployeePayRun row = new EmployeePayRun(
                    TENANT,
                    RUN,
                    new InclusionDecision(employeeId, InclusionStatus.INCLUDED, null, UUID.randomUUID()),
                    "officer");
            ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
            if (i < 2) {
                // Finished by attempt 1 and carried forward to attempt 2 when the officer resumed.
                row.recordComputation(PayRunTotals.of(List.of()), days(), 0, stamp(), 2, "officer", Instant.now());
            }
            rows.add(row);
            rowsById.put(row.getId(), row);
            employees.add(employee(employeeId));
        }
        when(employeePayRuns.findAllByTenantIdAndPayrunIdAndInclusionStatus(TENANT, RUN, InclusionStatus.INCLUDED))
                .thenReturn(rows);
        when(employeePayRuns.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(rowsById.get(invocation.<UUID>getArgument(0))));
        when(employeePayRuns.saveAndFlush(any(EmployeePayRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeService.listEmployedBetween(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(employees);
        when(salaryService.versionInForce(eq(TENANT), any(), eq(JULY.atEndOfMonth())))
                .thenReturn(StructureFixtures.version(List.of(), List.of(), List.of()));
        BigDecimal thirtyOne = new BigDecimal("31.00");
        when(basisCalculator.basisFor(eq(TENANT), eq(JULY), any()))
                .thenReturn(new WorkingDayBasisResponse(thirtyOne, thirtyOne, POLICY, LopRounding.HALF_UP_2));
        when(lopPolicyService.findPolicyInForceEntity(TENANT, JULY.atEndOfMonth()))
                .thenReturn(Optional.of(policy()));
        when(payInputService.forPeriod(JULY)).thenReturn(new PayInputListResponse(List.of(), Map.of(), Map.of()));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Attempt 2 computes the three rows not at it, reports 5/5, and leaves the two alone")
    void resumeComputesOnlyTheRest() {
        List<int[]> reports = new ArrayList<>();

        PayRunResponse result =
                service.compute(RUN, 2, "officer", (done, total) -> reports.add(new int[] {done, total}));

        assertThat(result.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0)).containsExactly(5, 5);
        assertThat(run.getProgressDone()).isEqualTo(5);
        assertThat(rows).allSatisfy(row -> assertThat(row.getComputedAttempt()).isEqualTo(2));
        for (EmployeePayRun carried : rows.subList(0, 2)) {
            verify(lines, never()).deleteByTenantIdAndEmployeePayrunId(TENANT, carried.getId());
            verify(salaryService, never()).versionInForce(TENANT, carried.getEmployeeId(), JULY.atEndOfMonth());
        }
        for (EmployeePayRun computed : rows.subList(2, 5)) {
            verify(lines).deleteByTenantIdAndEmployeePayrunId(TENANT, computed.getId());
            verify(salaryService).versionInForce(TENANT, computed.getEmployeeId(), JULY.atEndOfMonth());
        }
    }

    @Test
    @DisplayName("A message for attempt 1 while the run is at attempt 2 is superseded and writes nothing")
    void anOlderAttemptIsSuperseded() {
        assertThatThrownBy(() -> service.compute(RUN, 1, "officer", ProgressReporter.NONE))
                .isInstanceOf(SupersededPayRunJobException.class);

        verify(lines, never()).deleteByTenantIdAndEmployeePayrunId(any(), any());
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTING);
    }

    @Test
    @DisplayName("A slow worker overtaken by a newer attempt stops at its next report and never writes its progress")
    void anOvertakenWorkerStopsAtItsNextReport() {
        when(salaryService.versionInForce(eq(TENANT), any(), eq(JULY.atEndOfMonth())))
                .thenAnswer(invocation -> {
                    if (run.getComputeAttempt() == 2) {
                        // The officer judged attempt 2 abandoned and started attempt 3 meanwhile.
                        run.resumeComputing("officer");
                        run.beginAttempt("payrun-" + RUN + "-", 0, Instant.now());
                    }
                    return StructureFixtures.version(List.of(), List.of(), List.of());
                });
        List<Integer> reports = new ArrayList<>();

        assertThatThrownBy(() -> service.compute(RUN, 2, "officer", (done, total) -> reports.add(done)))
                .isInstanceOf(SupersededPayRunJobException.class);

        assertThat(reports).isEmpty();
        assertThat(run.getComputeAttempt()).isEqualTo(3);
        assertThat(run.getProgressDone()).isZero();
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTING);
    }

    private static LopPolicy policy() {
        LopPolicy policy = new LopPolicy(
                TENANT, WorkingDayBasis.ACTUAL_DAYS, null, true, true, LopRounding.HALF_UP_2, JULY.atDay(1));
        ReflectionTestUtils.setField(policy, "id", POLICY);
        return policy;
    }

    private static PolicyStamp stamp() {
        return new PolicyStamp(
                POLICY,
                WorkingDayBasis.ACTUAL_DAYS,
                new BigDecimal("31.00"),
                new BigDecimal("31.00"),
                LopRounding.HALF_UP_2);
    }

    private static PayRunDays days() {
        return PayRunDays.of(
                new BigDecimal("31.00"), BigDecimal.ZERO, JULY.atDay(1), JULY.atEndOfMonth(), JULY.atDay(1), null);
    }

    private static EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id,
                TENANT,
                "E-" + id.toString().substring(0, 4),
                "First",
                null,
                "Last",
                "MALE",
                LocalDate.of(2020, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
