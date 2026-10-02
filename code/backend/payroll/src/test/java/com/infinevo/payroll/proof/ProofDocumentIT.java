package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-34.1 — the files on a proof: streaming, ownership, removal, and the cap (spec sections 4 and 7). */
class ProofDocumentIT extends ProofIntegrationTestBase {

    @Test
    @DisplayName("A download streams the bytes that were uploaded, for the employee and for an officer")
    void downloadStreamsUploadedBytes() throws IOException {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        ProofDocumentResponse file = proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent receipt"));

        try (var content =
                proofService.openDocumentOwn(fy, rent, file.documentId()).content()) {
            assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("%PDF-1.4 rent receipt");
        }
        try (var content = proofService
                .openDocument(employeeId, fy, rent, file.documentId())
                .content()) {
            assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("%PDF-1.4 rent receipt");
        }
        assertThat(proofService.readOwn(fy).items().stream()
                        .filter(i -> i.id().equals(rent))
                        .findFirst()
                        .orElseThrow()
                        .documents())
                .extracting(ProofDocumentResponse::fileName)
                .containsExactly("rent.pdf");
    }

    @Test
    @DisplayName("A file cannot be fetched through another item, or by another employee, by guessing its id")
    void documentIdsCannotBeGuessed() throws Exception {
        openWindows();
        declare();
        ProofResponse mine = proofService.readOwn(fy);
        UUID rent = item(mine, ProofSourceKind.HOUSE_RENT).id();
        UUID interest = item(mine, ProofSourceKind.HOME_LOAN_INTEREST).id();
        UUID file = proofService.attachOwn(fy, rent, "rent.pdf", pdf("mine")).documentId();

        // Another item of the same proof.
        assertThatThrownBy(() -> proofService.openDocumentOwn(fy, interest, file))
                .isInstanceOf(ProofNotFoundException.class);
        assertThatThrownBy(() -> proofService.detachOwn(fy, interest, file)).isInstanceOf(ProofNotFoundException.class);

        // A colleague in the same tenant, with a proof of their own.
        UUID colleague = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-002", "colleague@acme.com", "Sam", "Roe");
        actAs(colleague);
        declare(colleague);
        ProofResponse theirs = proofService.readOwn(fy);
        UUID theirRent = item(theirs, ProofSourceKind.HOUSE_RENT).id();
        assertThat(theirRent).isNotEqualTo(rent);
        assertThatThrownBy(() -> proofService.openDocumentOwn(fy, rent, file))
                .isInstanceOf(ProofNotFoundException.class);
        assertThatThrownBy(() -> proofService.openDocumentOwn(fy, theirRent, file))
                .isInstanceOf(ProofNotFoundException.class);
        assertThatThrownBy(() -> proofService.attachOwn(fy, rent, "sneaky.pdf", pdf("x")))
                .isInstanceOf(ProofNotFoundException.class);
        assertThatThrownBy(() -> proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(BigDecimal.ONE, null)))
                .isInstanceOf(ProofNotFoundException.class);

        // An officer of another tenant cannot even name the employee.
        TenantContext.set(TENANT_B);
        assertThatThrownBy(() -> proofService.openDocument(employeeId, fy, rent, file))
                .isInstanceOf(EmployeeService.NotFoundException.class);
        assertThatThrownBy(() -> proofService.read(employeeId, fy))
                .isInstanceOf(EmployeeService.NotFoundException.class);
    }

    @Test
    @DisplayName("Detaching unlinks the file and soft-deletes it, and only while the proof can be edited")
    void detachRemovesTheFile() {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        UUID file = proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent")).documentId();

        proofService.detachOwn(fy, rent, file);

        assertThat(proofService.readOwn(fy).items().stream()
                        .filter(i -> i.id().equals(rent))
                        .findFirst()
                        .orElseThrow()
                        .documents())
                .isEmpty();
        assertThatThrownBy(() -> proofService.openDocumentOwn(fy, rent, file))
                .isInstanceOf(ProofNotFoundException.class);
        assertThatThrownBy(() -> proofService.detachOwn(fy, rent, file)).isInstanceOf(ProofNotFoundException.class);

        UUID again = proofService.attachOwn(fy, rent, "rent2.pdf", pdf("rent2")).documentId();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("10"), null));
        proofService.submitOwn(fy);
        ProofSubmissionIT.assertConflict("NOT_EDITABLE", () -> proofService.detachOwn(fy, rent, again));
    }

    @Test
    @DisplayName("An item holds at most ten files")
    void documentCap() {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        for (int i = 0; i < ProofRules.MAX_DOCUMENTS_PER_ITEM; i++) {
            proofService.attachOwn(fy, rent, "f" + i + ".pdf", pdf("f" + i));
        }

        ProofSubmissionIT.assertConflict(
                "DOCUMENT_LIMIT", () -> proofService.attachOwn(fy, rent, "eleventh.pdf", pdf("11")));
        assertThat(item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).documents())
                .hasSize(ProofRules.MAX_DOCUMENTS_PER_ITEM);
    }

    @Test
    @DisplayName("A login linked to no employee gets a permission error, not a proof")
    void noEmployeeNoProof() {
        openWindows();
        declare();
        com.infinevo.payroll.PayrollTestApp.CURRENT_EMPLOYEE.remove();

        assertThatThrownBy(() -> proofService.readOwn(fy))
                .isInstanceOf(com.infinevo.shared.authz.PermissionDeniedException.class);
        assertThatThrownBy(() -> proofService.submitOwn(fy))
                .isInstanceOf(com.infinevo.shared.authz.PermissionDeniedException.class);
        assertThatThrownBy(() -> proofService.openDocumentOwn(fy, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(com.infinevo.shared.authz.PermissionDeniedException.class);
    }

    @Test
    @DisplayName("A store failure leaves no link behind")
    void storeFailureLeavesNoLink() throws Exception {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();

        assertThatThrownBy(() -> proofService.attachOwn(fy, rent, "bad.pdf", new java.io.InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("disk gone");
                    }
                }))
                .isInstanceOf(RuntimeException.class);

        assertThat(ProofTestSchema.count("payroll.employee_proof_item_document", TENANT_A))
                .isZero();
    }
}
