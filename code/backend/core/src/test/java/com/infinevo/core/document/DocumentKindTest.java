package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DocumentKind} — which kinds a client may upload, and that the Java vocabulary and the
 * {@code document_kind_check} constraint agree (W-21, widened by W-36.5).
 */
class DocumentKindTest {

    @Test
    @DisplayName("A Form 16 Part A is system-filed and cannot be uploaded through /documents")
    void form16PartAIsNotUploadable() {
        assertThat(DocumentKind.FORM16_PART_A.isUploadable()).isFalse();
        assertThat(DocumentKind.SYSTEM_GENERATED)
                .containsExactlyInAnyOrder(DocumentKind.PAYSLIP, DocumentKind.EXPORT, DocumentKind.FORM16_PART_A);
    }

    @Test
    @DisplayName("Every kind the enum holds is named in V108's widened document_kind_check")
    void everyKindIsInTheCheckConstraint() throws Exception {
        String sql;
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("db/migration/core/V108__document_kind_form16.sql")) {
            assertThat(in).as("V108 must be on the test classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Arrays.stream(DocumentKind.values()).forEach(kind -> assertThat(sql).contains("'" + kind.name() + "'"));
    }
}
