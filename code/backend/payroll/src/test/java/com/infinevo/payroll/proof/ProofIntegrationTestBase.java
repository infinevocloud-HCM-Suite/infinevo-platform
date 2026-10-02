package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared set-up for the proof-of-investment integration tests (W-34.1).
 *
 * <p>The tests call the services through Spring with a tenant bound and a current employee set, as
 * the tax-declaration tests do. Every test starts with a clean slate and ends by clearing its proof
 * rows first, because other integration tests delete the declarations, employees and documents that
 * proof rows reference (see {@link ProofTestSchema}).
 */
@SpringBootTest(classes = {PayrollTestApp.class, ProofTestDocuments.class})
@RecordApplicationEvents
abstract class ProofIntegrationTestBase extends AbstractIntegrationTest {

    @Autowired
    protected TaxDeclarationWindowService windowService;

    @Autowired
    protected TaxDeclarationService taxDeclarationService;

    @Autowired
    protected ProofService proofService;

    @Autowired
    protected EmployeeService employeeService;

    @Autowired
    protected EmployeeInvHouseRentRepository houseRentRepository;

    @Autowired
    protected EmployeeInvHomeLoanRepository homeLoanRepository;

    @Autowired
    protected NotificationService notificationService;

    @Autowired
    protected ApplicationEvents events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    protected UUID employeeId;
    protected String fy;

    @BeforeAll
    static void applySchema() throws Exception {
        ProofTestSchema.apply();
    }

    @AfterAll
    static void tearDownSchema() throws SQLException {
        ProofTestSchema.clearProofs();
        TaxDeclarationTestSchema.clearAll();
    }

    @BeforeEach
    void setUpBase() throws SQLException {
        TenantContext.clear();
        ProofTestSchema.seedTenants();
        ProofTestSchema.clearProofs();
        TaxDeclarationTestSchema.clearDeclarations();
        Mockito.clearInvocations(notificationService);

        TenantContext.set(TENANT_A);
        fy = FinancialYear.of(today()).label();
        employeeId = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-001", "employee@acme.com", "Jane", "Doe");
        actAs(employeeId);
    }

    @AfterEach
    void cleanUpBase() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        ProofTestSchema.clearProofs();
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    /** Makes {@code id} the logged-in employee, in the tenant that is bound now. */
    protected void actAs(UUID id) {
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(id));
    }

    /**
     * Runs {@code work} in a transaction. A repository finder or a derived delete called from a test needs
     * one: the datasource binds the tenant per transaction and refuses a connection in auto-commit mode.
     */
    protected <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    protected static LocalDate today() {
        return LocalDate.now(TaxDeclarationRules.ZONE);
    }

    /**
     * Sets the year's window: the declaration window around today, and the proof window as given.
     * Dates are clipped to the financial year, which the declaration window must stay within.
     */
    protected void openWindows(LocalDate proofOpens, LocalDate proofDue, boolean attachmentMandatory) {
        FinancialYear year = FinancialYear.parse(fy);
        LocalDate opens = today().minusDays(2).isBefore(year.start()) ? year.start() : today().minusDays(2);
        LocalDate closes = today().plusDays(20).isAfter(year.end()) ? year.end() : today().plusDays(20);
        windowService.upsert(
                fy,
                new TaxDeclarationWindowRequest(
                        opens,
                        closes,
                        false,
                        "NEW",
                        true,
                        true,
                        false,
                        false,
                        proofOpens,
                        proofDue,
                        false,
                        attachmentMandatory,
                        false));
    }

    /** The proof window open from yesterday to ten days out, attachments mandatory. */
    protected void openWindows() {
        openWindows(today().minusDays(1), today().plusDays(10), true);
    }

    /**
     * Declares a year of rent (12 x 15,000 = 180,000) and a home loan (50,000 principal, 120,000
     * interest), then has an officer submit the declaration. That gives three proof items.
     */
    protected UUID declare(UUID forEmployee) {
        UUID tenantId = TenantContext.require();
        FinancialYear year = FinancialYear.parse(fy);
        UUID declarationId = taxDeclarationService.read(forEmployee, fy).id();
        houseRentRepository.save(new EmployeeInvHouseRent(
                tenantId,
                declarationId,
                LocalDate.of(year.startYear(), 4, 1),
                LocalDate.of(year.endYear(), 3, 1),
                "1 Test Street",
                "A. Landlord",
                null,
                false,
                new BigDecimal("15000")));
        homeLoanRepository.save(new EmployeeInvHomeLoan(
                tenantId,
                declarationId,
                "Test Bank",
                null,
                new BigDecimal("50000"),
                new BigDecimal("120000"),
                false,
                null));
        taxDeclarationService.submit(forEmployee, fy);
        return declarationId;
    }

    protected UUID declare() {
        return declare(employeeId);
    }

    protected static ByteArrayInputStream pdf(String text) {
        return new ByteArrayInputStream(("%PDF-1.4 " + text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected static ProofItemResponse item(ProofResponse proof, ProofSourceKind kind) {
        return proof.items().stream()
                .filter(i -> i.sourceKind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " item in " + proof.items()));
    }
}
