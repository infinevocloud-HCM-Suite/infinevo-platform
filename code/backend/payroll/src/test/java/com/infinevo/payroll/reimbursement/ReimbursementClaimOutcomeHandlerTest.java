package com.infinevo.payroll.reimbursement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ReimbursementClaimOutcomeHandler} (W-35.1, spec section 7).
 */
class ReimbursementClaimOutcomeHandlerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID REIMBURSEMENT_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final UUID DECIDER_ID = UUID.randomUUID();

    private ApprovalInstanceRepository instanceRepository;
    private ReimbursementClaimRepository claimRepository;
    private PayInputService payInputService;
    private ReimbursementClaimOutcomeHandler handler;

    private ApprovalInstance instance;
    private ReimbursementClaim claim;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        instanceRepository = mock(ApprovalInstanceRepository.class);
        claimRepository = mock(ReimbursementClaimRepository.class);
        payInputService = mock(PayInputService.class);
        handler = new ReimbursementClaimOutcomeHandler(instanceRepository, claimRepository, payInputService);

        instance = mock(ApprovalInstance.class);
        when(instance.getTenantId()).thenReturn(TENANT_ID);
        when(instance.getSubjectId()).thenReturn(CLAIM_ID);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));

        claim = new ReimbursementClaim(
                TENANT_ID,
                EMPLOYEE_ID,
                REIMBURSEMENT_ID,
                new BigDecimal("1500.00"),
                LocalDate.now(),
                "Travel expenses",
                null,
                "test");
        claim.setId(CLAIM_ID);
        when(claimRepository.findByTenantIdAndId(TENANT_ID, CLAIM_ID)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ReimbursementClaim.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("flowType() returns REIMBURSEMENT")
    void flowTypeIsReimbursement() {
        assertThat(handler.flowType()).isEqualTo(ApprovalFlowType.REIMBURSEMENT);
    }

    @Test
    @DisplayName(
            "onApproved calls record once with REIMBURSEMENT, positive Money, sourceRef = reimbursement_claim:{id}")
    void onApprovedCallsRecordOnce() {
        UUID payInputId = UUID.randomUUID();
        YearMonth period = YearMonth.now();
        PayInputResponse payInputResp = new PayInputResponse(
                payInputId,
                EMPLOYEE_ID,
                period,
                PayInputKind.REIMBURSEMENT,
                null,
                new BigDecimal("1200.00"),
                "payroll",
                "reimbursement_claim:" + CLAIM_ID,
                null,
                null,
                Instant.now());
        when(payInputService.record(any(PayInputCommand.class))).thenReturn(payInputResp);

        StepDecision decision = new StepDecision(
                UUID.randomUUID(), DECIDER_ID, "APPROVED", "Approved partial", new BigDecimal("1200.00"), null);

        handler.onApproved(INSTANCE_ID, List.of(decision));

        ArgumentCaptor<PayInputCommand> captor = ArgumentCaptor.forClass(PayInputCommand.class);
        verify(payInputService, times(1)).record(captor.capture());

        PayInputCommand cmd = captor.getValue();
        assertThat(cmd.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(cmd.kind()).isEqualTo(PayInputKind.REIMBURSEMENT);
        assertThat(cmd.amount().toAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(cmd.sourceModule()).isEqualTo("payroll");
        assertThat(cmd.sourceRef()).isEqualTo("reimbursement_claim:" + CLAIM_ID);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(claim.getPayInputId()).isEqualTo(payInputId);
        assertThat(claim.getPostedPeriod()).isEqualTo(period.toString());
        assertThat(claim.getApprovedBy()).isEqualTo(DECIDER_ID);
        assertThat(claim.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("A second onApproved on an APPROVED claim is idempotent and calls nothing")
    void secondOnApprovedCallsNothing() {
        claim.setStatus(ClaimStatus.APPROVED);

        StepDecision decision = new StepDecision(
                UUID.randomUUID(), DECIDER_ID, "APPROVED", "Approved again", new BigDecimal("1500.00"), null);

        handler.onApproved(INSTANCE_ID, List.of(decision));

        verify(payInputService, never()).record(any());
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("onRejected stores status REJECTED and the decision comment as remarks")
    void onRejectedStoresComment() {
        StepDecision decision = new StepDecision(
                UUID.randomUUID(), DECIDER_ID, "REJECTED", "Bills not legible. Please resubmit.", null, null);

        handler.onRejected(INSTANCE_ID, List.of(decision));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getRemarks()).isEqualTo("Bills not legible. Please resubmit.");
        verify(payInputService, never()).record(any());
        verify(claimRepository, times(1)).save(claim);
    }
}
