package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W-34.2 Access integration test:
 *
 * <p>An employee cannot decide; a reviewer who is not the assignee gets 403;
 * final before items returns 409 (spec section 7).
 */
class ProofReviewAccessIT extends ProofIntegrationTestBase {

    @Autowired
    private ProofReviewService reviewService;

    @Autowired
    private EmployeeProofOfInvestmentRepository proofRepository;

    @AfterEach
    void cleanUpApprover() {
        PayrollTestApp.APPROVER_ID.remove();
    }

    @Test
    @DisplayName("Calling final decision before all items are decided returns 409 ITEMS_UNDECIDED")
    void finalBeforeItems_returns409() {
        openWindows();
        declare();

        ProofResponse ownProof = proofService.readOwn(fy);
        ProofItemResponse rent = item(ownProof, ProofSourceKind.HOUSE_RENT);
        ProofItemResponse loanP = item(ownProof, ProofSourceKind.HOME_LOAN_PRINCIPAL);

        proofService.attachOwn(fy, rent.id(), "rent.pdf", pdf("rent"));
        proofService.attachOwn(fy, loanP.id(), "loan_p.pdf", pdf("loan_p"));
        proofService.updateItemOwn(fy, rent.id(), new ProofItemUpdateRequest(new BigDecimal("180000.00"), "Rent"));
        proofService.updateItemOwn(fy, loanP.id(), new ProofItemUpdateRequest(new BigDecimal("50000.00"), "Principal"));

        ProofResponse submitted = proofService.submitOwn(fy);
        assertThat(submitted.status()).isEqualTo(ProofStatus.SUBMITTED);

        // Attempt final approval before any item is decided -> must throw ProofConflictException with ITEMS_UNDECIDED
        assertThatThrownBy(() -> reviewService.decideFinal(
                        submitted.id(),
                        new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "Premature final decision")))
                .isInstanceOf(ProofConflictException.class)
                .satisfies(e -> assertThat(((ProofConflictException) e).reasonCode())
                        .isEqualTo(ProofConflictException.ITEMS_UNDECIDED));
    }

    @Test
    @DisplayName("Deciding an item that is not under review returns 409 NOT_UNDER_REVIEW")
    void itemNotUnderReview_returns409() {
        openWindows();
        declare();

        ProofResponse ownProof = proofService.readOwn(fy);
        ProofItemResponse rent = item(ownProof, ProofSourceKind.HOUSE_RENT);

        // Proof is still in DRAFT (not submitted)
        assertThatThrownBy(() -> reviewService.decideItem(
                        ownProof.id(),
                        rent.id(),
                        new ProofItemDecisionRequest(
                                ProofItemDecisionAction.APPROVE, new BigDecimal("100000.00"), "Approved")))
                .isInstanceOf(ProofConflictException.class)
                .satisfies(e -> assertThat(((ProofConflictException) e).reasonCode())
                        .isEqualTo(ProofConflictException.NOT_UNDER_REVIEW));
    }

    @Test
    @DisplayName("A decider who is not the step assignee gets AccessDeniedException (403)")
    void nonAssignee_gets403() throws Exception {
        openWindows();
        declare();

        ProofResponse ownProof = proofService.readOwn(fy);
        ProofItemResponse rent = item(ownProof, ProofSourceKind.HOUSE_RENT);
        proofService.attachOwn(fy, rent.id(), "rent.pdf", pdf("rent"));
        proofService.updateItemOwn(fy, rent.id(), new ProofItemUpdateRequest(new BigDecimal("180000.00"), "Rent"));

        UUID hrApprover = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-HR", "hr@acme.com", "HR", "Approver");
        PayrollTestApp.APPROVER_ID.set(hrApprover);

        ProofResponse submitted = proofService.submitOwn(fy);

        EmployeeProofOfInvestment proofEntity = inTransaction(() ->
                proofRepository.findByTenantIdAndId(TENANT_A, submitted.id()).orElseThrow());
        UUID instanceId = proofEntity.getApprovalInstanceId();
        assertThat(instanceId).isNotNull();

        // Seed an outsider employee who is not the assigned approver
        UUID outsider = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-OUT", "out@acme.com", "Out", "Sider");
        actAs(outsider);

        assertThatThrownBy(() -> reviewService.decideItem(
                        proofEntity.getId(),
                        rent.id(),
                        new ProofItemDecisionRequest(
                                ProofItemDecisionAction.APPROVE, new BigDecimal("100000.00"), "Approved")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
