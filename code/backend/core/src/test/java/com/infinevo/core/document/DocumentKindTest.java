package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentKindTest {

    @Test
    @DisplayName("FORM16_PART_A is system generated and not uploadable through /documents")
    void form16PartAIsNotUploadable() {
        assertThat(DocumentKind.FORM16_PART_A.isUploadable()).isFalse();
    }

    @Test
    @DisplayName("Payslip and Export are system generated and not uploadable")
    void payslipAndExportAreNotUploadable() {
        assertThat(DocumentKind.PAYSLIP.isUploadable()).isFalse();
        assertThat(DocumentKind.EXPORT.isUploadable()).isFalse();
    }

    @Test
    @DisplayName("Client uploaded document kinds are uploadable")
    void clientUploadedKindsAreUploadable() {
        assertThat(DocumentKind.EMPLOYEE_DOCUMENT.isUploadable()).isTrue();
        assertThat(DocumentKind.LEAVE_ATTACHMENT.isUploadable()).isTrue();
        assertThat(DocumentKind.REIMBURSEMENT_RECEIPT.isUploadable()).isTrue();
        assertThat(DocumentKind.INVESTMENT_PROOF.isUploadable()).isTrue();
    }
}
