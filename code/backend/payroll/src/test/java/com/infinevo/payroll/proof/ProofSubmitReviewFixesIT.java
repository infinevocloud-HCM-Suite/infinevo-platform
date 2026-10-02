package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.DocumentService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The W-34.1 review findings, each as the employee would meet it:
 *
 * <ul>
 *   <li>a proof submitted without being read again after the declaration changed;
 *   <li>a file soft-deleted through core since it was attached;
 *   <li>a notification that cannot be composed, and the order of notification and commit.
 * </ul>
 */
class ProofSubmitReviewFixesIT extends ProofIntegrationTestBase {

    @Autowired
    private DocumentService documentService;

    @Test
    @DisplayName("Submit brings the items in line with the declaration: a line removed since is not sent in")
    void submitDropsLinesRemovedFromTheDeclaration() throws Exception {
        openWindows();
        UUID declarationId = declare();
        ProofResponse proof = proofService.readOwn(fy);
        UUID rent = item(proof, ProofSourceKind.HOUSE_RENT).id();
        UUID interest = item(proof, ProofSourceKind.HOME_LOAN_INTEREST).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));
        proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));
        proofService.updateItemOwn(fy, interest, new ProofItemUpdateRequest(new BigDecimal("200"), null));
        proofService.attachOwn(fy, interest, "interest.pdf", pdf("interest"));

        // The declaration is reopened, the home loan is dropped, and it is submitted again. The employee does
        // not open the proof screen in between: the next call is the submit itself.
        taxDeclarationService.reopen(employeeId, fy);
        UUID tenant = TenantContext.require();
        inTransaction(() -> {
            homeLoanRepository.deleteByTenantIdAndDeclarationId(tenant, declarationId);
            return null;
        });
        taxDeclarationService.submit(employeeId, fy);

        ProofResponse submitted = proofService.submitOwn(fy);

        assertThat(submitted.status()).isEqualTo(ProofStatus.SUBMITTED);
        assertThat(submitted.items())
                .as("the removed home-loan lines are not part of what was sent in")
                .extracting(ProofItemResponse::sourceKind)
                .containsExactly(ProofSourceKind.HOUSE_RENT);
        assertThat(submitted.items().get(0).claimedAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Submit with only a removed line claimed is refused: nothing claimed, not a stale claim sent")
    void submitWithOnlyARemovedLineClaimedIsRefused() throws Exception {
        openWindows(today().minusDays(1), today().plusDays(10), false);
        UUID declarationId = declare();
        ProofResponse proof = proofService.readOwn(fy);
        UUID interest = item(proof, ProofSourceKind.HOME_LOAN_INTEREST).id();
        proofService.updateItemOwn(fy, interest, new ProofItemUpdateRequest(new BigDecimal("200"), null));

        taxDeclarationService.reopen(employeeId, fy);
        UUID tenant = TenantContext.require();
        inTransaction(() -> {
            homeLoanRepository.deleteByTenantIdAndDeclarationId(tenant, declarationId);
            return null;
        });
        taxDeclarationService.submit(employeeId, fy);

        ProofSubmissionIT.assertConflict("NOTHING_CLAIMED", () -> proofService.submitOwn(fy));
        assertThat(proofService.readOwn(fy).status()).isEqualTo(ProofStatus.DRAFT);
    }

    @Test
    @DisplayName("A file soft-deleted through core no longer counts as attached at submit")
    void softDeletedFileDoesNotCountAsAttached() throws Exception {
        openWindows();
        declare();
        ProofResponse proof = proofService.readOwn(fy);
        UUID rent = item(proof, ProofSourceKind.HOUSE_RENT).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));
        ProofDocumentResponse file = proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));

        documentService.delete(file.documentId());

        ProofSubmissionIT.assertConflict("ATTACHMENT_REQUIRED", () -> proofService.submitOwn(fy));
        assertThat(item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).documents())
                .as("and the read does not show it")
                .isEmpty();
    }

    @Test
    @DisplayName("A file soft-deleted through core can still be detached, and the item takes a new one")
    void softDeletedFileCanBeDetached() throws Exception {
        openWindows();
        declare();
        ProofResponse proof = proofService.readOwn(fy);
        UUID rent = item(proof, ProofSourceKind.HOUSE_RENT).id();
        ProofDocumentResponse file = proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));
        documentService.delete(file.documentId());

        proofService.detachOwn(fy, rent, file.documentId());

        assertThat(ProofTestSchema.count("payroll.employee_proof_item_document", TenantContext.require()))
                .as("the link went with it")
                .isZero();
        assertThat(proofService.attachOwn(fy, rent, "again.pdf", pdf("again")).fileName())
                .isEqualTo("again.pdf");
    }

    @Test
    @DisplayName("The submit is committed before the notification is composed, and a failure to compose undoes nothing")
    void notificationComesAfterCommitAndCannotUndoTheSubmit() throws Exception {
        openWindows(today().minusDays(1), today().plusDays(10), false);
        declare();
        ProofResponse proof = proofService.readOwn(fy);
        UUID rent = item(proof, ProofSourceKind.HOUSE_RENT).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));

        AtomicReference<String> statusSeenByNotification = new AtomicReference<>();
        doAnswer(invocation -> {
                    statusSeenByNotification.set(committedProofStatus());
                    throw new IllegalStateException("template store is down");
                })
                .when(notificationService)
                .compose(any(NotificationEvent.class), any(UUID.class), anyMap());
        try {
            ProofResponse submitted = proofService.submitOwn(fy);

            assertThat(submitted.status()).isEqualTo(ProofStatus.SUBMITTED);
        } finally {
            when(notificationService.compose(any(), any(), anyMap())).thenReturn(List.of());
        }

        assertThat(statusSeenByNotification.get())
                .as("by the time the notification is composed the submit is already committed")
                .isEqualTo("SUBMITTED");
        assertThat(committedProofStatus()).as("and it stays so").isEqualTo("SUBMITTED");
    }

    /** The proof's status as another connection sees it, i.e. committed. */
    private String committedProofStatus() throws Exception {
        try (Connection conn = com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT status FROM payroll.employee_proof_of_investment WHERE tenant_id = ? AND employee_id = ?")) {
            ps.setObject(1, TenantContext.require());
            ps.setObject(2, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
