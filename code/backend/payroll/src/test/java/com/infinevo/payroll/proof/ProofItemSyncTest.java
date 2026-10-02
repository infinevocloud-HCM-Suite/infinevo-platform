package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** W-34.1 — how a proof's items follow the declared lines (spec section 3, "Item sync"). */
class ProofItemSyncTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PROOF = UUID.randomUUID();

    @Test
    @DisplayName("a declared line with no item gets a new item")
    void newLineCreatesItem() {
        ProofSourceLine line = line(ProofSourceKind.SECTION_6A, "80C", "150000");

        ProofItemSync.Plan plan = ProofItemSync.plan(List.of(line), List.of());

        assertThat(plan.toCreate()).containsExactly(line);
        assertThat(plan.toRefresh()).isEmpty();
        assertThat(plan.toRemove()).isEmpty();
    }

    @Test
    @DisplayName("an item whose line is gone is removed")
    void removedLineRemovesItem() {
        EmployeeProofItem gone = item(ProofSourceKind.SECTION_6A, UUID.randomUUID(), "80C", "100");

        ProofItemSync.Plan plan = ProofItemSync.plan(List.of(), List.of(gone));

        assertThat(plan.toRemove()).containsExactly(gone.getId());
        assertThat(plan.toCreate()).isEmpty();
    }

    @Test
    @DisplayName("a changed amount refreshes the item and keeps its identity")
    void changedAmountRefreshes() {
        ProofSourceLine line = line(ProofSourceKind.SECTION_6A, "80C", "200");
        EmployeeProofItem existing = item(line.kind(), line.lineId(), "80C", "100");

        ProofItemSync.Plan plan = ProofItemSync.plan(List.of(line), List.of(existing));

        assertThat(plan.toRefresh()).hasSize(1);
        assertThat(plan.toRefresh().get(0).item()).isSameAs(existing);
        assertThat(plan.toRefresh().get(0).line().declaredAmount()).isEqualByComparingTo("200");
        assertThat(plan.toCreate()).isEmpty();
        assertThat(plan.toRemove()).isEmpty();
    }

    @Test
    @DisplayName("an unchanged line changes nothing, and scale alone is not a change")
    void unchangedIsEmpty() {
        ProofSourceLine line = line(ProofSourceKind.HOME_LOAN_INTEREST, "Loan", "100.0000");
        EmployeeProofItem existing = item(line.kind(), line.lineId(), "Loan", "100");

        assertThat(ProofItemSync.plan(List.of(line), List.of(existing)).isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("one loan yields a principal item and an interest item that do not collide")
    void oneLoanTwoItems() {
        UUID loan = UUID.randomUUID();
        ProofSourceLine principal =
                new ProofSourceLine(ProofSourceKind.HOME_LOAN_PRINCIPAL, loan, "Principal", new BigDecimal("50"));
        ProofSourceLine interest =
                new ProofSourceLine(ProofSourceKind.HOME_LOAN_INTEREST, loan, "Interest", new BigDecimal("70"));
        EmployeeProofItem existingPrincipal = item(principal.kind(), loan, "Principal", "50");

        ProofItemSync.Plan plan = ProofItemSync.plan(List.of(principal, interest), List.of(existingPrincipal));

        assertThat(plan.toCreate()).containsExactly(interest);
        assertThat(plan.toRefresh()).isEmpty();
        assertThat(plan.toRemove()).isEmpty();
    }

    @Test
    @DisplayName("the same line id under a different kind is a different item")
    void kindIsPartOfIdentity() {
        UUID id = UUID.randomUUID();
        EmployeeProofItem rent = item(ProofSourceKind.HOUSE_RENT, id, "Rent", "10");
        ProofSourceLine section = new ProofSourceLine(ProofSourceKind.SECTION_6A, id, "80C", new BigDecimal("10"));

        ProofItemSync.Plan plan = ProofItemSync.plan(List.of(section), List.of(rent));

        assertThat(plan.toCreate()).containsExactly(section);
        assertThat(plan.toRemove()).containsExactly(rent.getId());
    }

    @Test
    @DisplayName("a description over 150 characters is cut to fit its column")
    void longDescriptionIsCut() {
        ProofSourceLine line = new ProofSourceLine(
                ProofSourceKind.SECTION_6A, UUID.randomUUID(), "x".repeat(400), new BigDecimal("1"));

        assertThat(line.description()).hasSize(150);
    }

    private static ProofSourceLine line(ProofSourceKind kind, String description, String amount) {
        return new ProofSourceLine(kind, UUID.randomUUID(), description, new BigDecimal(amount));
    }

    private static EmployeeProofItem item(ProofSourceKind kind, UUID lineId, String description, String declared) {
        EmployeeProofItem item =
                new EmployeeProofItem(TENANT, PROOF, kind, lineId, description, new BigDecimal(declared), "test");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }
}
