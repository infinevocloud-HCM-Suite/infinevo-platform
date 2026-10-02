package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link ProofReviewServiceImpl} (W-34.2 spec §3 & §4).
 */
class ProofReviewServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private EmployeeProofOfInvestmentRepository proofRepository;
    private EmployeeProofItemRepository itemRepository;
    private EmployeeProofItemDocumentRepository documentLinkRepository;
    private EmployeeProofItemCommentRepository commentRepository;
    private ApprovalStepRepository stepRepository;
    private ApprovalService approvalService;
    private TaxDeclarationWindowService windowService;
    private DocumentService documentService;

    private ProofReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        proofRepository = mock(EmployeeProofOfInvestmentRepository.class);
        itemRepository = mock(EmployeeProofItemRepository.class);
        documentLinkRepository = mock(EmployeeProofItemDocumentRepository.class);
        commentRepository = mock(EmployeeProofItemCommentRepository.class);
        stepRepository = mock(ApprovalStepRepository.class);
        approvalService = mock(ApprovalService.class);
        windowService = mock(TaxDeclarationWindowService.class);
        documentService = mock(DocumentService.class);

        reviewService = new ProofReviewServiceImpl(
                proofRepository,
                itemRepository,
                documentLinkRepository,
                commentRepository,
                stepRepository,
                approvalService,
                windowService,
                documentService);

        IncomeTaxDeclarationWindow defaultWindow = new IncomeTaxDeclarationWindow();
        given(windowService.findOrCreateDefault(any(), any())).willReturn(defaultWindow);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("review: builds complete review response with open steps and comment counts")
    void reviewBuildsResponse() {
        UUID proofId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID itemStepId = UUID.randomUUID();
        UUID finalStepId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, empId, UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                new BigDecimal("100000.0000"),
                "test");
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setClaimedAmount(new BigDecimal("100000.0000"));

        ApprovalStep itemStep =
                new ApprovalStep(TENANT_ID, instanceId, 0, itemId.toString(), ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(itemStep, "id", itemStepId);

        ApprovalStep finalStep = new ApprovalStep(TENANT_ID, instanceId, 1, null, ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(finalStep, "id", finalStepId);

        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(itemStep, finalStep));
        given(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(TENANT_ID, proofId))
                .willReturn(List.of(item));
        given(commentRepository.findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(TENANT_ID, List.of(itemId)))
                .willReturn(List.of());

        ProofReviewResponse response = reviewService.review(proofId);

        assertThat(response.id()).isEqualTo(proofId);
        assertThat(response.status()).isEqualTo(ProofStatus.SUBMITTED);
        assertThat(response.approvalInstanceId()).isEqualTo(instanceId);
        assertThat(response.finalStepId()).isEqualTo(finalStepId);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(itemId);
        assertThat(response.items().get(0).openStepId()).isEqualTo(itemStepId);
        assertThat(response.items().get(0).commentCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("decideItem: APPROVE sets approved amount and status, and decides engine step")
    void decideItemApprove() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                new BigDecimal("100000.0000"),
                "test");
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setClaimedAmount(new BigDecimal("100000.0000"));

        ApprovalStep itemStep =
                new ApprovalStep(TENANT_ID, instanceId, 0, itemId.toString(), ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(itemStep, "id", stepId);

        given(proofRepository.lockByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.of(item));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(itemStep));

        ProofItemDecisionRequest req = new ProofItemDecisionRequest(
                ProofItemDecisionAction.APPROVE, new BigDecimal("80000.0000"), "Capped at 80k");

        ProofReviewItemResponse resp = reviewService.decideItem(proofId, itemId, req);

        assertThat(resp.status()).isEqualTo(ProofItemStatus.APPROVED);
        assertThat(resp.approvedAmount()).isEqualByComparingTo(new BigDecimal("80000.0000"));
        assertThat(resp.reviewerNote()).isEqualTo("Capped at 80k");

        ArgumentCaptor<ApprovalDecideRequest> captor = ArgumentCaptor.forClass(ApprovalDecideRequest.class);
        verify(approvalService).decide(eq(stepId), captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(captor.getValue().approvedAmount()).isEqualByComparingTo(new BigDecimal("80000.0000"));
        assertThat(captor.getValue().comment()).isEqualTo("Capped at 80k");
    }

    @Test
    @DisplayName("decideItem: DISALLOW sets approved amount to ZERO and decides engine step APPROVED with 0")
    void decideItemDisallow() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                new BigDecimal("100000.0000"),
                "test");
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setClaimedAmount(new BigDecimal("100000.0000"));

        ApprovalStep itemStep =
                new ApprovalStep(TENANT_ID, instanceId, 0, itemId.toString(), ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(itemStep, "id", stepId);

        given(proofRepository.lockByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.of(item));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(itemStep));

        ProofItemDecisionRequest req =
                new ProofItemDecisionRequest(ProofItemDecisionAction.DISALLOW, null, "Ineligible investment");

        ProofReviewItemResponse resp = reviewService.decideItem(proofId, itemId, req);

        assertThat(resp.status()).isEqualTo(ProofItemStatus.DISALLOWED);
        assertThat(resp.approvedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.reviewerNote()).isEqualTo("Ineligible investment");

        ArgumentCaptor<ApprovalDecideRequest> captor = ArgumentCaptor.forClass(ApprovalDecideRequest.class);
        verify(approvalService).decide(eq(stepId), captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(captor.getValue().approvedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("decideItem: RETURN marks item RETURNED before calling decide(REJECTED)")
    void decideItemReturn() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                new BigDecimal("100000.0000"),
                "test");
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setClaimedAmount(new BigDecimal("100000.0000"));

        ApprovalStep itemStep =
                new ApprovalStep(TENANT_ID, instanceId, 0, itemId.toString(), ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(itemStep, "id", stepId);

        given(proofRepository.lockByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.of(item));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(itemStep));

        ProofItemDecisionRequest req = new ProofItemDecisionRequest(
                ProofItemDecisionAction.RETURN, null, "Please re-upload receipt with clear dates");

        ProofReviewItemResponse resp = reviewService.decideItem(proofId, itemId, req);

        assertThat(resp.status()).isEqualTo(ProofItemStatus.RETURNED);
        assertThat(resp.reviewerNote()).isEqualTo("Please re-upload receipt with clear dates");

        ArgumentCaptor<ApprovalDecideRequest> captor = ArgumentCaptor.forClass(ApprovalDecideRequest.class);
        verify(approvalService).decide(eq(stepId), captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(captor.getValue().comment()).isEqualTo("Please re-upload receipt with clear dates");
    }

    @Test
    @DisplayName("decideFinal: throws 409 ITEMS_UNDECIDED when any item step is undecided")
    void decideFinalItemsUndecided() {
        UUID proofId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        ApprovalStep undecidedItemStep = new ApprovalStep(
                TENANT_ID, instanceId, 0, UUID.randomUUID().toString(), ApproverKind.ROLE, UUID.randomUUID());
        ApprovalStep finalStep = new ApprovalStep(TENANT_ID, instanceId, 1, null, ApproverKind.ROLE, UUID.randomUUID());

        given(proofRepository.lockByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(undecidedItemStep, finalStep));

        ProofFinalDecisionRequest req = new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, null);

        assertThatThrownBy(() -> reviewService.decideFinal(proofId, req))
                .isInstanceOf(ProofConflictException.class)
                .hasMessageContaining("Cannot make final decision until all claimed items are decided")
                .satisfies(ex -> assertThat(((ProofConflictException) ex).reasonCode())
                        .isEqualTo(ProofConflictException.ITEMS_UNDECIDED));
    }

    @Test
    @DisplayName("decideFinal: APPROVE calls approvalService with final step")
    void decideFinalApprove() {
        UUID proofId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID finalStepId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        ApprovalStep decidedItemStep = new ApprovalStep(
                TENANT_ID, instanceId, 0, UUID.randomUUID().toString(), ApproverKind.ROLE, UUID.randomUUID());
        decidedItemStep.setDecision(ApprovalDecision.APPROVED);

        ApprovalStep finalStep = new ApprovalStep(TENANT_ID, instanceId, 1, null, ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(finalStep, "id", finalStepId);

        given(proofRepository.lockByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(decidedItemStep, finalStep));
        given(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(TENANT_ID, proofId))
                .willReturn(List.of());

        ProofFinalDecisionRequest req = new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "All good");

        reviewService.decideFinal(proofId, req);

        ArgumentCaptor<ApprovalDecideRequest> captor = ArgumentCaptor.forClass(ApprovalDecideRequest.class);
        verify(approvalService).decide(eq(finalStepId), captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(captor.getValue().comment()).isEqualTo("All good");
    }

    @Test
    @DisplayName("decideFinal: RETURN sets proof reviewer_note and calls approvalService REJECTED")
    void decideFinalReturn() {
        UUID proofId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID finalStepId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setApprovalInstanceId(instanceId);

        ApprovalStep decidedItemStep = new ApprovalStep(
                TENANT_ID, instanceId, 0, UUID.randomUUID().toString(), ApproverKind.ROLE, UUID.randomUUID());
        decidedItemStep.setDecision(ApprovalDecision.APPROVED);

        ApprovalStep finalStep = new ApprovalStep(TENANT_ID, instanceId, 1, null, ApproverKind.ROLE, UUID.randomUUID());
        ReflectionTestUtils.setField(finalStep, "id", finalStepId);

        given(proofRepository.lockByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_ID, instanceId))
                .willReturn(List.of(decidedItemStep, finalStep));
        given(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(TENANT_ID, proofId))
                .willReturn(List.of());

        ProofFinalDecisionRequest req =
                new ProofFinalDecisionRequest(ProofFinalDecisionAction.RETURN, "Final check failed, returning proof");

        reviewService.decideFinal(proofId, req);

        assertThat(proof.getReviewerNote()).isEqualTo("Final check failed, returning proof");
        verify(proofRepository).save(proof);

        ArgumentCaptor<ApprovalDecideRequest> captor = ArgumentCaptor.forClass(ApprovalDecideRequest.class);
        verify(approvalService).decide(eq(finalStepId), captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(captor.getValue().comment()).isEqualTo("Final check failed, returning proof");
    }
}
