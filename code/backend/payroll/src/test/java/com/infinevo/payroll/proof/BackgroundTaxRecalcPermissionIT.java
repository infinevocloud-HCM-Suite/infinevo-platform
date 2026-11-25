package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.detail.EmployeePersonalRequest;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.taxcalc.recalc.TaxRecalcFailureService;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * D-154: the automatic tax recalculation must not depend on the permissions of whoever triggered it.
 *
 * <p>Before the fix {@code TaxInputAssembler} read the date of birth through
 * {@link EmployeePersonalService#get(UUID)}, which refuses a caller without {@code core.employee.read} unless the
 * caller is the employee themself. The recalculation runs on the {@code taxRecalc} pool, where there is no user
 * at all, so it failed and landed in {@code payroll.tax_recalc_failure}. Here the personal service's {@code get}
 * applies exactly that rule for a caller holding no {@code core.employee.read}, the pool is a real thread pool (as
 * in production, so the triggering user's thread-bound context does not reach it), and:
 *
 * <ul>
 *   <li>a salary revision by a payroll user who is not the employee recalculates, with the date of birth read, and
 *       records no failure;</li>
 *   <li>the employee's own declaration submit recalculates on the pool thread, with no user there, and records no
 *       failure.</li>
 * </ul>
 *
 * <p>The refusal on the pool thread is asserted too, so the test cannot pass for want of a check.
 */
// The test EmployeeService opens its own connection inside a service transaction, and the recalculation holds one
// on the pool thread: two connections are too few (as DeclarationTaxRecalcIT).
@org.springframework.test.context.TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class BackgroundTaxRecalcPermissionIT extends ProofIntegrationTestBase {

    private static final LocalDate SENIOR_DOB = LocalDate.of(1960, 5, 15);

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private TaxRecalcFailureService failureService;

    /** The test application's personal service, its get() made to refuse as core's does without the permission. */
    @MockitoSpyBean
    private EmployeePersonalService personalService;

    @TestBean(name = "taxRecalc", methodName = "pooledTaxRecalc")
    private Executor taxRecalc;

    private static ThreadPoolTaskExecutor pool;

    private final List<UUID> earningIds = new ArrayList<>();

    private UUID officerId;

    static Executor pooledTaxRecalc() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setThreadNamePrefix("tax-recalc-d154-");
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
    void setUp() throws SQLException {
        clearRecalcRows();
        // core's EmployeePersonalServiceImpl.get for a caller without core.employee.read: only the employee themself.
        doAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    Optional<EmployeeResponse> me = employeeService.currentEmployee();
                    if (me.isEmpty() || !me.get().id().equals(id)) {
                        throw new PermissionDeniedException("core.employee.read");
                    }
                    return invocation.callRealMethod();
                })
                .when(personalService)
                .get(any());
        personalService.put(employeeId, new EmployeePersonalRequest(SENIOR_DOB, null, null, null, null, null, false));
        officerId = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-OFF", "officer@acme.com", "Olu", "Officer");
        clearInvocations(personalService);
    }

    @AfterEach
    void tearDown() throws SQLException {
        awaitRecalcIdle();
        clearRecalcRows();
        ProofTestSchema.clearProofs();
        TaxDeclarationTestSchema.clearDeclarations();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM payroll.earning WHERE id = ?")) {
                for (UUID id : earningIds) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }
            }
        }
        earningIds.clear();
    }

    @Test
    @DisplayName("Control: on the taxRecalc pool thread there is no user, and the user-facing get refuses")
    void poolThreadHasNoUserAndGetRefuses() {
        UUID id = employeeId;
        var future = pool.submit(() -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            TenantContext.set(TENANT_A);
            try {
                return personalService.get(id);
            } finally {
                TenantContext.clear();
            }
        });
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("core.employee.read");
    }

    @Test
    @DisplayName("Salary revision by a payroll user who is not the employee: recalculated, no failure recorded")
    void salaryRevisionByAnotherUserRecalculates() throws SQLException {
        openWindows();
        // A draft declaration, so the salary change is recalculated as SALARY_REVISION rather than SALARY_DEFAULT.
        taxDeclarationService.saveOwn(fy, new TaxDeclarationRequest("OLD", false, false, false));
        awaitRecalcIdle();

        actAs(officerId);
        createSalary();
        awaitRecalcIdle();

        assertThat(failureService.open(fy)).as("no open tax_recalc_failure").isEmpty();
        assertThat(countFailures()).as("no tax_recalc_failure row at all").isZero();
        assertThat(countComputations("SALARY_REVISION")).isGreaterThanOrEqualTo(1);
        verify(personalService, atLeastOnce()).dateOfBirth(employeeId);
        verify(personalService, never()).get(any());
    }

    @Test
    @DisplayName("Declaration submit by the employee: recalculated on the pool with no user, no failure recorded")
    void declarationSubmitRecalculates() throws SQLException {
        actAs(officerId);
        createSalary();
        awaitRecalcIdle();

        actAs(employeeId);
        openWindows();
        taxDeclarationService.saveOwn(fy, new TaxDeclarationRequest("OLD", false, false, false));
        taxDeclarationService.submitOwn(fy);
        awaitRecalcIdle();

        assertThat(failureService.open(fy)).as("no open tax_recalc_failure").isEmpty();
        assertThat(countFailures()).as("no tax_recalc_failure row at all").isZero();
        assertThat(countComputations("DECLARATION_SUBMITTED")).isEqualTo(1);
        verify(personalService, atLeastOnce()).dateOfBirth(employeeId);
        verify(personalService, never()).get(any());
    }

    private void createSalary() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID basic = earning("PB" + suffix, "Basic Salary", "BASIC");
        UUID hra = earning("PH" + suffix, "House Rent Allowance", "HRA");
        UUID special = earning("PS" + suffix, "Special Allowance", "ALLOWANCE");
        salaryService.create(
                employeeId,
                new SalaryVersionRequest(
                        new BigDecimal("1200000.00"),
                        FinancialYear.parse(fy).start(),
                        "D-154 12L",
                        List.of(monthly(basic, "50000.00"), monthly(hra, "20000.00"), monthly(special, "30000.00")),
                        List.of(),
                        List.of()));
    }

    /** Waits, at most 30 seconds, until every recalculation handed to the pool so far has finished. */
    private static void awaitRecalcIdle() {
        ThreadPoolExecutor executor = pool.getThreadPoolExecutor();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        int idleChecks = 0;
        while (idleChecks < 10) {
            idleChecks = executor.getCompletedTaskCount() < executor.getTaskCount() ? 0 : idleChecks + 1;
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

    private int countComputations(String trigger) throws SQLException {
        return count("SELECT count(*) FROM payroll.tax_computation WHERE tenant_id = ? AND employee_id = ? "
                + "AND financial_year = ? AND trigger = '" + trigger + "'");
    }

    private int countFailures() throws SQLException {
        return count("SELECT count(*) FROM payroll.tax_recalc_failure WHERE tenant_id = ? AND employee_id = ? "
                + "AND financial_year = ?");
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
