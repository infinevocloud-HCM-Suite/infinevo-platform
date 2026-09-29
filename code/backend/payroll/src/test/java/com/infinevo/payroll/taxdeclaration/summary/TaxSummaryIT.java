package com.infinevo.payroll.taxdeclaration.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Acceptance integration test for other income declarations and tax summary snapshots (W-32.4).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxSummaryIT extends AbstractIntegrationTest {

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private OtherIncomeService otherIncomeService;

    @Autowired
    private TaxSummaryService taxSummaryService;

    @Autowired
    private EmployeeInvTaxSummaryRepository taxSummaryRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

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
        tx = new TransactionTemplate(txManager);
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
        TaxDeclarationTestSchema.clearDeclarations();

        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        employeeId = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-301", "summary.emp@acme.com", "Sum", "Tester");

        FinancialYear fy = FinancialYear.of(LocalDate.now());
        currentFy = fy.label();

        // The real stand-in (PayrollTestApp) looks the employee up tenant-scoped, so every
        // employeeService.get(employeeId) ownership check in the services runs for real.
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));

        LocalDate openDate = LocalDate.now().minusDays(1);
        LocalDate closeDate = LocalDate.now().plusDays(30);
        if (openDate.isBefore(fy.start())) openDate = fy.start();
        if (closeDate.isAfter(fy.end())) closeDate = fy.end();

        TaxDeclarationWindowRequest winReq =
                new TaxDeclarationWindowRequest(openDate, closeDate, false, "OLD", true, true, false, false);
        windowService.upsert(currentFy, winReq);
    }

    @AfterEach
    void cleanUp() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Full tax summary lifecycle: other income PUT, summary read, record computation, and lock protection")
    void testTaxSummaryLifecycle() {
        // 1. Initial summary read returns zero declared totals and null computed, and creates empty summary row
        taxDeclarationService.readOwn(currentFy);
        TaxSummaryResponse initialSummary = taxSummaryService.summaryOwn(currentFy);
        assertThat(initialSummary.declared().otherIncomeTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(initialSummary.computed()).isNull();
        var initialDecl = taxDeclarationService.require(employeeId, currentFy);
        boolean rowExists = Boolean.TRUE.equals(tx.execute(status -> taxSummaryRepository
                .findByTenantIdAndDeclarationIdAndRegime(
                        TaxDeclarationTestSchema.TENANT_A, initialDecl.getId(), initialDecl.getTaxRegime())
                .isPresent()));
        assertThat(rowExists).isTrue();

        // 2. PUT other income
        List<OtherIncomeRequest> requests = List.of(
                new OtherIncomeRequest(
                        OtherIncomeKind.SAVINGS_INTEREST, "Ignored bank desc", new BigDecimal("8000.0000")),
                new OtherIncomeRequest(OtherIncomeKind.OTHER, "Consulting fee", new BigDecimal("20000.0000")));
        var savedOther = otherIncomeService.replaceOwn(currentFy, requests);
        assertThat(savedOther).hasSize(2);
        var savingsItem = savedOther.stream()
                .filter(o -> o.kind() == OtherIncomeKind.SAVINGS_INTEREST)
                .findFirst()
                .orElseThrow();
        assertThat(savingsItem.description()).isNull();
        var otherItem = savedOther.stream()
                .filter(o -> o.kind() == OtherIncomeKind.OTHER)
                .findFirst()
                .orElseThrow();
        assertThat(otherItem.description()).isEqualTo("Consulting fee");

        // 2b. Description exceeding 150 chars throws WindowValidationException (400)
        List<OtherIncomeRequest> over150 =
                List.of(new OtherIncomeRequest(OtherIncomeKind.OTHER, "x".repeat(151), new BigDecimal("1000.0000")));
        assertThatThrownBy(() -> otherIncomeService.replaceOwn(currentFy, over150))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Description must not exceed 150 characters");

        // 3. GET summary reflects declared other income
        TaxSummaryResponse summaryAfterOther = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryAfterOther.declared().otherIncomeTotal()).isEqualByComparingTo(new BigDecimal("28000.00"));
        assertThat(summaryAfterOther.computed()).isNull();

        // 4. Record computed tax figures (simulating W-33 calculation engine)
        var decl = taxDeclarationService.require(employeeId, currentFy);
        TaxSummaryFigures figures1 = new TaxSummaryFigures(
                new BigDecimal("1200000.0000"),
                new BigDecimal("950000.0000"),
                new BigDecimal("102500.0000"),
                new BigDecimal("50000.0000"),
                new BigDecimal("52500.0000"),
                new BigDecimal("50000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("28000.0000"),
                BigDecimal.ZERO,
                new BigDecimal("150000.0000"),
                10);
        taxSummaryService.record(decl.getId(), decl.getTaxRegime(), figures1);

        // 5. GET summary now returns populated computed figures
        TaxSummaryResponse summaryWithComputed = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryWithComputed.computed()).isNotNull();
        assertThat(summaryWithComputed.computed().taxableIncome()).isEqualByComparingTo(new BigDecimal("1200000.00"));
        assertThat(summaryWithComputed.computed().netTaxableIncome()).isEqualByComparingTo(new BigDecimal("950000.00"));
        assertThat(summaryWithComputed.computed().taxOnTaxableIncome())
                .isEqualByComparingTo(new BigDecimal("102500.00"));
        assertThat(summaryWithComputed.computed().remainingMonths()).isEqualTo(10);
        assertThat(summaryWithComputed.computed().computedAt()).isNotNull();

        // 5b. Overwrite test (spec §7): second record() call overwrites existing summary row instead of duplicating
        // Also verify regime == null safely defaults to declaration's regime
        TaxSummaryFigures figures2 = new TaxSummaryFigures(
                new BigDecimal("1300000.0000"),
                new BigDecimal("1050000.0000"),
                new BigDecimal("122500.0000"),
                new BigDecimal("60000.0000"),
                new BigDecimal("62500.0000"),
                new BigDecimal("60000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("28000.0000"),
                BigDecimal.ZERO,
                new BigDecimal("150000.0000"),
                9);
        taxSummaryService.record(decl.getId(), null, figures2);
        TaxSummaryResponse summaryOverwritten = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryOverwritten.computed().taxableIncome()).isEqualByComparingTo(new BigDecimal("1300000.00"));
        assertThat(summaryOverwritten.computed().remainingMonths()).isEqualTo(9);

        // Verify null figures throws WindowValidationException
        assertThatThrownBy(() -> taxSummaryService.record(decl.getId(), "NEW", null))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Tax summary figures must not be null");

        // Verify exactly 1 summary row exists for (tenant, declaration, regime)
        var summaries = taxSummaryRepository.findAll();
        long matchingRows = summaries.stream()
                .filter(s -> s.getDeclarationId().equals(decl.getId())
                        && s.getRegime().equals(decl.getTaxRegime()))
                .count();
        assertThat(matchingRows).isEqualTo(1);

        // 6. Submit declaration -> editing other income is blocked (409)
        taxDeclarationService.submitOwn(currentFy);

        assertThatThrownBy(() -> otherIncomeService.replaceOwn(currentFy, requests))
                .isInstanceOf(DeclarationNotEditableException.class);

        // 7. GET summary still succeeds on submitted declaration
        TaxSummaryResponse summaryAfterSubmit = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryAfterSubmit.computed()).isNotNull();
    }
}
