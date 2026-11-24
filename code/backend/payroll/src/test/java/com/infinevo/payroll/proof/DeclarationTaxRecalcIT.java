package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import com.infinevo.payroll.taxcalc.recalc.TaxRecalcFailureResponse;
import com.infinevo.payroll.taxcalc.recalc.TaxRecalcFailureService;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.tds.EmployeeTdsServiceBridge;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * D-147, against real PostgreSQL and the production executor shape (as {@link ProofTaxRecalcIT}).
 *
 * <ul>
 *   <li>End to end: the employee submits their declaration; after commit the recalculation writes a
 *       DECLARATION_SUBMITTED {@code tax_computation} row and an active {@code employee_tds} record, source
 *       DECLARATION, carrying the declaration.</li>
 *   <li>A failure in the TDS write rolls the whole recalculation back - no {@code tax_computation} row, no tax
 *       summary, no new TDS record - and one open {@code tax_recalc_failure} row says why. The employee's
 *       submit stands.</li>
 *   <li>The officer's retry, once the cause is gone, writes everything and closes the failure.</li>
 * </ul>
 *
 * <p>It lives beside {@link ProofTaxRecalcIT} to share {@link ProofIntegrationTestBase}'s set-up.
 */
// The test EmployeeService opens its own connection inside a service transaction, and the recalculation holds one
// on the pool thread: two connections are too few (as ProofSubmissionIT).
@org.springframework.test.context.TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class DeclarationTaxRecalcIT extends ProofIntegrationTestBase {

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private TaxRecalcFailureService failureService;

    /** The real bridge, so a test can make its record() throw inside the recalculation's transaction. */
    @MockitoSpyBean
    private EmployeeTdsServiceBridge tdsBridge;

    @TestBean(name = "taxRecalc", methodName = "pooledTaxRecalc")
    private Executor taxRecalc;

    private static ThreadPoolTaskExecutor pool;

    private final List<UUID> earningIds = new ArrayList<>();

    static Executor pooledTaxRecalc() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setThreadNamePrefix("tax-recalc-d147-");
        ex.initialize();
        pool = ex;
        return ex;
    }

    @BeforeAll
    static void applyRecalcSchema() throws Exception {
        PayRunTestSchema.apply();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            if (!TaxDeclarationTestSchema.ruleForYearExists(conn, "standard_deduction_rule_master", "2026-2027")) {
                TaxDeclarationTestSchema.executeResource(conn, "db/migration/reference/V147__fy_2026_27_tax_rules.sql");
            }
            if (!TaxDeclarationTestSchema.tableExists(conn, "payroll", "tax_recalc_failure")) {
                TaxDeclarationTestSchema.executeResource(conn, "db/migration/payroll/V222__tax_recalc_failure.sql");
            }
        }
    }

    @BeforeEach
    void seedSalary() throws SQLException {
        Mockito.reset(tdsBridge);
        clearRecalcRows();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID basic = earning("DB" + suffix, "Basic Salary", "BASIC");
        UUID hra = earning("DH" + suffix, "House Rent Allowance", "HRA");
        UUID special = earning("DS" + suffix, "Special Allowance", "ALLOWANCE");

        FinancialYear year = FinancialYear.parse(fy);
        salaryService.create(
                employeeId,
                new SalaryVersionRequest(
                        new BigDecimal("1200000.00"),
                        year.start(),
                        "D-147 12L",
                        List.of(monthly(basic, "50000.00"), monthly(hra, "20000.00"), monthly(special, "30000.00")),
                        List.of(),
                        List.of()));
        awaitRecalcIdle();
    }

    @AfterEach
    void clearSalary() throws SQLException {
        awaitRecalcIdle();
        Mockito.reset(tdsBridge);
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
    @DisplayName("Salary default: the TDS record is the calculator's (DECLARATION), not OFFICER")
    void salaryDefaultIsCalculatorSource() throws SQLException {
        List<Tds> tds = tdsRows();
        assertThat(tds).as("the salary-default recalculation wrote one record").hasSize(1);
        assertThat(tds.get(0).source()).isEqualTo("DECLARATION");
        assertThat(tds.get(0).declarationId()).isNull();
        assertThat(tds.get(0).note()).startsWith("SALARY_DEFAULT ");
    }

    @Test
    @DisplayName("End to end: the employee's submit leads to a DECLARATION_SUBMITTED computation and TDS record")
    void submitWritesComputationAndTds() throws SQLException {
        openWindows();
        taxDeclarationService.saveOwn(fy, new TaxDeclarationRequest("OLD", false, false, false));
        UUID declarationId = taxDeclarationService.submitOwn(fy).id();
        awaitRecalcIdle();

        assertThat(countComputations("DECLARATION_SUBMITTED")).isEqualTo(1);
        Tds active = activeTds();
        assertThat(active.declarationId()).isEqualTo(declarationId);
        assertThat(active.source()).isEqualTo("DECLARATION");
        assertThat(active.regime()).isEqualTo("OLD");
        // No pay run exists, so no earlier month is open: the record starts this month (D-147 floor).
        assertThat(active.effectiveFrom()).isEqualTo(currentPayrollMonth());
        assertThat(active.taxable()).isLessThanOrEqualTo(active.gross());
        assertThat(countSummaries(declarationId)).isEqualTo(1);
        assertThat(failureService.open(fy)).isEmpty();
    }

    @Test
    @DisplayName("A TDS write failure rolls the recalculation back whole and is recorded; the officer's retry fixes it")
    void failureIsAtomicVisibleAndRetryable() throws SQLException {
        openWindows();
        taxDeclarationService.saveOwn(fy, new TaxDeclarationRequest("OLD", false, false, false));
        doThrow(new IllegalStateException("forced TDS failure")).when(tdsBridge).record(any(), any(), any());

        UUID declarationId = taxDeclarationService.submitOwn(fy).id();
        awaitRecalcIdle();

        // The employee's submit stands.
        assertThat(inTransaction(
                        () -> taxDeclarationService.require(employeeId, fy).getStatus()))
                .isEqualTo(DeclarationStatus.SUBMITTED);
        // Nothing partial: no computation row, no tax summary, no new TDS record.
        assertThat(countComputations("DECLARATION_SUBMITTED")).isZero();
        assertThat(countSummaries(declarationId)).isZero();
        List<Tds> tds = tdsRows();
        assertThat(tds).as("only the salary-default record").hasSize(1);
        assertThat(tds.get(0).declarationId()).isNull();
        // The failure is recorded where the officer can see it.
        List<TaxRecalcFailureResponse> open = failureService.open(fy);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).employeeId()).isEqualTo(employeeId);
        assertThat(open.get(0).trigger().name()).isEqualTo("DECLARATION_SUBMITTED");
        assertThat(open.get(0).attempts()).isEqualTo(1);
        assertThat(open.get(0).errorMessage()).contains("forced TDS failure");

        // The cause goes; the officer retries.
        Mockito.reset(tdsBridge);
        TaxComputationRecord retried = failureService.retry(open.get(0).id(), null);

        assertThat(retried.getDeclarationId()).isEqualTo(declarationId);
        assertThat(countComputations("DECLARATION_SUBMITTED")).isEqualTo(1);
        assertThat(countSummaries(declarationId)).isEqualTo(1);
        assertThat(activeTds().declarationId()).isEqualTo(declarationId);
        assertThat(failureService.open(fy)).isEmpty();
        assertThat(resolvedFailures()).isEqualTo(1);
    }

    private static String currentPayrollMonth() {
        return YearMonth.from(today()).toString();
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

    private record Tds(
            UUID declarationId,
            String source,
            String regime,
            String effectiveFrom,
            String note,
            BigDecimal gross,
            BigDecimal taxable,
            boolean active) {}

    private List<Tds> tdsRows() throws SQLException {
        List<Tds> rows = new ArrayList<>();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT declaration_id, source, regime, effective_from_period, note,
                               annual_gross, annual_taxable_income, superseded_at IS NULL
                          FROM payroll.employee_tds
                         WHERE tenant_id = ? AND employee_id = ? AND financial_year = ?
                         ORDER BY created_at
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId);
            ps.setString(3, fy);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Tds(
                            rs.getObject(1, UUID.class),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getBigDecimal(6),
                            rs.getBigDecimal(7),
                            rs.getBoolean(8)));
                }
            }
        }
        return rows;
    }

    private Tds activeTds() throws SQLException {
        List<Tds> active = tdsRows().stream().filter(Tds::active).toList();
        assertThat(active).as("one active TDS record").hasSize(1);
        return active.get(0);
    }

    private int countComputations(String trigger) throws SQLException {
        return count("SELECT count(*) FROM payroll.tax_computation WHERE tenant_id = ? AND employee_id = ? "
                + "AND financial_year = ? AND trigger = '" + trigger + "'");
    }

    private int resolvedFailures() throws SQLException {
        return count("SELECT count(*) FROM payroll.tax_recalc_failure WHERE tenant_id = ? AND employee_id = ? "
                + "AND financial_year = ? AND resolved_at IS NOT NULL");
    }

    private int count(String sql) throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeId);
            ps.setString(3, fy);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countSummaries(UUID declarationId) throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.employee_inv_tax_summary WHERE declaration_id = ?")) {
            ps.setObject(1, declarationId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void clearRecalcRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                Statement st = conn.createStatement()) {
            for (String table :
                    List.of("payroll.employee_tds", "payroll.tax_computation", "payroll.tax_recalc_failure")) {
                st.execute("DELETE FROM " + table + " WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
        }
    }
}
