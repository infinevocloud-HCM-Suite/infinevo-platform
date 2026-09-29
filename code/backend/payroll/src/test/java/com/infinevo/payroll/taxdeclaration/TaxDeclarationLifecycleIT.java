package com.infinevo.payroll.taxdeclaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Acceptance integration test for income tax declaration lifecycle (W-32.1, spec §7).
 *
 * <p>Lifecycle flow:
 * <ol>
 *   <li>Officer sets the window</li>
 *   <li>Employee GET /me creates DRAFT with default regime</li>
 *   <li>Employee PUT updates flags</li>
 *   <li>Employee submits -> SUBMITTED with submitted_at</li>
 *   <li>Employee PUT refused with 409 NOT_EDITABLE</li>
 *   <li>Employee reopens -> DRAFT</li>
 *   <li>Window moved to past -> reopen refused with 409 WINDOW_CLOSED, PUT refused with 409 NOT_EDITABLE</li>
 *   <li>Officer PUT still succeeds (window ignored)</li>
 *   <li>Officer locks declaration -> employee reopen refused with 409 LOCKED</li>
 * </ol>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxDeclarationLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private EmployeeService employeeService;

    private UUID employeeId;
    private String currentFy;

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        TaxDeclarationTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
        TaxDeclarationTestSchema.clearDeclarations();

        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        employeeId = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-001", "employee@acme.com", "Jane", "Doe");

        FinancialYear fy = FinancialYear.of(LocalDate.now());
        currentFy = fy.label();

        // The real stand-in (PayrollTestApp) looks the employee up tenant-scoped, so every
        // employeeService.get(employeeId) ownership check in the services runs for real.
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));
    }

    @AfterEach
    void cleanUp() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Full tax declaration lifecycle: window, submit, reopen, expired window, officer bypass, and lock")
    void testTaxDeclarationFullLifecycle() {
        FinancialYear fy = FinancialYear.parse(currentFy);

        // 1. Officer sets the window
        LocalDate openDate = LocalDate.now().minusDays(2);
        LocalDate closeDate = LocalDate.now().plusDays(20);
        if (openDate.isBefore(fy.start())) {
            openDate = fy.start();
        }
        if (closeDate.isAfter(fy.end())) {
            closeDate = fy.end();
        }

        TaxDeclarationWindowRequest winReq =
                new TaxDeclarationWindowRequest(openDate, closeDate, false, "NEW", true, true, false, false);
        TaxDeclarationWindowResponse winResp = windowService.upsert(currentFy, winReq);
        assertThat(winResp.isOpen()).isTrue();
        assertThat(winResp.defaultTaxRegime()).isEqualTo("NEW");

        // 2. Employee GET /me creates DRAFT with default regime
        TaxDeclarationResponse decl = taxDeclarationService.readOwn(currentFy);
        assertThat(decl.status()).isEqualTo(DeclarationStatus.DRAFT);
        assertThat(decl.taxRegime()).isEqualTo("NEW");
        assertThat(decl.windowOpen()).isTrue();
        assertThat(decl.editable()).isTrue();
        assertThat(decl.isLocked()).isFalse();
        assertThat(decl.submittedAt()).isNull();

        // 3. Employee updates flags and regime
        TaxDeclarationRequest updateReq = new TaxDeclarationRequest("OLD", true, true, false);
        TaxDeclarationResponse updated = taxDeclarationService.saveOwn(currentFy, updateReq);
        assertThat(updated.taxRegime()).isEqualTo("OLD");
        assertThat(updated.isStayingInRentedHouse()).isTrue();
        assertThat(updated.isRepayingSelfOccupiedLoan()).isTrue();
        assertThat(updated.hasLetOutProperty()).isFalse();

        // 4. Employee submits -> SUBMITTED with submitted_at
        TaxDeclarationResponse submitted = taxDeclarationService.submitOwn(currentFy);
        assertThat(submitted.status()).isEqualTo(DeclarationStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();
        assertThat(submitted.editable()).isFalse();

        // 5. Employee PUT on submitted declaration -> 409 NOT_EDITABLE
        assertThatThrownBy(() -> taxDeclarationService.saveOwn(currentFy, updateReq))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("NOT_EDITABLE"));

        // 6. Employee reopens -> DRAFT
        TaxDeclarationResponse reopened = taxDeclarationService.reopenOwn(currentFy);
        assertThat(reopened.status()).isEqualTo(DeclarationStatus.DRAFT);
        assertThat(reopened.editable()).isTrue();

        // Re-submit for testing window closure
        taxDeclarationService.submitOwn(currentFy);

        // 7. Window moved to the past -> reopen 409 WINDOW_CLOSED, save 409
        // To set dates within the FY but closed, we can set window in the past (if today > fy.start + 10)
        // or set window.isLocked = true (or closed date before today).
        LocalDate pastOpen = fy.start();
        LocalDate pastClose = fy.start().plusDays(2);
        if (LocalDate.now().isAfter(pastClose)) {
            TaxDeclarationWindowRequest pastWin =
                    new TaxDeclarationWindowRequest(pastOpen, pastClose, false, "NEW", true, true, false, false);
            windowService.upsert(currentFy, pastWin);
        } else {
            // Alternatively lock the window to test closed window
            TaxDeclarationWindowRequest lockedWin = new TaxDeclarationWindowRequest(
                    openDate, closeDate, true /* isLocked */, "NEW", true, true, false, false);
            windowService.upsert(currentFy, lockedWin);
        }

        // When window is closed: reopenOwn throws WINDOW_CLOSED
        assertThatThrownBy(() -> taxDeclarationService.reopenOwn(currentFy))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("WINDOW_CLOSED"));

        // When window is closed: saveOwn (PUT) is 409 NOT_EDITABLE (spec §4). The header is also
        // SUBMITTED here, which is the reason the message names first.
        TaxDeclarationRequest closedPutReq = new TaxDeclarationRequest("OLD", false, false, false);
        assertThatThrownBy(() -> taxDeclarationService.saveOwn(currentFy, closedPutReq))
                .isInstanceOf(DeclarationNotEditableException.class)
                .hasMessageContaining("Submitted")
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("NOT_EDITABLE"));

        // 8. Officer can still reopen and save (window ignored)
        TaxDeclarationResponse officerReopened = taxDeclarationService.reopen(employeeId, currentFy);
        assertThat(officerReopened.status()).isEqualTo(DeclarationStatus.DRAFT);

        TaxDeclarationRequest officerReq = new TaxDeclarationRequest(null, false, true, true);
        TaxDeclarationResponse officerSaved = taxDeclarationService.save(employeeId, currentFy, officerReq);
        assertThat(officerSaved.hasLetOutProperty()).isTrue();

        // Submit again before officer locks
        taxDeclarationService.submit(employeeId, currentFy);

        // 9. Officer locks declaration -> employee reopen is 409 LOCKED
        TaxDeclarationResponse locked = taxDeclarationService.lock(employeeId, currentFy);
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.lockedAt()).isNotNull();

        assertThatThrownBy(() -> taxDeclarationService.reopenOwn(currentFy))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("LOCKED"));
    }
}
