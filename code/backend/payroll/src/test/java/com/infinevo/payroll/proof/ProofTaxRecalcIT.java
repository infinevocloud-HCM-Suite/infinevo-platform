package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.taxcalc.TaxCalculationService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * W-34.2 spec section 15a item 5: with W-33.3's listener in the context, approving a proof writes a
 * {@code payroll.tax_computation} row whose figures are the approved ones, not the declared ones.
 *
 * <p>{@code ProofOutcomeHandler} publishes {@code ProofVerifiedEvent} inside the approving transaction;
 * {@code TaxRecalculationListener.onProofVerified} picks it up after commit, on the {@code taxRecalc}
 * executor. This class swaps {@code PayrollTestApp}'s {@code SyncTaskExecutor} for a real pool, as
 * {@code TaxRecalcConfig} has in production: run on the committing thread, inside its after-commit callback,
 * the recalculation's own {@code @Transactional} joins the finished transaction and the tenant-bound
 * datasource refuses the auto-commit connection, so nothing is ever written. The test waits for the pool to
 * drain, bounded, before it reads.
 *
 * <p>The employee is on the old regime with a 12L salary (Basic 6L, HRA 2.4L, Special 3.6L) and declares
 * rent 1,80,000 (non-metro), home-loan principal 50,000 and interest 1,20,000. HR approves 1,20,000 rent,
 * 30,000 principal and 1,00,000 interest. The PROOF_VERIFIED row must show:
 * <ul>
 *   <li>HRA exemption 60,000 = min(HRA 2,40,000; rent 1,20,000 - 10% of Basic 60,000; 40% of Basic 2,40,000),
 *       where the declared rent would give 1,20,000;</li>
 *   <li>Chapter VI-A 30,000 (the approved principal under 80C), where the declared would give 50,000;</li>
 *   <li>house-property income -1,00,000 (the approved interest), where the declared would give -1,20,000.</li>
 * </ul>
 */
class ProofTaxRecalcIT extends ProofIntegrationTestBase {

    @Autowired
    private ProofReviewService reviewService;

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private TaxCalculationService taxCalculationService;

    /** The production shape of the executor (TaxRecalcConfig), replacing the test application's synchronous one. */
    @TestBean(name = "taxRecalc", methodName = "pooledTaxRecalc")
    private Executor taxRecalc;

    private static ThreadPoolTaskExecutor pool;

    private final List<UUID> earningIds = new ArrayList<>();

    static Executor pooledTaxRecalc() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setThreadNamePrefix("tax-recalc-it-");
        ex.initialize();
        pool = ex;
        return ex;
    }

    /** Salary tables, V102 employee_tds and V104 tax_computation the recalculation writes, and V147's rules. */
    @BeforeAll
    static void applyRecalcSchema() throws Exception {
        PayRunTestSchema.apply();
        // FY 2026-27 slab, rebate, surcharge, cess and standard-deduction rules (V105/V106 carry only the
        // declaration rules). The proof year is today's, so the calculator needs them.
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            if (!TaxDeclarationTestSchema.ruleForYearExists(conn, "standard_deduction_rule_master", "2026-2027")) {
                TaxDeclarationTestSchema.executeResource(conn, "db/migration/reference/V147__fy_2026_27_tax_rules.sql");
            }
        }
    }

    @BeforeEach
    void seedSalary() throws SQLException {
        clearRecalcRows();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID basic = earning("PB" + suffix, "Basic Salary", "BASIC");
        UUID hra = earning("PH" + suffix, "House Rent Allowance", "HRA");
        UUID special = earning("PS" + suffix, "Special Allowance", "ALLOWANCE");

        FinancialYear year = FinancialYear.parse(fy);
        salaryService.create(
                employeeId,
                new SalaryVersionRequest(
                        new BigDecimal("1200000.00"),
                        year.start(),
                        "Proof recalc 12L",
                        List.of(monthly(basic, "50000.00"), monthly(hra, "20000.00"), monthly(special, "30000.00")),
                        List.of(),
                        List.of()));
        awaitRecalcIdle();
    }

    @AfterEach
    void clearSalary() throws SQLException {
        // tax_computation and employee_tds point at the declaration and the employee, which the base teardown
        // deletes; the salary lines point at the earnings. So: recalculation rows, then the base clean-up, then
        // the earnings. The base teardown runs the same clean-up again, which is harmless.
        awaitRecalcIdle();
        clearRecalcRows();
        ProofTestSchema.clearProofs();
        TaxDeclarationTestSchema.clearDeclarations();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM payroll.earning WHERE id = ?")) {
            for (UUID id : earningIds) {
                ps.setObject(1, id);
                ps.executeUpdate();
            }
        }
        earningIds.clear();
    }

    @Test
    @DisplayName("Final approval writes a PROOF_VERIFIED tax_computation row computed on the approved figures")
    void approvingProofRecalculatesTaxOnApprovedFigures() throws SQLException {
        openWindows();
        taxDeclarationService.save(employeeId, fy, new TaxDeclarationRequest("OLD", true, true, false));
        UUID declarationId = declare();

        ProofResponse own = proofService.readOwn(fy);
        ProofItemResponse rent = item(own, ProofSourceKind.HOUSE_RENT);
        ProofItemResponse principal = item(own, ProofSourceKind.HOME_LOAN_PRINCIPAL);
        ProofItemResponse interest = item(own, ProofSourceKind.HOME_LOAN_INTEREST);
        proofService.attachOwn(fy, rent.id(), "rent.pdf", pdf("rent"));
        proofService.attachOwn(fy, principal.id(), "loan_p.pdf", pdf("loan_p"));
        proofService.attachOwn(fy, interest.id(), "loan_i.pdf", pdf("loan_i"));
        proofService.updateItemOwn(fy, rent.id(), new ProofItemUpdateRequest(new BigDecimal("180000.00"), "Rent"));
        proofService.updateItemOwn(
                fy, principal.id(), new ProofItemUpdateRequest(new BigDecimal("50000.00"), "Principal"));
        proofService.updateItemOwn(
                fy, interest.id(), new ProofItemUpdateRequest(new BigDecimal("120000.00"), "Interest"));
        UUID proofId = proofService.submitOwn(fy).id();

        reviewService.decideItem(
                proofId,
                rent.id(),
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("120000.00"), "Part"));
        reviewService.decideItem(
                proofId,
                principal.id(),
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("30000.00"), "Part"));
        reviewService.decideItem(
                proofId,
                interest.id(),
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("100000.00"), "Part"));

        awaitRecalcIdle();
        assertThat(proofVerifiedRows())
                .as("no recalculation before the final approval")
                .isEmpty();

        reviewService.decideFinal(proofId, new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "OK"));
        awaitRecalcIdle();

        List<Row> rows = proofVerifiedRows();
        assertThat(rows).as("one PROOF_VERIFIED tax_computation row").hasSize(1);
        Row row = rows.get(0);
        assertThat(row.declarationId()).isEqualTo(declarationId);
        assertThat(row.regime()).isEqualTo("OLD");
        assertThat(row.hraExemption()).isEqualByComparingTo("60000");
        assertThat(row.chapterVia()).isEqualByComparingTo("30000");
        assertThat(row.housePropertyIncome()).isEqualByComparingTo("-100000");

        // The stored row is the calculator's answer on the approved proof, end to end.
        TaxComputation now = taxCalculationService.compute(employeeId, FinancialYear.parse(fy), TaxRegime.OLD);
        assertThat(row.taxableIncome()).isEqualByComparingTo(now.taxableIncome().raw());
        assertThat(row.annualTax()).isEqualByComparingTo(now.annualTax().raw());
    }

    /** Waits, at most 30 seconds, until every recalculation handed to the pool so far has finished. */
    private static void awaitRecalcIdle() {
        ThreadPoolExecutor executor = pool.getThreadPoolExecutor();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (executor.getCompletedTaskCount() < executor.getTaskCount()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("tax recalculation still running after 30 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for tax recalculation", e);
            }
        }
    }

    private UUID earning(String code, String name, String type) {
        Earning e = new Earning(TENANT_A, "system");
        e.setCode(code);
        e.setName(name);
        e.setEarningType(type);
        e.setCalculationType(CalculationType.FLAT);
        e.setIncludedInCtc(true);
        e.setTaxable(true);
        UUID id = earningRepository.save(e).getId();
        earningIds.add(id);
        return id;
    }

    private static SalaryComponentItemRequest monthly(UUID earningId, String amount) {
        return new SalaryComponentItemRequest(
                earningId, CalculationType.FLAT, new BigDecimal(amount), null, true, "MONTHLY", null);
    }

    private record Row(
            UUID declarationId,
            String regime,
            BigDecimal hraExemption,
            BigDecimal chapterVia,
            BigDecimal housePropertyIncome,
            BigDecimal taxableIncome,
            BigDecimal annualTax) {}

    private List<Row> proofVerifiedRows() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT declaration_id, regime, hra_exemption, chapter_via, house_property_income,
                               taxable_income, annual_tax
                          FROM payroll.tax_computation
                         WHERE tenant_id = ? AND employee_id = ? AND financial_year = ? AND trigger = 'PROOF_VERIFIED'
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId);
            ps.setString(3, fy);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(
                            rs.getObject(1, UUID.class),
                            rs.getString(2),
                            rs.getBigDecimal(3),
                            rs.getBigDecimal(4),
                            rs.getBigDecimal(5),
                            rs.getBigDecimal(6),
                            rs.getBigDecimal(7)));
                }
            }
        }
        return rows;
    }

    private static void clearRecalcRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                Statement st = conn.createStatement()) {
            for (String table : List.of("payroll.employee_tds", "payroll.tax_computation")) {
                st.execute("DELETE FROM " + table + " WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
        }
    }
}
