package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-34.1 acceptance: an employee with a submitted declaration attaches files to each declared line and
 * submits the proof while the proof window is open (spec section 7).
 */
// Four connections, not the test default of two: in the submit-and-reopen race each side holds its transaction's
// connection and asks for one more (submit's notification transaction, reopen's employee look-up in the test
// stand-in), so two would leave both waiting on the pool and never on the row lock under test.
@org.springframework.test.context.TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class ProofSubmissionIT extends ProofIntegrationTestBase {

    @Test
    @DisplayName("Acceptance: declare, prove, submit; then submit again, edit and reopen are all refused")
    void employeeProvesAndSubmits() throws Exception {
        openWindows();

        // The proof is proof of a submitted declaration. With none, and with a draft one, it is refused.
        assertConflict("NOT_SUBMITTED", () -> proofService.readOwn(fy));
        taxDeclarationService.read(employeeId, fy);
        assertConflict("NOT_SUBMITTED", () -> proofService.readOwn(fy));

        declare();
        ProofResponse proof = proofService.readOwn(fy);
        assertThat(proof.status()).isEqualTo(ProofStatus.DRAFT);
        assertThat(proof.proofOpen()).isTrue();
        assertThat(proof.editable()).isTrue();
        assertThat(proof.dueDate()).isEqualTo(today().plusDays(10));
        assertThat(proof.items())
                .extracting(ProofItemResponse::sourceKind)
                .containsExactlyInAnyOrder(
                        ProofSourceKind.HOUSE_RENT,
                        ProofSourceKind.HOME_LOAN_PRINCIPAL,
                        ProofSourceKind.HOME_LOAN_INTEREST);
        ProofItemResponse rent = item(proof, ProofSourceKind.HOUSE_RENT);
        assertThat(rent.declaredAmount()).isEqualByComparingTo("180000");
        assertThat(rent.status()).isEqualTo(ProofItemStatus.PENDING);

        // Reading again does not duplicate anything.
        assertThat(proofService.readOwn(fy).items()).hasSize(3);

        proofService.updateItemOwn(
                fy, rent.id(), new ProofItemUpdateRequest(new BigDecimal("175000.50"), "paid by bank"));
        ProofDocumentResponse file = proofService.attachOwn(fy, rent.id(), "rent.pdf", pdf("rent"));
        assertThat(file.fileName()).isEqualTo("rent.pdf");

        ProofResponse submitted = proofService.submitOwn(fy);
        assertThat(submitted.status()).isEqualTo(ProofStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();
        assertThat(submitted.editable()).isFalse();
        assertThat(item(submitted, ProofSourceKind.HOUSE_RENT).claimedAmount()).isEqualByComparingTo("175000.50");

        List<ProofSubmittedEvent> published =
                events.stream(ProofSubmittedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).proofId()).isEqualTo(submitted.id());
        assertThat(published.get(0).employeeId()).isEqualTo(employeeId);
        assertThat(published.get(0).tenantId()).isEqualTo(TenantContext.require());
        assertThat(published.get(0).financialYear()).isEqualTo(fy);
        verify(notificationService, times(1)).compose(eq(NotificationEvent.POI_SUBMITTED), eq(employeeId), anyMap());

        assertConflict("ALREADY_SUBMITTED", () -> proofService.submitOwn(fy));
        assertThat(events.stream(ProofSubmittedEvent.class).count()).isEqualTo(1);
        assertConflict(
                "NOT_EDITABLE",
                () -> proofService.updateItemOwn(fy, rent.id(), new ProofItemUpdateRequest(BigDecimal.ONE, null)));
        assertConflict("NOT_EDITABLE", () -> proofService.attachOwn(fy, rent.id(), "late.pdf", pdf("late")));

        // The declaration cannot be pulled from under the proof by the employee, but an officer can.
        assertThatThrownBy(() -> taxDeclarationService.reopenOwn(fy))
                .isInstanceOfSatisfying(DeclarationNotEditableException.class, e -> assertThat(e.reasonCode())
                        .isEqualTo("PROOF_IN_PROGRESS"));
        assertThat(taxDeclarationService.reopen(employeeId, fy).status()).isEqualTo(DeclarationStatus.DRAFT);
    }

    @Test
    @DisplayName("Past its due date the proof can be read but not changed or submitted; the due day itself is open")
    void windowClosedAndBoundary() {
        openWindows(today().minusDays(10), today().minusDays(1), true);
        declare();

        ProofResponse proof = proofService.readOwn(fy);
        assertThat(proof.proofOpen()).isFalse();
        assertThat(proof.editable()).isFalse();
        UUID rent = item(proof, ProofSourceKind.HOUSE_RENT).id();
        assertConflict(
                "PROOF_WINDOW_CLOSED",
                () -> proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(BigDecimal.TEN, null)));
        assertConflict("PROOF_WINDOW_CLOSED", () -> proofService.attachOwn(fy, rent, "x.pdf", pdf("x")));
        assertConflict("PROOF_WINDOW_CLOSED", () -> proofService.submitOwn(fy));

        openWindows(today().minusDays(10), today(), true);
        assertThat(proofService.readOwn(fy).proofOpen())
                .as("the due date is a day the window is open")
                .isTrue();
    }

    @Test
    @DisplayName("A proof window that was never set is closed")
    void windowNeverSet() {
        declare();
        // No window row at all for the year: the default window has no proof dates.
        ProofResponse proof = proofService.readOwn(fy);

        assertThat(proof.proofOpen()).isFalse();
        assertConflict("PROOF_WINDOW_CLOSED", () -> proofService.submitOwn(fy));
    }

    @Test
    @DisplayName("Submit needs a claim, and with attachments mandatory, a file on every claimed item")
    void claimAndAttachmentRules() {
        openWindows();
        declare();
        ProofResponse proof = proofService.readOwn(fy);
        UUID rent = item(proof, ProofSourceKind.HOUSE_RENT).id();
        UUID interest = item(proof, ProofSourceKind.HOME_LOAN_INTEREST).id();

        assertConflict("NOTHING_CLAIMED", () -> proofService.submitOwn(fy));
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(BigDecimal.ZERO, null));
        assertConflict("NOTHING_CLAIMED", () -> proofService.submitOwn(fy));

        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));
        proofService.updateItemOwn(fy, interest, new ProofItemUpdateRequest(new BigDecimal("200"), null));
        proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));
        assertThatThrownBy(() -> proofService.submitOwn(fy)).isInstanceOfSatisfying(ProofConflictException.class, e -> {
            assertThat(e.reasonCode()).isEqualTo("ATTACHMENT_REQUIRED");
            assertThat(e.itemIds()).containsExactly(interest);
        });
        assertThat(proofService.readOwn(fy).status())
                .as("a refused submit changes nothing")
                .isEqualTo(ProofStatus.DRAFT);

        // The officer turns the attachment rule off for the year: the same claim now goes through.
        openWindows(today().minusDays(1), today().plusDays(10), false);
        assertThat(proofService.submitOwn(fy).status()).isEqualTo(ProofStatus.SUBMITTED);
    }

    @Test
    @DisplayName("Claimed amounts are validated: negative, more than two decimals, too large, and a long note")
    void amountValidation() {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();

        for (String bad : List.of("-1", "1.234", "1000000000000000")) {
            assertThatThrownBy(() ->
                            proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal(bad), null)))
                    .as(bad)
                    .isInstanceOf(ProofValidationException.class);
        }
        assertThatThrownBy(() -> proofService.updateItemOwn(
                        fy, rent, new ProofItemUpdateRequest(BigDecimal.ONE, "x".repeat(1001))))
                .isInstanceOf(ProofValidationException.class);
        assertThatThrownBy(() -> proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(null, null)))
                .isInstanceOf(ProofValidationException.class);
        assertThat(item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).claimedAmount())
                .as("nothing was stored")
                .isNull();
    }

    @Test
    @DisplayName(
            "Items follow the declaration while the proof is a draft: a removed line goes, a changed one refreshes")
    void itemsFollowDeclaration() throws Exception {
        openWindows();
        UUID declarationId = declare();
        ProofResponse first = proofService.readOwn(fy);
        UUID interest = item(first, ProofSourceKind.HOME_LOAN_INTEREST).id();
        UUID rent = item(first, ProofSourceKind.HOUSE_RENT).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("999"), "kept"));
        proofService.attachOwn(fy, interest, "interest.pdf", pdf("interest"));

        // An officer reopens, the loan is removed and the rent changes, and it is submitted again.
        taxDeclarationService.reopen(employeeId, fy);
        UUID tenant = TenantContext.require();
        inTransaction(() -> {
            homeLoanRepository.deleteByTenantIdAndDeclarationId(tenant, declarationId);
            var rentLine = houseRentRepository
                    .findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenant, declarationId)
                    .get(0);
            rentLine.setAmountPerMonth(new BigDecimal("20000"));
            return houseRentRepository.save(rentLine);
        });
        taxDeclarationService.submit(employeeId, fy);

        ProofResponse second = proofService.readOwn(fy);
        assertThat(second.items())
                .extracting(ProofItemResponse::sourceKind)
                .containsExactly(ProofSourceKind.HOUSE_RENT);
        ProofItemResponse refreshed = second.items().get(0);
        assertThat(refreshed.id()).as("the item keeps its identity").isEqualTo(rent);
        assertThat(refreshed.declaredAmount()).isEqualByComparingTo("240000");
        assertThat(refreshed.claimedAmount())
                .as("and what the employee entered")
                .isEqualByComparingTo("999");
        assertThat(refreshed.employeeNote()).isEqualTo("kept");
        assertThat(deletedDocuments())
                .as("the removed item's file was soft-deleted")
                .isEqualTo(1);
        assertThat(ProofTestSchema.count("payroll.employee_proof_item_document", TenantContext.require()))
                .as("and its link is gone")
                .isZero();
    }

    @Test
    @DisplayName("An officer's read creates nothing and never syncs; with no proof it is not found")
    void officerReadChangesNothing() throws Exception {
        openWindows();
        declare();

        assertThatThrownBy(() -> proofService.read(employeeId, fy)).isInstanceOf(ProofNotFoundException.class);
        assertThat(ProofTestSchema.count("payroll.employee_proof_of_investment", TenantContext.require()))
                .isZero();

        proofService.readOwn(fy);
        houseRentRepository.save(new com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent(
                TenantContext.require(),
                taxDeclarationService.require(employeeId, fy).getId(),
                java.time.LocalDate.of(2030, 4, 1),
                java.time.LocalDate.of(2030, 4, 1),
                "2 Other Street",
                "B. Landlord",
                null,
                false,
                new BigDecimal("500")));

        assertThat(proofService.read(employeeId, fy).items())
                .as("a line added since is not an item until the employee reads their own proof")
                .hasSize(3);
    }

    @Test
    @DisplayName("Two submits at once give one SUBMITTED, one ALREADY_SUBMITTED, and one notification")
    void concurrentSubmitIsOnce() throws Exception {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));
        proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));
        UUID tenant = TenantContext.require();
        var me = employeeService.get(employeeId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> submit = () -> {
            TenantContext.set(tenant);
            PayrollTestApp.CURRENT_EMPLOYEE.set(me);
            try {
                start.await();
                proofService.submitOwn(fy);
                return "SUBMITTED";
            } catch (ProofConflictException e) {
                return e.reasonCode();
            } finally {
                PayrollTestApp.CURRENT_EMPLOYEE.remove();
                TenantContext.clear();
            }
        };
        try {
            Future<String> a = pool.submit(submit);
            Future<String> b = pool.submit(submit);
            start.countDown();

            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder("SUBMITTED", "ALREADY_SUBMITTED");
        } finally {
            pool.shutdownNow();
        }
        verify(notificationService, times(1)).compose(eq(NotificationEvent.POI_SUBMITTED), eq(employeeId), anyMap());
    }

    @Test
    @DisplayName("Proof submit and declaration reopen at once: exactly one wins, never a submitted proof on a draft")
    void concurrentSubmitAndReopenNeverBothSucceed() throws Exception {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));
        proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));
        UUID tenant = TenantContext.require();
        var me = employeeService.get(employeeId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> submit = () -> {
            TenantContext.set(tenant);
            PayrollTestApp.CURRENT_EMPLOYEE.set(me);
            try {
                start.await();
                proofService.submitOwn(fy);
                return "SUBMITTED";
            } catch (ProofConflictException e) {
                return e.reasonCode();
            } finally {
                PayrollTestApp.CURRENT_EMPLOYEE.remove();
                TenantContext.clear();
            }
        };
        Callable<String> reopen = () -> {
            TenantContext.set(tenant);
            PayrollTestApp.CURRENT_EMPLOYEE.set(me);
            try {
                start.await();
                taxDeclarationService.reopenOwn(fy);
                return "REOPENED";
            } catch (DeclarationNotEditableException e) {
                return e.reasonCode();
            } finally {
                PayrollTestApp.CURRENT_EMPLOYEE.remove();
                TenantContext.clear();
            }
        };
        try {
            Future<String> a = pool.submit(submit);
            Future<String> b = pool.submit(reopen);
            start.countDown();

            assertThat(List.of(a.get(), b.get()))
                    .isIn(List.of("SUBMITTED", "PROOF_IN_PROGRESS"), List.of("NOT_SUBMITTED", "REOPENED"));
        } finally {
            pool.shutdownNow();
        }
        ProofStatus proof = proofService.readOwn(fy).status();
        DeclarationStatus declaration =
                taxDeclarationService.read(employeeId, fy).status();
        assertThat(proof == ProofStatus.SUBMITTED && declaration == DeclarationStatus.DRAFT)
                .as("a submitted proof on a draft declaration")
                .isFalse();
    }

    @Test
    @DisplayName("After a return the employee edits and submits again, and every item is back under review")
    void resubmitAfterReturn() throws Exception {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        proofService.updateItemOwn(fy, rent, new ProofItemUpdateRequest(new BigDecimal("100"), null));
        proofService.attachOwn(fy, rent, "rent.pdf", pdf("rent"));
        proofService.submitOwn(fy);

        // What W-34.2 does on a return, written directly.
        try (Connection conn = com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.migrationConnection()) {
            conn.createStatement()
                    .execute("UPDATE payroll.employee_proof_of_investment SET status = 'REJECTED', "
                            + "reviewer_note = 'Blurred receipt', decided_at = now()");
            conn.createStatement()
                    .execute("UPDATE payroll.employee_proof_item SET status = 'RETURNED', approved_amount = 0");
        }

        ProofResponse returned = proofService.readOwn(fy);
        assertThat(returned.status()).isEqualTo(ProofStatus.REJECTED);
        assertThat(returned.editable()).isTrue();
        assertThat(returned.reviewerNote()).isEqualTo("Blurred receipt");

        proofService.attachOwn(fy, rent, "rent-clear.pdf", pdf("clear"));
        ProofResponse again = proofService.submitOwn(fy);

        assertThat(again.status()).isEqualTo(ProofStatus.SUBMITTED);
        assertThat(again.reviewerNote()).as("the old reason is cleared").isNull();
        assertThat(again.decidedAt()).isNull();
        assertThat(again.items()).allSatisfy(i -> {
            assertThat(i.status()).isEqualTo(ProofItemStatus.PENDING);
            assertThat(i.approvedAmount()).isNull();
        });
        assertThat(events.stream(ProofSubmittedEvent.class).count()).isEqualTo(2);
    }

    private long deletedDocuments() throws Exception {
        try (Connection conn = com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.document WHERE tenant_id = ? AND is_deleted")) {
            ps.setObject(1, TenantContext.require());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    static void assertConflict(String reasonCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ProofConflictException.class, e -> assertThat(e.reasonCode())
                .isEqualTo(reasonCode));
    }
}
