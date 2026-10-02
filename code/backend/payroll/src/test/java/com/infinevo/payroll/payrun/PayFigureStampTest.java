package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.lop.LopPolicy;
import com.infinevo.core.lop.LopPolicyService;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.NoLopPolicyException;
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
 * W-18.2 §7 — no figure without a stamp. A stamp needs every part; it must name the policy the
 * calculator used; a row refuses a figure without one and loses it on failure. In the computation, a
 * {@code NoLopPolicyException} fails that employee, not the run, and a tenant with no policy in force
 * gets failed rows, never a defaulted stamp.
 */
class PayFigureStampTest {

    private static final UUID TENANT = StructureFixtures.TENANT;
    private static final YearMonth JULY = StructureFixtures.JULY;
    private static final UUID RUN = UUID.randomUUID();
    private static final UUID POLICY = UUID.randomUUID();
    private static final BigDecimal THIRTY_ONE = new BigDecimal("31.00");

    private WorkingDayBasisCalculator basisCalculator;
    private LopPolicyService lopPolicyService;
    private PayRunComputationServiceImpl service;
    private PayRun run;
    private final List<EmployeePayRun> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        PayRunRepository payRuns = mock(PayRunRepository.class);
        EmployeePayRunRepository employeePayRuns = mock(EmployeePayRunRepository.class);
        EmployeeSalaryService salaryService = mock(EmployeeSalaryService.class);
        EmployeeService employeeService = mock(EmployeeService.class);
        basisCalculator = mock(WorkingDayBasisCalculator.class);
        lopPolicyService = mock(LopPolicyService.class);
        PayInputService payInputService = mock(PayInputService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());

        service = new PayRunComputationServiceImpl(
                payRuns,
                employeePayRuns,
                mock(EmployeePayRunLineRepository.class),
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
                TENANT, JULY, JULY.atDay(1), JULY.atEndOfMonth(), JULY.atDay(25), JULY.atEndOfMonth(), 2, 0, "officer");
        ReflectionTestUtils.setField(run, "id", RUN);
        run.lock("officer", Instant.now());
        run.startComputing("officer");
        run.beginAttempt("payrun-" + RUN + "-", 0, Instant.now());
        when(payRuns.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(run));
        when(payRuns.findForUpdate(RUN, TENANT)).thenReturn(Optional.of(run));
        when(payRuns.saveAndFlush(any(PayRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<UUID, EmployeePayRun> byId = new HashMap<>();
        List<EmployeeResponse> employees = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID employeeId = UUID.randomUUID();
            EmployeePayRun row = new EmployeePayRun(
                    TENANT,
                    RUN,
                    new InclusionDecision(employeeId, InclusionStatus.INCLUDED, null, UUID.randomUUID()),
                    "officer");
            ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
            rows.add(row);
            byId.put(row.getId(), row);
            employees.add(employee(employeeId));
        }
        when(employeePayRuns.findAllByTenantIdAndPayrunIdAndInclusionStatus(TENANT, RUN, InclusionStatus.INCLUDED))
                .thenReturn(rows);
        when(employeePayRuns.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(byId.get(invocation.<UUID>getArgument(0))));
        when(employeePayRuns.saveAndFlush(any(EmployeePayRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeService.listEmployedBetween(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(employees);
        when(salaryService.versionInForce(eq(TENANT), any(), eq(JULY.atEndOfMonth())))
                .thenReturn(StructureFixtures.version(List.of(), List.of(), List.of()));
        when(payInputService.forPeriod(JULY)).thenReturn(new PayInputListResponse(List.of(), Map.of(), Map.of()));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A stamp needs every part: no policy id, basis, divisor, payable days or rounding is refused")
    void aStampNeedsEveryPart() {
        BigDecimal d = THIRTY_ONE;
        assertThatThrownBy(() -> new PolicyStamp(null, WorkingDayBasis.ACTUAL_DAYS, d, d, LopRounding.HALF_UP_2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyStamp(POLICY, null, d, d, LopRounding.HALF_UP_2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyStamp(POLICY, WorkingDayBasis.ACTUAL_DAYS, null, d, LopRounding.HALF_UP_2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyStamp(POLICY, WorkingDayBasis.ACTUAL_DAYS, d, null, LopRounding.HALF_UP_2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyStamp(POLICY, WorkingDayBasis.ACTUAL_DAYS, d, d, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The stamp names the policy the calculator used, or it is not made")
    void theStampMustNameTheCalculatorsPolicy() {
        WorkingDayBasisResponse basis =
                new WorkingDayBasisResponse(THIRTY_ONE, THIRTY_ONE, UUID.randomUUID(), LopRounding.HALF_UP_2);

        assertThatThrownBy(() -> PolicyStamp.of(basis, policy(WorkingDayBasis.ACTUAL_DAYS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be stamped");
    }

    @Test
    @DisplayName("A row refuses a figure without a stamp, and loses its stamp when it fails")
    void aRowNeverHoldsAFigureWithoutItsStamp() {
        EmployeePayRun row = rows.get(0);
        PayRunDays days = PayRunDays.of(THIRTY_ONE, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() ->
                        row.recordComputation(PayRunTotals.of(List.of()), days, 0, null, 1, "officer", Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("never written without its policy");
        assertThat(row.getStamp()).isEmpty();

        PolicyStamp stamp = new PolicyStamp(
                POLICY, WorkingDayBasis.ORG_DAYS, new BigDecimal("26"), new BigDecimal("26"), LopRounding.HALF_UP_0);
        row.recordComputation(PayRunTotals.of(List.of()), days, 0, stamp, 1, "officer", Instant.now());
        assertThat(row.getStamp()).hasValueSatisfying(s -> {
            assertThat(s.policyId()).isEqualTo(POLICY);
            assertThat(s.workingDayBasis()).isEqualTo(WorkingDayBasis.ORG_DAYS);
            assertThat(s.divisor()).isEqualByComparingTo("26.00");
            assertThat(s.payableDays()).isEqualByComparingTo("26.00");
            assertThat(s.lopRounding()).isEqualTo(LopRounding.HALF_UP_0);
        });

        row.recordError("NoLopPolicyException: none", 2, "officer", Instant.now());
        assertThat(row.getStamp()).isEmpty();
    }

    @Test
    @DisplayName("NoLopPolicyException fails that employee, not the run: the other row is computed and stamped")
    void noPolicyForOneEmployeeFailsOnlyThatEmployee() {
        UUID failing = rows.get(1).getEmployeeId();
        when(lopPolicyService.findPolicyInForceEntity(TENANT, JULY.atEndOfMonth()))
                .thenReturn(Optional.of(policy(WorkingDayBasis.ACTUAL_DAYS)));
        when(basisCalculator.basisFor(eq(TENANT), eq(JULY), any())).thenAnswer(invocation -> {
            if (failing.equals(invocation.getArgument(2))) {
                throw new NoLopPolicyException("No working week for " + failing);
            }
            return new WorkingDayBasisResponse(THIRTY_ONE, THIRTY_ONE, POLICY, LopRounding.HALF_UP_2);
        });

        PayRunResponse result = service.compute(RUN, 1, "officer", ProgressReporter.NONE);

        assertThat(result.status()).isEqualTo(PayRunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("1 of 2 employees could not be computed");
        assertThat(rows.get(0).getComputationError()).isNull();
        assertThat(rows.get(0).getStamp()).hasValueSatisfying(s -> {
            assertThat(s.policyId()).isEqualTo(POLICY);
            assertThat(s.workingDayBasis()).isEqualTo(WorkingDayBasis.ACTUAL_DAYS);
        });
        assertThat(rows.get(1).getComputationError()).startsWith("NoLopPolicyException");
        assertThat(rows.get(1).getStamp()).isEmpty();
    }

    @Test
    @DisplayName("No policy in force: every employee fails with the reason; nothing is stamped by default")
    void noPolicyInForceIsNeverDefaulted() {
        when(lopPolicyService.findPolicyInForceEntity(TENANT, JULY.atEndOfMonth()))
                .thenReturn(Optional.empty());
        // Even a calculator that answered would not be enough: the stamp needs the policy version.
        when(basisCalculator.basisFor(eq(TENANT), eq(JULY), any()))
                .thenReturn(new WorkingDayBasisResponse(THIRTY_ONE, THIRTY_ONE, POLICY, LopRounding.HALF_UP_2));

        PayRunResponse result = service.compute(RUN, 1, "officer", ProgressReporter.NONE);

        assertThat(result.status()).isEqualTo(PayRunStatus.FAILED);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStamp()).isEmpty();
            assertThat(row.getComputationError()).contains("No loss-of-pay policy in force");
        });
    }

    private static LopPolicy policy(WorkingDayBasis basis) {
        LopPolicy policy = new LopPolicy(TENANT, basis, null, true, true, LopRounding.HALF_UP_2, JULY.atDay(1));
        ReflectionTestUtils.setField(policy, "id", POLICY);
        return policy;
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
