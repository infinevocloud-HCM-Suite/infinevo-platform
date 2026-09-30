package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputListResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test verifying that LOP writes to core.pay_input as LOP_DAYS with delta row semantics (W-16.4a, spec section 7).
 */
@SpringBootTest(classes = LeaveTestApp.class)
@ContextConfiguration(initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class LopToPayInputIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveConsumptionService leaveConsumptionService;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeavePolicyRepository leavePolicyRepository;

    @Autowired
    private LeaveAllocationRepository leaveAllocationRepository;

    @Autowired
    private LeaveMonthlyLopRepository leaveMonthlyLopRepository;

    @Autowired
    private PayInputService payInputService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    private static UUID employeeId;
    private UUID leaveTypeId;

    @BeforeAll
    static void setupSchema() throws Exception {
        LeaveTestSchema.apply();
        LeaveTestSchema.seedTenants();
        employeeId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-LOP-01", "Charlie", "charlie@a.test");
    }

    @AfterAll
    static void cleanup() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void setupData() {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        TenantContext.set(TENANT_A);

        LeaveTypeResponse tA = leaveTypeService.createLeaveType(
                TENANT_A,
                new LeaveTypeRequest(
                        "Loss Of Pay Test",
                        "LOPT",
                        true,
                        LeaveUnit.DAYS,
                        true,
                        LocalDate.now().minusYears(1),
                        null));
        leaveTypeId = tA.id();

        LeavePolicy policy = new LeavePolicy();
        policy.setTenantId(TENANT_A);
        policy.setLeaveTypeId(leaveTypeId);
        policy.setAnnualDays(new BigDecimal("5.00"));
        policy.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        policy.setExceedBalanceMode(ExceedBalanceMode.MARK_AS_LOP); // Key setting: markAsLOP
        policy = leavePolicyRepository.save(policy);

        // Give Charlie 2.00 days entitlement
        LeaveAllocation alloc = new LeaveAllocation();
        alloc.setTenantId(TENANT_A);
        alloc.setEmployeeId(employeeId);
        alloc.setLeaveTypeId(leaveTypeId);
        alloc.setPolicyId(policy.getId());
        alloc.setLeaveYear("2026");
        alloc.setYearStartDate(LocalDate.of(2026, 1, 1));
        alloc.setYearEndDate(LocalDate.of(2026, 12, 31));
        alloc.setEntitlementDays(new BigDecimal("2.00"));
        alloc.setAccruedDays(BigDecimal.ZERO);
        alloc.setCarriedForwardDays(BigDecimal.ZERO);
        alloc.setProRateFactor(BigDecimal.ONE);
        leaveAllocationRepository.save(alloc);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a derived LOP figure appears in core.pay_input with kind LOP_DAYS, matching period and sourceRef")
    void lopAppearsInPayInputWithDeltaSemantics() {
        TenantContext.set(TENANT_A);

        // Request 1: 5.00 days from 10 Apr to 14 Apr 2026 -> 3.00 excess days -> 3.00 LOP days in 2026-04
        LeaveRequest req1 = new LeaveRequest(
                TENANT_A,
                employeeId,
                leaveTypeId,
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 14),
                false,
                null,
                new BigDecimal("5.00"),
                "Trip",
                LeaveRequestStatus.APPROVED,
                null,
                false);
        req1 = leaveRequestRepository.save(req1);

        leaveConsumptionService.consume(req1);

        // Check Monthly LOP row
        List<LeaveMonthlyLop> lops1 =
                tx.execute(status -> leaveMonthlyLopRepository.findByTenantIdAndEmployeeIdAndPeriodOrderByCreatedAtAsc(
                        TENANT_A, employeeId, "2026-04"));
        assertThat(lops1).hasSize(1);
        LeaveMonthlyLop firstLop = lops1.get(0);
        assertThat(firstLop.getLopDays()).isEqualByComparingTo("3.00");
        assertThat(firstLop.getPayInputId()).isNotNull();

        // Check PayInputService
        PayInputListResponse payInputs1 = payInputService.forEmployee(employeeId, YearMonth.of(2026, 4));
        assertThat(payInputs1.rows()).hasSize(1);
        assertThat(payInputs1.rows().get(0).kind()).isEqualTo(PayInputKind.LOP_DAYS);
        assertThat(payInputs1.rows().get(0).quantity()).isEqualByComparingTo("3.00");
        assertThat(payInputs1.rows().get(0).sourceRef())
                .isEqualTo(firstLop.getId().toString());

        // Request 2 in same month: 2.00 days from 20 Apr to 21 Apr -> balance is now negative, so all 2.00 days are LOP
        LeaveRequest req2 = new LeaveRequest(
                TENANT_A,
                employeeId,
                leaveTypeId,
                LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 4, 21),
                false,
                null,
                new BigDecimal("2.00"),
                "Trip 2",
                LeaveRequestStatus.APPROVED,
                null,
                false);
        req2 = leaveRequestRepository.save(req2);

        leaveConsumptionService.consume(req2);

        // Check that a SECOND delta row exists in LeaveMonthlyLop, NOT an update
        List<LeaveMonthlyLop> lops2 =
                tx.execute(status -> leaveMonthlyLopRepository.findByTenantIdAndEmployeeIdAndPeriodOrderByCreatedAtAsc(
                        TENANT_A, employeeId, "2026-04"));
        assertThat(lops2).hasSize(2);
        LeaveMonthlyLop secondLop = lops2.get(1);
        assertThat(secondLop.getLopDays()).isEqualByComparingTo("2.00");

        // Month sum in LopResponse must be 5.00
        LopResponse lopResp = leaveConsumptionService.getLop(employeeId, YearMonth.of(2026, 4));
        assertThat(lopResp.totalLopDays()).isEqualByComparingTo("5.00");
        assertThat(lopResp.deltaRows()).hasSize(2);

        // Check that a SECOND row exists in core.pay_input (delta row semantics)
        PayInputListResponse payInputs2 = payInputService.forEmployee(employeeId, YearMonth.of(2026, 4));
        assertThat(payInputs2.rows()).hasSize(2);
        assertThat(payInputs2.quantityTotalsByKind().get(PayInputKind.LOP_DAYS)).isEqualByComparingTo("5.00");

        // Cancel req2 and assert pay input reversal (W-16.4a review item 22)
        leaveConsumptionService.cancel(req2, "Cancelled req2");

        LopResponse lopRespAfterCancel = leaveConsumptionService.getLop(employeeId, YearMonth.of(2026, 4));
        assertThat(lopRespAfterCancel.totalLopDays()).isEqualByComparingTo("3.00");

        PayInputListResponse payInputsAfterCancel = payInputService.forEmployee(employeeId, YearMonth.of(2026, 4));
        assertThat(payInputsAfterCancel.rows()).hasSize(3);
        assertThat(payInputsAfterCancel.quantityTotalsByKind().get(PayInputKind.LOP_DAYS))
                .isEqualByComparingTo("3.00");
    }
}
