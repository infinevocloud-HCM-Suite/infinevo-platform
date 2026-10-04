package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.payroll.taxcalc.recalc.event.ProofVerifiedEvent;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W-34.2 Acceptance test:
 *
 * <p>Submit (W-34.1) starts one instance with one item step per claimed item;
 * HR approves one, disallows one; final approve => proof APPROVED, APPROVAL_DECIDED notification queued.
 *
 * <p>Second proof: RETURN one item => proof REJECTED, item RETURNED with the reason on the /me read;
 * employee edits and resubmits => a new instance id (spec section 7).
 */
class ProofVerificationIT extends ProofIntegrationTestBase {

    @Autowired
    private ProofReviewService reviewService;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private EmployeeProofOfInvestmentRepository proofRepository;

    @Autowired
    private EmployeeProofItemRepository itemRepository;

    @Test
    @DisplayName(
            "Acceptance: Submit starts engine instance; HR approves/disallows items; final approve marks proof APPROVED")
    void verificationHappyPath() {
        openWindows();
        declare();

        ProofResponse ownProof = proofService.readOwn(fy);
        ProofItemResponse rent = item(ownProof, ProofSourceKind.HOUSE_RENT);
        ProofItemResponse loanPrincipal = item(ownProof, ProofSourceKind.HOME_LOAN_PRINCIPAL);
        ProofItemResponse loanInterest = item(ownProof, ProofSourceKind.HOME_LOAN_INTEREST);

        // Attach documents to all 3 items
        proofService.attachOwn(fy, rent.id(), "rent.pdf", pdf("rent"));
        proofService.attachOwn(fy, loanPrincipal.id(), "loan_p.pdf", pdf("loan_p"));
        proofService.attachOwn(fy, loanInterest.id(), "loan_i.pdf", pdf("loan_i"));

        // Claim amounts on rent and principal
        proofService.updateItemOwn(fy, rent.id(), new ProofItemUpdateRequest(new BigDecimal("180000.00"), "Full rent"));
        proofService.updateItemOwn(
                fy, loanPrincipal.id(), new ProofItemUpdateRequest(new BigDecimal("50000.00"), "Principal"));
        proofService.updateItemOwn(
                fy, loanInterest.id(), new ProofItemUpdateRequest(new BigDecimal("120000.00"), "Interest"));

        // Submit
        ProofResponse submitted = proofService.submitOwn(fy);
        assertThat(submitted.status()).isEqualTo(ProofStatus.SUBMITTED);

        // Engine instance started
        UUID proofId = submitted.id();
        EmployeeProofOfInvestment proofEntity = inTransaction(
                () -> proofRepository.findByTenantIdAndId(TENANT_A, proofId).orElseThrow());

        UUID instanceId = proofEntity.getApprovalInstanceId();
        assertThat(instanceId).isNotNull();

        ApprovalInstance instance =
                inTransaction(() -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(instance.getFlowType().name()).isEqualTo("PROOF_OF_INVESTMENT");

        List<ApprovalStep> steps = inTransaction(
                () -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        // 3 item steps at initial submission (final step is created sequentially once all item steps are decided)
        assertThat(steps).hasSize(3);

        // Reviewer reads review
        ProofReviewResponse review = reviewService.review(proofEntity.getId());
        assertThat(review.items()).hasSize(3);

        // HR approves rent item with 175000
        reviewService.decideItem(
                proofEntity.getId(),
                rent.id(),
                new ProofItemDecisionRequest(
                        ProofItemDecisionAction.APPROVE, new BigDecimal("175000.00"), "Approved partial"));

        // HR disallows loan principal item
        reviewService.decideItem(
                proofEntity.getId(),
                loanPrincipal.id(),
                new ProofItemDecisionRequest(ProofItemDecisionAction.DISALLOW, null, "Disallowed principal"));

        // HR approves loan interest item with full 120000
        reviewService.decideItem(
                proofEntity.getId(),
                loanInterest.id(),
                new ProofItemDecisionRequest(
                        ProofItemDecisionAction.APPROVE, new BigDecimal("120000.00"), "Approved interest"));

        assertThat(events.stream(ProofVerifiedEvent.class))
                .as("no event before the final approval")
                .isEmpty();

        // Final approve
        reviewService.decideFinal(
                proofEntity.getId(), new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "All verified"));

        // Verify outcome
        EmployeeProofOfInvestment finalProof = inTransaction(() -> proofRepository
                .findByTenantIdAndId(TENANT_A, proofEntity.getId())
                .orElseThrow());
        assertThat(finalProof.getStatus()).isEqualTo(ProofStatus.APPROVED);
        assertThat(finalProof.getDecidedAt()).isNotNull();

        EmployeeProofItem itemRent = inTransaction(
                () -> itemRepository.findByTenantIdAndId(TENANT_A, rent.id()).orElseThrow());
        assertThat(itemRent.getStatus()).isEqualTo(ProofItemStatus.APPROVED);
        assertThat(itemRent.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("175000.00"));

        EmployeeProofItem itemPrincipal = inTransaction(() ->
                itemRepository.findByTenantIdAndId(TENANT_A, loanPrincipal.id()).orElseThrow());
        assertThat(itemPrincipal.getStatus()).isEqualTo(ProofItemStatus.DISALLOWED);
        assertThat(itemPrincipal.getApprovedAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(notificationService, atLeastOnce())
                .compose(eq(NotificationEvent.APPROVAL_DECIDED), eq(employeeId), anyMap());

        // Exactly one ProofVerifiedEvent, carrying the proof's declaration and year (section 7)
        assertThat(events.stream(ProofVerifiedEvent.class).toList())
                .containsExactly(new ProofVerifiedEvent(
                        TENANT_A, employeeId, finalProof.getDeclarationId(), finalProof.getFinancialYear()));
    }

    @Test
    @DisplayName("Acceptance: Return item rejects proof; employee edits and resubmits to start a new instance")
    void returnItemFlow() throws Exception {
        UUID employee2 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-002", "emp2@acme.com", "Bob", "Smith");
        actAs(employee2);
        openWindows();
        UUID dec2 = declare(employee2);

        ProofResponse ownProof = proofService.readOwn(fy);
        ProofItemResponse rent = item(ownProof, ProofSourceKind.HOUSE_RENT);
        proofService.attachOwn(fy, rent.id(), "rent.pdf", pdf("rent"));
        proofService.updateItemOwn(
                fy, rent.id(), new ProofItemUpdateRequest(new BigDecimal("180000.00"), "Rent claim"));

        ProofResponse submitted = proofService.submitOwn(fy);
        EmployeeProofOfInvestment proofEntity = inTransaction(() ->
                proofRepository.findByTenantIdAndId(TENANT_A, submitted.id()).orElseThrow());
        UUID originalInstanceId = proofEntity.getApprovalInstanceId();
        assertThat(originalInstanceId).isNotNull();

        // Reviewer returns the rent item
        reviewService.decideItem(
                proofEntity.getId(),
                rent.id(),
                new ProofItemDecisionRequest(ProofItemDecisionAction.RETURN, null, "Rent receipt blurry"));

        // Verify rejected/returned state
        EmployeeProofOfInvestment rejectedProof = inTransaction(() -> proofRepository
                .findByTenantIdAndId(TENANT_A, proofEntity.getId())
                .orElseThrow());
        assertThat(rejectedProof.getStatus()).isEqualTo(ProofStatus.REJECTED);
        assertThat(rejectedProof.getDecidedAt()).isNotNull();

        EmployeeProofItem rejectedItem = inTransaction(
                () -> itemRepository.findByTenantIdAndId(TENANT_A, rent.id()).orElseThrow());
        assertThat(rejectedItem.getStatus()).isEqualTo(ProofItemStatus.RETURNED);
        assertThat(rejectedItem.getReviewerNote()).isEqualTo("Rent receipt blurry");
        assertThat(events.stream(ProofVerifiedEvent.class))
                .as("a returned proof is not verified")
                .isEmpty();

        // Employee reads own proof and sees RETURNED with reason
        ProofResponse readAfterReturn = proofService.readOwn(fy);
        assertThat(readAfterReturn.status()).isEqualTo(ProofStatus.REJECTED);
        assertThat(readAfterReturn.editable()).isTrue();
        ProofItemResponse readItem = item(readAfterReturn, ProofSourceKind.HOUSE_RENT);
        assertThat(readItem.status()).isEqualTo(ProofItemStatus.RETURNED);
        assertThat(readItem.reviewerNote()).isEqualTo("Rent receipt blurry");

        // Employee attaches clearer document and resubmits
        proofService.attachOwn(fy, rent.id(), "clear_rent.pdf", pdf("clear_rent"));
        ProofResponse resubmitted = proofService.submitOwn(fy);

        assertThat(resubmitted.status()).isEqualTo(ProofStatus.SUBMITTED);
        EmployeeProofOfInvestment resubmittedProof = inTransaction(() -> proofRepository
                .findByTenantIdAndId(TENANT_A, proofEntity.getId())
                .orElseThrow());
        UUID newInstanceId = resubmittedProof.getApprovalInstanceId();
        assertThat(newInstanceId).isNotNull();
        assertThat(newInstanceId).isNotEqualTo(originalInstanceId);

        EmployeeProofItem resetItem = inTransaction(
                () -> itemRepository.findByTenantIdAndId(TENANT_A, rent.id()).orElseThrow());
        assertThat(resetItem.getStatus()).isEqualTo(ProofItemStatus.PENDING);
    }
}
