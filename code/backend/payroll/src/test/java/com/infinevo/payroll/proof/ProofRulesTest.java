package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** W-34.1 — the proof window and the submit rules (spec sections 3 and 4). */
class ProofRulesTest {

    private static final LocalDate OPENS = LocalDate.of(2026, 1, 10);
    private static final LocalDate DUE = LocalDate.of(2026, 2, 20);

    @Nested
    @DisplayName("proofOpen")
    class ProofOpen {

        @Test
        @DisplayName("true on both boundary days and between")
        void openOnBoundaries() {
            IncomeTaxDeclarationWindow window = window(OPENS, DUE, false);

            assertThat(window.isProofOpenOn(OPENS)).isTrue();
            assertThat(window.isProofOpenOn(DUE)).isTrue();
            assertThat(window.isProofOpenOn(LocalDate.of(2026, 2, 1))).isTrue();
        }

        @Test
        @DisplayName("false the day before it opens and the day after it is due")
        void closedOutside() {
            IncomeTaxDeclarationWindow window = window(OPENS, DUE, false);

            assertThat(window.isProofOpenOn(OPENS.minusDays(1))).isFalse();
            assertThat(window.isProofOpenOn(DUE.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("false when locked, whatever the dates")
        void closedWhenLocked() {
            assertThat(window(OPENS, DUE, true).isProofOpenOn(LocalDate.of(2026, 2, 1)))
                    .isFalse();
        }

        @Test
        @DisplayName("false when either date is unset, and for a null date")
        void closedWhenUnset() {
            assertThat(window(null, DUE, false).isProofOpenOn(DUE)).isFalse();
            assertThat(window(OPENS, null, false).isProofOpenOn(OPENS)).isFalse();
            assertThat(window(OPENS, DUE, false).isProofOpenOn(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("editable only while open and DRAFT or REJECTED")
        void editableStates() {
            assertThat(ProofRules.isEditable(true, ProofStatus.DRAFT)).isTrue();
            assertThat(ProofRules.isEditable(true, ProofStatus.REJECTED)).isTrue();
            assertThat(ProofRules.isEditable(true, ProofStatus.SUBMITTED)).isFalse();
            assertThat(ProofRules.isEditable(true, ProofStatus.APPROVED)).isFalse();
            assertThat(ProofRules.isEditable(false, ProofStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("requireEditable names the reason: state first, then the window")
        void requireEditableReasons() {
            assertThatThrownBy(() -> ProofRules.requireEditable(true, ProofStatus.SUBMITTED))
                    .isInstanceOfSatisfying(ProofConflictException.class, e -> assertThat(e.reasonCode())
                            .isEqualTo("NOT_EDITABLE"));
            assertThatThrownBy(() -> ProofRules.requireEditable(false, ProofStatus.DRAFT))
                    .isInstanceOfSatisfying(ProofConflictException.class, e -> assertThat(e.reasonCode())
                            .isEqualTo("PROOF_WINDOW_CLOSED"));
            assertThatCode(() -> ProofRules.requireEditable(true, ProofStatus.REJECTED))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a claimed amount must be present, not negative, at most two decimals and fit the column")
        void claimedAmountRules() {
            assertThatCode(() -> ProofRules.validateClaimedAmount(new BigDecimal("0")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ProofRules.validateClaimedAmount(new BigDecimal("1500.50")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ProofRules.validateClaimedAmount(new BigDecimal("10.500")))
                    .as("trailing zeros are not extra decimals")
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> ProofRules.validateClaimedAmount(null))
                    .isInstanceOf(ProofValidationException.class);
            assertThatThrownBy(() -> ProofRules.validateClaimedAmount(new BigDecimal("-0.01")))
                    .isInstanceOf(ProofValidationException.class);
            assertThatThrownBy(() -> ProofRules.validateClaimedAmount(new BigDecimal("1.234")))
                    .isInstanceOf(ProofValidationException.class);
            assertThatThrownBy(() -> ProofRules.validateClaimedAmount(new BigDecimal("1000000000000000")))
                    .isInstanceOf(ProofValidationException.class);
        }

        @Test
        @DisplayName("a note is capped at 1000 characters")
        void noteCap() {
            assertThatCode(() -> ProofRules.validateNote("x".repeat(1000))).doesNotThrowAnyException();
            assertThatCode(() -> ProofRules.validateNote(null)).doesNotThrowAnyException();
            assertThatThrownBy(() -> ProofRules.validateNote("x".repeat(1001)))
                    .isInstanceOf(ProofValidationException.class);
        }
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("refused when already submitted or approved, before the window is even looked at")
        void alreadySubmitted() {
            EmployeeProofItem item = claimed("100");

            assertThat(reason(ProofStatus.SUBMITTED, false, true, item, Map.of()))
                    .isEqualTo("ALREADY_SUBMITTED");
            assertThat(reason(ProofStatus.APPROVED, true, true, item, Map.of())).isEqualTo("ALREADY_SUBMITTED");
        }

        @Test
        @DisplayName("refused once the window is closed")
        void windowClosed() {
            assertThat(reason(ProofStatus.DRAFT, false, true, claimed("100"), Map.of()))
                    .isEqualTo("PROOF_WINDOW_CLOSED");
        }

        @Test
        @DisplayName("refused when nothing is claimed: no items, no amount, or a zero amount")
        void nothingClaimed() {
            assertThat(reasonForItems(ProofStatus.DRAFT, true, true, List.of(), Map.of()))
                    .isEqualTo("NOTHING_CLAIMED");
            assertThat(reason(ProofStatus.DRAFT, true, true, item(null), Map.of()))
                    .isEqualTo("NOTHING_CLAIMED");
            assertThat(reason(ProofStatus.DRAFT, true, true, claimed("0"), Map.of()))
                    .isEqualTo("NOTHING_CLAIMED");
        }

        @Test
        @DisplayName("attachment mandatory: every claimed item needs a file, and the missing ones are named")
        void attachmentRequired() {
            EmployeeProofItem withFile = claimed("100");
            EmployeeProofItem without = claimed("200");
            EmployeeProofItem unclaimed = item(null);

            assertThatThrownBy(() -> ProofRules.validateSubmit(
                            ProofStatus.DRAFT,
                            true,
                            true,
                            List.of(withFile, without, unclaimed),
                            Map.of(withFile.getId(), 1L)))
                    .isInstanceOfSatisfying(ProofConflictException.class, e -> {
                        assertThat(e.reasonCode()).isEqualTo("ATTACHMENT_REQUIRED");
                        assertThat(e.itemIds()).containsExactly(without.getId());
                    });
        }

        @Test
        @DisplayName("attachment not mandatory: a claim with no file is accepted")
        void attachmentNotMandatory() {
            assertThatCode(() -> ProofRules.validateSubmit(
                            ProofStatus.DRAFT, true, false, List.of(claimed("100")), Map.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a REJECTED proof can be submitted again")
        void resubmitAfterReturn() {
            EmployeeProofItem item = claimed("100");

            assertThatCode(() -> ProofRules.validateSubmit(
                            ProofStatus.REJECTED, true, true, List.of(item), Map.of(item.getId(), 2L)))
                    .doesNotThrowAnyException();
        }

        private String reason(
                ProofStatus status,
                boolean open,
                boolean attachmentMandatory,
                EmployeeProofItem item,
                Map<UUID, Long> docs) {
            return reasonForItems(status, open, attachmentMandatory, List.of(item), docs);
        }

        private String reasonForItems(
                ProofStatus status,
                boolean open,
                boolean attachmentMandatory,
                List<EmployeeProofItem> items,
                Map<UUID, Long> docs) {
            try {
                ProofRules.validateSubmit(status, open, attachmentMandatory, items, docs);
                return null;
            } catch (ProofConflictException e) {
                return e.reasonCode();
            }
        }
    }

    private static IncomeTaxDeclarationWindow window(LocalDate opens, LocalDate due, boolean locked) {
        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow(
                UUID.randomUUID(), "2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31), "NEW", true, true);
        window.setPoiOpensOn(opens);
        window.setPoiDueDate(due);
        window.setPoiLocked(locked);
        return window;
    }

    private static EmployeeProofItem claimed(String amount) {
        return item(new BigDecimal(amount));
    }

    private static EmployeeProofItem item(BigDecimal claimed) {
        EmployeeProofItem item = new EmployeeProofItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "80C",
                new BigDecimal("500"),
                "test");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setClaimedAmount(claimed);
        return item;
    }
}
