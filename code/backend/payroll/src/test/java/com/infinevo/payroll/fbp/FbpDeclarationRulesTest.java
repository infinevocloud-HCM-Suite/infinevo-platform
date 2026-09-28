package com.infinevo.payroll.fbp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.payroll.salary.CtcStructure;
import com.infinevo.payroll.salary.CtcStructureRepository;
import com.infinevo.payroll.salary.EmployeeEarning;
import com.infinevo.payroll.salary.EmployeeEarningRepository;
import com.infinevo.payroll.salary.EmployeeReimbursement;
import com.infinevo.payroll.salary.EmployeeReimbursementRepository;
import com.infinevo.payroll.salary.SalaryConflictException;
import com.infinevo.payroll.salary.SalaryValidationException;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for FBP declaration rules, calculations and validations (W-27.2).
 */
class FbpDeclarationRulesTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID CTC_ID = UUID.randomUUID();

    private static final UUID EARNING_FBP_ID = UUID.randomUUID();
    private static final UUID REIMB_FBP_ID = UUID.randomUUID();
    private static final UUID NON_FBP_ID = UUID.randomUUID();

    private EmployeeFbpComponentRepository employeeFbpComponentRepository;
    private CtcStructureRepository ctcStructureRepository;
    private EmployeeEarningRepository employeeEarningRepository;
    private EmployeeReimbursementRepository employeeReimbursementRepository;
    private EarningRepository earningRepository;
    private ReimbursementRepository reimbursementRepository;
    private FbpPlanService fbpPlanService;
    private EmployeeService employeeService;

    private FbpDeclarationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        employeeFbpComponentRepository = mock(EmployeeFbpComponentRepository.class);
        ctcStructureRepository = mock(CtcStructureRepository.class);
        employeeEarningRepository = mock(EmployeeEarningRepository.class);
        employeeReimbursementRepository = mock(EmployeeReimbursementRepository.class);
        earningRepository = mock(EarningRepository.class);
        reimbursementRepository = mock(ReimbursementRepository.class);
        fbpPlanService = mock(FbpPlanService.class);
        employeeService = mock(EmployeeService.class);

        service = new FbpDeclarationServiceImpl(
                employeeFbpComponentRepository,
                ctcStructureRepository,
                employeeEarningRepository,
                employeeReimbursementRepository,
                earningRepository,
                reimbursementRepository,
                fbpPlanService,
                employeeService);

        EmployeeResponse emp = mock(EmployeeResponse.class);
        when(emp.id()).thenReturn(EMPLOYEE_ID);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp));

        CtcStructure ctc = mock(CtcStructure.class);
        when(ctc.getId()).thenReturn(CTC_ID);
        when(ctcStructureRepository
                        .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(ctc));

        when(fbpPlanService.isWindowOpen(any())).thenReturn(true);

        // FBP Earning: Line ceiling is 48000
        EmployeeEarning ee = new EmployeeEarning(TENANT_ID, EARNING_FBP_ID, ctc, "test");
        ee.setCalculationType(CalculationType.FLAT);
        ee.setValue(new BigDecimal("48000.0000"));
        ee.setMonthlyAmount(new BigDecimal("4000.0000"));
        ee.setAnnualAmount(new BigDecimal("48000.0000"));
        ee.setEnabled(true);
        ee.setEarningFrequency("MONTHLY");
        when(employeeEarningRepository.findAllByTenantIdAndCtcStructureId(TENANT_ID, CTC_ID))
                .thenReturn(List.of(ee));

        Earning earningDef = new Earning(TENANT_ID, "test");
        earningDef.setCode("FUEL");
        earningDef.setName("Fuel Allowance");
        earningDef.setFbpComponent(true);
        earningDef.setActive(true);
        when(earningRepository.findByIdAndTenantIdAndDeletedFalse(EARNING_FBP_ID, TENANT_ID))
                .thenReturn(Optional.of(earningDef));

        // FBP Reimbursement: Line ceiling is 24000
        EmployeeReimbursement er = new EmployeeReimbursement(TENANT_ID, REIMB_FBP_ID, ctc, "test");
        er.setCalculationType(CalculationType.FLAT);
        er.setValue(new BigDecimal("24000.0000"));
        er.setMonthlyAmount(new BigDecimal("2000.0000"));
        er.setAnnualAmount(new BigDecimal("24000.0000"));
        er.setEnabled(true);
        when(employeeReimbursementRepository.findAllByTenantIdAndCtcStructureId(TENANT_ID, CTC_ID))
                .thenReturn(List.of(er));

        Reimbursement reimbDef = new Reimbursement(TENANT_ID, "test");
        reimbDef.setCode("MEAL");
        reimbDef.setName("Meal Voucher");
        reimbDef.setFbpComponent(true);
        reimbDef.setActive(true);
        when(reimbursementRepository.findByIdAndTenantIdAndDeletedFalse(REIMB_FBP_ID, TENANT_ID))
                .thenReturn(Optional.of(reimbDef));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Line above the salary line ceiling is refused with ceiling in message")
    void lineAboveCeilingRefused() {
        FbpDeclarationRequest req = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", EARNING_FBP_ID, new BigDecimal("50000.0000"))));

        assertThatThrownBy(() -> service.declareOwn(req))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining("exceeds line ceiling 48000.0000");
    }

    @Test
    @DisplayName("Non-FBP component is refused")
    void nonFbpComponentRefused() {
        FbpDeclarationRequest req = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", NON_FBP_ID, new BigDecimal("10000.0000"))));

        assertThatThrownBy(() -> service.declareOwn(req))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining("is not an active FBP component in the employee's salary version");
    }

    @Test
    @DisplayName("Duplicate component lines are refused")
    void duplicateComponentRefused() {
        FbpDeclarationRequest req = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", EARNING_FBP_ID, new BigDecimal("20000.0000")),
                new FbpDeclarationLineRequest("EARNING", EARNING_FBP_ID, new BigDecimal("10000.0000"))));

        assertThatThrownBy(() -> service.declareOwn(req))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining("Duplicate declaration line");
    }

    @Test
    @DisplayName("Omitted line from request is stored as 0")
    void omittedLineStoredAsZero() {
        // Request only declares for EARNING_FBP_ID, omits REIMB_FBP_ID
        FbpDeclarationRequest req = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", EARNING_FBP_ID, new BigDecimal("36000.0000"))));

        service.declareOwn(req);

        ArgumentCaptor<EmployeeFbpComponent> captor = ArgumentCaptor.forClass(EmployeeFbpComponent.class);
        verify(employeeFbpComponentRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        List<EmployeeFbpComponent> saved = captor.getAllValues();
        EmployeeFbpComponent omittedReimb = saved.stream()
                .filter(c -> REIMB_FBP_ID.equals(c.getReimbursementId()))
                .findFirst()
                .orElseThrow();

        assertThat(omittedReimb.getAnnualAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(omittedReimb.getMonthlyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Monthly amount is derived as annual / 12 at scale 4 HALF_UP")
    void monthlyAmountCalculationScale4HalfUp() {
        // 10000 / 12 = 833.33333333... -> 833.3333
        BigDecimal annual = new BigDecimal("10000.0000");
        BigDecimal expectedMonthly =
                Money.of(annual).divide(BigDecimal.valueOf(12)).raw();
        assertThat(expectedMonthly).isEqualByComparingTo(new BigDecimal("833.3333"));

        FbpDeclarationRequest req =
                new FbpDeclarationRequest(List.of(new FbpDeclarationLineRequest("EARNING", EARNING_FBP_ID, annual)));

        service.declareOwn(req);

        ArgumentCaptor<EmployeeFbpComponent> captor = ArgumentCaptor.forClass(EmployeeFbpComponent.class);
        verify(employeeFbpComponentRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        EmployeeFbpComponent earningDecl = captor.getAllValues().stream()
                .filter(c -> EARNING_FBP_ID.equals(c.getEarningId()))
                .findFirst()
                .orElseThrow();

        assertThat(earningDecl.getMonthlyAmount()).isEqualByComparingTo("833.3333");
    }

    @Test
    @DisplayName("Window closed returns conflict for employee declaration")
    void windowClosedConflict() {
        when(fbpPlanService.isWindowOpen(any())).thenReturn(false);

        FbpDeclarationRequest req = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", EARNING_FBP_ID, new BigDecimal("10000.0000"))));

        assertThatThrownBy(() -> service.declareOwn(req))
                .isInstanceOf(SalaryConflictException.class)
                .hasMessageContaining("WINDOW_CLOSED");
    }

    @Test
    @DisplayName("carryForward caps at new line annual amount and zeroes new lines")
    void carryForwardCappingAndZeroing() {
        UUID oldVersionId = UUID.randomUUID();
        UUID newVersionId = UUID.randomUUID();

        // Old declaration: declared 30000 on EARNING_FBP_ID
        EmployeeFbpComponent oldDecl = new EmployeeFbpComponent(
                TENANT_ID,
                oldVersionId,
                EMPLOYEE_ID,
                EARNING_FBP_ID,
                null,
                new BigDecimal("30000.0000"),
                new BigDecimal("2500.0000"),
                java.time.Instant.now(),
                DeclaredBy.EMPLOYEE,
                "emp");
        when(employeeFbpComponentRepository.findAllByTenantIdAndCtcStructureId(TENANT_ID, oldVersionId))
                .thenReturn(List.of(oldDecl));

        // New version: EARNING_FBP_ID line has ceiling of 20000 (lower than 30000)
        // AND REIMB_FBP_ID is new to the version with ceiling 24000
        CtcStructure newCtc = mock(CtcStructure.class);
        when(newCtc.getId()).thenReturn(newVersionId);

        EmployeeEarning newEe = new EmployeeEarning(TENANT_ID, EARNING_FBP_ID, newCtc, "test");
        newEe.setCalculationType(CalculationType.FLAT);
        newEe.setValue(new BigDecimal("20000.0000"));
        newEe.setMonthlyAmount(new BigDecimal("1666.6667"));
        newEe.setAnnualAmount(new BigDecimal("20000.0000"));
        newEe.setEnabled(true);
        newEe.setEarningFrequency("MONTHLY");
        when(employeeEarningRepository.findAllByTenantIdAndCtcStructureId(TENANT_ID, newVersionId))
                .thenReturn(List.of(newEe));

        EmployeeReimbursement newEr = new EmployeeReimbursement(TENANT_ID, REIMB_FBP_ID, newCtc, "test");
        newEr.setCalculationType(CalculationType.FLAT);
        newEr.setValue(new BigDecimal("24000.0000"));
        newEr.setMonthlyAmount(new BigDecimal("2000.0000"));
        newEr.setAnnualAmount(new BigDecimal("24000.0000"));
        newEr.setEnabled(true);
        when(employeeReimbursementRepository.findAllByTenantIdAndCtcStructureId(TENANT_ID, newVersionId))
                .thenReturn(List.of(newEr));

        service.carryForward(oldVersionId, newVersionId, TENANT_ID, EMPLOYEE_ID);

        ArgumentCaptor<EmployeeFbpComponent> captor = ArgumentCaptor.forClass(EmployeeFbpComponent.class);
        verify(employeeFbpComponentRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        List<EmployeeFbpComponent> carried = captor.getAllValues();

        // 1. Capped line: min(30000, 20000) = 20000
        EmployeeFbpComponent carriedEarning = carried.stream()
                .filter(c -> EARNING_FBP_ID.equals(c.getEarningId()))
                .findFirst()
                .orElseThrow();
        assertThat(carriedEarning.getAnnualAmount()).isEqualByComparingTo("20000.0000");
        assertThat(carriedEarning.getDeclaredBy()).isEqualTo(DeclaredBy.CARRIED);

        // 2. New line: 0
        EmployeeFbpComponent carriedReimb = carried.stream()
                .filter(c -> REIMB_FBP_ID.equals(c.getReimbursementId()))
                .findFirst()
                .orElseThrow();
        assertThat(carriedReimb.getAnnualAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(carriedReimb.getDeclaredBy()).isEqualTo(DeclaredBy.CARRIED);
    }
}
