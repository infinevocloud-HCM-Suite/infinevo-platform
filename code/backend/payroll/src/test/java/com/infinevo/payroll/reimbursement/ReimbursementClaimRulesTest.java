package com.infinevo.payroll.reimbursement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
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
 * Unit tests verifying business rules for reimbursement claims (W-35.1, spec section 7).
 */
class ReimbursementClaimRulesTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID REIMBURSEMENT_ID = UUID.randomUUID();

    private ReimbursementClaimRepository claimRepository;
    private ReimbursementRepository reimbursementRepository;
    private ApprovalService approvalService;
    private EmployeeService employeeService;
    private DocumentService documentService;
    private ReimbursementClaimServiceImpl service;

    private ApprovalInstanceRepository instanceRepository;
    private PayInputService payInputService;
    private ReimbursementClaimOutcomeHandler outcomeHandler;

    private Reimbursement activeComponent;
    private EmployeeResponse employeeResponse;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        claimRepository = mock(ReimbursementClaimRepository.class);
        reimbursementRepository = mock(ReimbursementRepository.class);
        approvalService = mock(ApprovalService.class);
        employeeService = mock(EmployeeService.class);
        documentService = mock(DocumentService.class);

        service = new ReimbursementClaimServiceImpl(
                claimRepository, reimbursementRepository, approvalService, employeeService, documentService);

        instanceRepository = mock(ApprovalInstanceRepository.class);
        payInputService = mock(PayInputService.class);
        outcomeHandler = new ReimbursementClaimOutcomeHandler(instanceRepository, claimRepository, payInputService);

        employeeResponse = PayrollTestSchema.createTestEmployee(
                EMPLOYEE_ID, TENANT_ID, "EMP001", "Jane", "Doe", "jane@example.com");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(employeeResponse));

        activeComponent = new Reimbursement(TENANT_ID, "test");
        activeComponent.setCode("MED");
        activeComponent.setName("Medical Reimbursement");
        activeComponent.setActive(true);
        when(reimbursementRepository.findByIdAndTenantIdAndDeletedFalse(REIMBURSEMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(activeComponent));

        when(claimRepository.save(any(ReimbursementClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalService.start(any(ApprovalFlowType.class), any(SubjectRef.class), any(UUID.class)))
                .thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Zero requested amount is refused with 400")
    void zeroAmountRefused() {
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, BigDecimal.ZERO, LocalDate.now(), "Medical checkup", null);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("requested_amount must be greater than 0");
    }

    @Test
    @DisplayName("Negative requested amount is refused with 400")
    void negativeAmountRefused() {
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("-50.00"), LocalDate.now(), "Medical checkup", null);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("requested_amount must be greater than 0");
    }

    @Test
    @DisplayName("Amount with scale > 2 is refused with 400")
    void scaleGreaterThanTwoRefused() {
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("100.125"), LocalDate.now(), "Medical checkup", null);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("requested_amount scale must be at most 2");
    }

    @Test
    @DisplayName("Future bill date is refused with 400")
    void futureBillDateRefused() {
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("100.00"), LocalDate.now().plusDays(1), "Medical checkup", null);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("bill_date must not be after today");
    }

    @Test
    @DisplayName("Inactive reimbursement component is refused with 400")
    void inactiveComponentRefused() {
        activeComponent.setActive(false);

        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("100.00"), LocalDate.now(), "Medical checkup", null);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("Reimbursement component is not active");
    }

    @Test
    @DisplayName("Description over 500 characters is refused with 400")
    void descriptionOver500CharsRefused() {
        String longDesc = "a".repeat(501);
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("100.00"), LocalDate.now(), longDesc, null);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("description must be at most 500 characters");
    }

    @Test
    @DisplayName("Receipt document not owned by caller or wrong kind is refused with 400")
    void invalidDocumentRefused() {
        UUID docId = UUID.randomUUID();
        DocumentResponse wrongKindDoc = new DocumentResponse(
                docId,
                EMPLOYEE_ID,
                DocumentKind.EMPLOYEE_DOCUMENT,
                "policy.pdf",
                "application/pdf",
                100L,
                "chk",
                Instant.now(),
                "test");
        when(documentService.get(docId)).thenReturn(wrongKindDoc);

        ReimbursementClaimRequest reqWrongKind = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("100.00"), LocalDate.now(), "desc", docId);
        assertThatThrownBy(() -> service.submit(reqWrongKind))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("Document must be of kind REIMBURSEMENT_RECEIPT");

        UUID otherEmployeeId = UUID.randomUUID();
        DocumentResponse wrongEmployeeDoc = new DocumentResponse(
                docId,
                otherEmployeeId,
                DocumentKind.REIMBURSEMENT_RECEIPT,
                "receipt.pdf",
                "application/pdf",
                100L,
                "chk",
                Instant.now(),
                "test");
        when(documentService.get(docId)).thenReturn(wrongEmployeeDoc);

        ReimbursementClaimRequest reqWrongEmp = new ReimbursementClaimRequest(
                REIMBURSEMENT_ID, new BigDecimal("100.00"), LocalDate.now(), "desc", docId);
        assertThatThrownBy(() -> service.submit(reqWrongEmp))
                .isInstanceOf(ReimbursementClaimValidationException.class)
                .hasMessageContaining("Document does not belong to caller");
    }

    @Test
    @DisplayName("Approved amount absent -> defaults to requested amount")
    void approvedAmountAbsentDefaultsToRequested() {
        UUID claimId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        ReimbursementClaim claim = new ReimbursementClaim(
                TENANT_ID,
                EMPLOYEE_ID,
                REIMBURSEMENT_ID,
                new BigDecimal("2000.00"),
                LocalDate.now(),
                "desc",
                null,
                "test");
        when(claimRepository.findByTenantIdAndId(TENANT_ID, claimId)).thenReturn(Optional.of(claim));

        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getTenantId()).thenReturn(TENANT_ID);
        when(instance.getSubjectId()).thenReturn(claimId);
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        PayInputResponse payInputResp = new PayInputResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                YearMonth.now(),
                PayInputKind.REIMBURSEMENT,
                null,
                new BigDecimal("2000.00"),
                "payroll",
                "ref",
                null,
                null,
                Instant.now());
        when(payInputService.record(any(PayInputCommand.class))).thenReturn(payInputResp);

        StepDecision decisionWithoutAmount =
                new StepDecision(UUID.randomUUID(), UUID.randomUUID(), "APPROVED", "Approved", null, null);
        outcomeHandler.onApproved(instanceId, List.of(decisionWithoutAmount));

        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        ArgumentCaptor<PayInputCommand> captor = ArgumentCaptor.forClass(PayInputCommand.class);
        verify(payInputService).record(captor.capture());
        assertThat(captor.getValue().amount().toAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("Approved amount above requested -> clamped to requested amount")
    void approvedAmountAboveRequestedClamped() {
        UUID claimId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        ReimbursementClaim claim = new ReimbursementClaim(
                TENANT_ID,
                EMPLOYEE_ID,
                REIMBURSEMENT_ID,
                new BigDecimal("1000.00"),
                LocalDate.now(),
                "desc",
                null,
                "test");
        when(claimRepository.findByTenantIdAndId(TENANT_ID, claimId)).thenReturn(Optional.of(claim));

        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getTenantId()).thenReturn(TENANT_ID);
        when(instance.getSubjectId()).thenReturn(claimId);
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        PayInputResponse payInputResp = new PayInputResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                YearMonth.now(),
                PayInputKind.REIMBURSEMENT,
                null,
                new BigDecimal("1000.00"),
                "payroll",
                "ref",
                null,
                null,
                Instant.now());
        when(payInputService.record(any(PayInputCommand.class))).thenReturn(payInputResp);

        StepDecision decisionHighAmount = new StepDecision(
                UUID.randomUUID(), UUID.randomUUID(), "APPROVED", "Approved", new BigDecimal("1500.00"), null);
        outcomeHandler.onApproved(instanceId, List.of(decisionHighAmount));

        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        ArgumentCaptor<PayInputCommand> captor = ArgumentCaptor.forClass(PayInputCommand.class);
        verify(payInputService).record(captor.capture());
        assertThat(captor.getValue().amount().toAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Approved amount zero -> becomes rejected with remark 'Approved amount was zero'")
    void approvedAmountZeroBecomesRejected() {
        UUID claimId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        ReimbursementClaim claim = new ReimbursementClaim(
                TENANT_ID,
                EMPLOYEE_ID,
                REIMBURSEMENT_ID,
                new BigDecimal("1000.00"),
                LocalDate.now(),
                "desc",
                null,
                "test");
        when(claimRepository.findByTenantIdAndId(TENANT_ID, claimId)).thenReturn(Optional.of(claim));

        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getTenantId()).thenReturn(TENANT_ID);
        when(instance.getSubjectId()).thenReturn(claimId);
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        StepDecision decisionZeroAmount = new StepDecision(
                UUID.randomUUID(), UUID.randomUUID(), "APPROVED", "Approved zero", BigDecimal.ZERO, null);
        outcomeHandler.onApproved(instanceId, List.of(decisionZeroAmount));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getRemarks()).isEqualTo("Approved amount was zero");
    }
}
