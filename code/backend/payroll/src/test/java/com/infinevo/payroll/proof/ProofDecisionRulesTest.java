package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProofDecisionRules} (W-34.2 spec §3 and §4).
 */
class ProofDecisionRulesTest {

    private static final BigDecimal CLAIMED = new BigDecimal("50000.0000");

    @Nested
    @DisplayName("Item Decision Rules")
    class ItemDecisionRules {

        @Test
        @DisplayName("Rejects null request or action")
        void rejectsNullRequestOrAction() {
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(null, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("must not be null");

            ProofItemDecisionRequest noAction = new ProofItemDecisionRequest(null, CLAIMED, "comment");
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(noAction, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Action must not be null");
        }

        @Test
        @DisplayName("APPROVE: Rejects null or negative approved amount")
        void approveRejectsNullOrNegativeAmount() {
            ProofItemDecisionRequest nullAmount =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, null, "ok");
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(nullAmount, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Approved amount is required");

            ProofItemDecisionRequest negativeAmount =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("-1"), "ok");
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(negativeAmount, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("APPROVE: Rejects approved amount exceeding claimed amount")
        void approveRejectsAmountExceedingClaimed() {
            ProofItemDecisionRequest excessive =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("50000.01"), "ok");
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(excessive, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("cannot exceed claimed amount");
        }

        @Test
        @DisplayName("APPROVE: Requires comment for partial approval (approved < claimed)")
        void approvePartialRequiresComment() {
            ProofItemDecisionRequest partialNoComment =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("30000"), null);
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(partialNoComment, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is required for partial approval");

            ProofItemDecisionRequest partialBlankComment =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("30000"), "   ");
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(partialBlankComment, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is required for partial approval");

            ProofItemDecisionRequest partialWithComment = new ProofItemDecisionRequest(
                    ProofItemDecisionAction.APPROVE, new BigDecimal("30000"), "Rent agreement missing last 2 months");
            assertThatCode(() -> ProofDecisionRules.validateItemDecision(partialWithComment, CLAIMED, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("APPROVE: Requires comment when poiCommentMandatory is true even for full approval")
        void approveEnforcesGlobalMandatoryComment() {
            ProofItemDecisionRequest fullNoComment =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, CLAIMED, null);

            // Allowed when mandatory flag is false
            assertThatCode(() -> ProofDecisionRules.validateItemDecision(fullNoComment, CLAIMED, false))
                    .doesNotThrowAnyException();

            // Refused when mandatory flag is true
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(fullNoComment, CLAIMED, true))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is mandatory");

            ProofItemDecisionRequest fullWithComment =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, CLAIMED, "Verified receipts");
            assertThatCode(() -> ProofDecisionRules.validateItemDecision(fullWithComment, CLAIMED, true))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DISALLOW: Requires comment and rejects non-zero approved amount")
        void disallowRequiresCommentAndZeroAmount() {
            ProofItemDecisionRequest noComment =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.DISALLOW, BigDecimal.ZERO, null);
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(noComment, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is required when disallowing");

            ProofItemDecisionRequest nonZeroAmount = new ProofItemDecisionRequest(
                    ProofItemDecisionAction.DISALLOW, new BigDecimal("100"), "Invalid bill");
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(nonZeroAmount, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Approved amount must be 0 for DISALLOW");

            ProofItemDecisionRequest validDisallow =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.DISALLOW, BigDecimal.ZERO, "Invalid receipt");
            assertThatCode(() -> ProofDecisionRules.validateItemDecision(validDisallow, CLAIMED, false))
                    .doesNotThrowAnyException();

            ProofItemDecisionRequest nullAmountDisallow =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.DISALLOW, null, "Invalid receipt");
            assertThatCode(() -> ProofDecisionRules.validateItemDecision(nullAmountDisallow, CLAIMED, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RETURN: Requires comment")
        void returnRequiresComment() {
            ProofItemDecisionRequest noComment =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.RETURN, null, null);
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(noComment, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is required when returning an item");

            ProofItemDecisionRequest validReturn = new ProofItemDecisionRequest(
                    ProofItemDecisionAction.RETURN, null, "Please attach signed agreement");
            assertThatCode(() -> ProofDecisionRules.validateItemDecision(validReturn, CLAIMED, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Rejects comment exceeding 1000 characters")
        void rejectsExcessiveCommentLength() {
            String longComment = "a".repeat(1001);
            ProofItemDecisionRequest excessive =
                    new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, CLAIMED, longComment);
            assertThatThrownBy(() -> ProofDecisionRules.validateItemDecision(excessive, CLAIMED, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("must not exceed 1000 characters");
        }
    }

    @Nested
    @DisplayName("Final Decision Rules")
    class FinalDecisionRules {

        @Test
        @DisplayName("Rejects null request or action")
        void rejectsNullRequestOrAction() {
            assertThatThrownBy(() -> ProofDecisionRules.validateFinalDecision(null, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("must not be null");

            ProofFinalDecisionRequest noAction = new ProofFinalDecisionRequest(null, "comment");
            assertThatThrownBy(() -> ProofDecisionRules.validateFinalDecision(noAction, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Action must not be null");
        }

        @Test
        @DisplayName("APPROVE: Allows null comment when not mandatory; requires comment when mandatory")
        void approveFinalCommentRules() {
            ProofFinalDecisionRequest noComment = new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, null);
            assertThatCode(() -> ProofDecisionRules.validateFinalDecision(noComment, false))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> ProofDecisionRules.validateFinalDecision(noComment, true))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is mandatory");

            ProofFinalDecisionRequest withComment =
                    new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "All items verified");
            assertThatCode(() -> ProofDecisionRules.validateFinalDecision(withComment, true))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RETURN: Always requires comment")
        void returnFinalRequiresComment() {
            ProofFinalDecisionRequest noComment = new ProofFinalDecisionRequest(ProofFinalDecisionAction.RETURN, null);
            assertThatThrownBy(() -> ProofDecisionRules.validateFinalDecision(noComment, false))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("Comment is required when returning proof");

            ProofFinalDecisionRequest withComment =
                    new ProofFinalDecisionRequest(ProofFinalDecisionAction.RETURN, "Please fix rent receipt");
            assertThatCode(() -> ProofDecisionRules.validateFinalDecision(withComment, false))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Comment Rules")
    class CommentRules {

        @Test
        @DisplayName("Rejects null or blank body")
        void rejectsNullOrBlankBody() {
            assertThatThrownBy(() -> ProofDecisionRules.validateComment(null))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("must not be blank");

            assertThatThrownBy(() -> ProofDecisionRules.validateComment(new ProofCommentRequest("   ")))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("Rejects body exceeding 1000 characters")
        void rejectsExcessiveBody() {
            String longBody = "x".repeat(1001);
            assertThatThrownBy(() -> ProofDecisionRules.validateComment(new ProofCommentRequest(longBody)))
                    .isInstanceOf(ProofValidationException.class)
                    .hasMessageContaining("must not exceed 1000 characters");
        }

        @Test
        @DisplayName("Accepts valid comment body")
        void acceptsValidBody() {
            assertThatCode(() -> ProofDecisionRules.validateComment(new ProofCommentRequest("Valid feedback")))
                    .doesNotThrowAnyException();
        }
    }
}
