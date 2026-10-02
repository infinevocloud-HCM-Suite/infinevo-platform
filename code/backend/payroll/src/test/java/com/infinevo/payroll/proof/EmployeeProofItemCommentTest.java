package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmployeeProofItemComment} (W-34.2).
 */
class EmployeeProofItemCommentTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();

    @Test
    @DisplayName("Creates comment successfully with all fields")
    void createsCommentSuccessfully() {
        EmployeeProofItemComment comment = new EmployeeProofItemComment(
                TENANT_ID, ITEM_ID, AUTHOR_ID, ProofCommentRole.REVIEWER, "Please attach valid bill", "reviewer-user");

        assertThat(comment.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(comment.getItemId()).isEqualTo(ITEM_ID);
        assertThat(comment.getAuthorEmployeeId()).isEqualTo(AUTHOR_ID);
        assertThat(comment.getAuthorRole()).isEqualTo(ProofCommentRole.REVIEWER);
        assertThat(comment.getBody()).isEqualTo("Please attach valid bill");
        assertThat(comment.getCreatedBy()).isEqualTo("reviewer-user");
    }

    @Test
    @DisplayName("Rejects null arguments in constructor")
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new EmployeeProofItemComment(
                        null, ITEM_ID, AUTHOR_ID, ProofCommentRole.EMPLOYEE, "body", "actor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");

        assertThatThrownBy(() -> new EmployeeProofItemComment(
                        TENANT_ID, null, AUTHOR_ID, ProofCommentRole.EMPLOYEE, "body", "actor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("itemId");

        assertThatThrownBy(() -> new EmployeeProofItemComment(
                        TENANT_ID, ITEM_ID, null, ProofCommentRole.EMPLOYEE, "body", "actor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("authorEmployeeId");

        assertThatThrownBy(() -> new EmployeeProofItemComment(TENANT_ID, ITEM_ID, AUTHOR_ID, null, "body", "actor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("authorRole");

        assertThatThrownBy(() -> new EmployeeProofItemComment(
                        TENANT_ID, ITEM_ID, AUTHOR_ID, ProofCommentRole.EMPLOYEE, null, "actor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("body");

        assertThatThrownBy(() -> new EmployeeProofItemComment(
                        TENANT_ID, ITEM_ID, AUTHOR_ID, ProofCommentRole.EMPLOYEE, "body", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actor");
    }
}
