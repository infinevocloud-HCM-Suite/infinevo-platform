package com.infinevo.payroll.tds;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRepository;
import com.infinevo.payroll.taxcalc.recalc.TaxTrigger;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.tds.dto.EmployeeTdsResponse;
import com.infinevo.payroll.tds.dto.RecordTdsRequest;
import com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
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

/**
 * D-151 against real Postgres (V223): an officer's TDS figure is not replaced by an automatic recalculation.
 * The recalculation reaches the record through the same bridge {@code TaxRecalculationServiceImpl} calls;
 * its figure is kept as a suggestion the officer applies or clears, and the active figure the pay run reads
 * stays the officer's.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeTdsSuggestionIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private EmployeeTdsService tdsService;

    @Autowired
    private com.infinevo.payroll.taxcalc.recalc.EmployeeTdsService recalcBridge;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TaxComputationRepository taxComputationRepository;

    private EmployeeTdsController controller;
    private UUID employeeId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();
        employeeId = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-SUG-01", "sug@acme.com", "Sita", "Iyer");
        TenantContext.set(TENANT_A);
        controller = new EmployeeTdsController(tdsService, employeeService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    private void officerSets(String annualTax) {
        controller.put(
                employeeId,
                FY,
                new RecordTdsRequest(
                        "NEW",
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal(annualTax),
                        "2026-04",
                        "officer figure"));
    }

    private void recalculationWrites(String annualTax) {
        recalcBridge.record(
                employeeId,
                FY,
                new com.infinevo.payroll.taxcalc.recalc.EmployeeTdsService.TdsFigures(
                        "OLD",
                        new BigDecimal("1300000.00"),
                        new BigDecimal("1100000.00"),
                        new BigDecimal(annualTax),
                        null,
                        "SALARY_REVISION " + UUID.randomUUID()));
    }

    @Test
    @DisplayName("A recalculation after an officer's figure leaves it active and stores a suggestion")
    void recalculationKeepsOfficerFigure() {
        officerSets("120000.00");

        recalculationWrites("90000.00");

        EmployeeTds active = tdsService.active(employeeId, FY).orElseThrow();
        assertThat(active.getSource()).isEqualTo(TdsSource.OFFICER);
        assertThat(active.getAnnualTax()).isEqualByComparingTo("120000.00");
        assertThat(active.getSuggestedAnnualTax()).isEqualByComparingTo("90000.00");
        assertThat(active.getSuggestedRegime()).isEqualTo(TaxRegime.OLD);
        assertThat(tdsService.history(employeeId, FY)).hasSize(1);

        ApiResponse<EmployeeTdsResponse> got = controller.get(employeeId, FY);
        assertThat(got.data().annualTax()).isEqualByComparingTo("120000.00");
        assertThat(got.data().suggestion()).isNotNull();
        assertThat(got.data().suggestion().annualTax()).isEqualByComparingTo("90000.00");

        // A second recalculation replaces the suggestion, never the figure.
        recalculationWrites("95000.00");
        EmployeeTds again = tdsService.active(employeeId, FY).orElseThrow();
        assertThat(again.getAnnualTax()).isEqualByComparingTo("120000.00");
        assertThat(again.getSuggestedAnnualTax()).isEqualByComparingTo("95000.00");

        // The employee's own view carries no suggestion.
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));
        assertThat(controller.getOwn(FY).data().suggestion()).isNull();
    }

    @Test
    @DisplayName("Applying the suggestion supersedes the officer's row with a DECLARATION row of the suggested figures")
    void applySuggestion() {
        officerSets("120000.00");
        recalculationWrites("90000.00");

        ApiResponse<EmployeeTdsResponse> applied = controller.applySuggestion(employeeId, FY);

        assertThat(applied.data().source()).isEqualTo("DECLARATION");
        assertThat(applied.data().annualTax()).isEqualByComparingTo("90000.00");
        assertThat(applied.data().regime()).isEqualTo("OLD");
        assertThat(applied.data().suggestion()).isNull();
        List<EmployeeTds> rows = tdsService.history(employeeId, FY);
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(EmployeeTds::isActive).hasSize(1);
        assertThat(rows)
                .filteredOn(r -> !r.isActive())
                .singleElement()
                .extracting(EmployeeTds::getSource)
                .isEqualTo(TdsSource.OFFICER);

        // From here a recalculation supersedes as it always did.
        recalculationWrites("91000.00");
        assertThat(tdsService.active(employeeId, FY).orElseThrow().getAnnualTax())
                .isEqualByComparingTo("91000.00");
        assertThat(tdsService.history(employeeId, FY)).hasSize(3);
    }

    @Test
    @DisplayName("Clearing the suggestion keeps the officer's figure; with nothing to apply, apply is 404")
    void clearSuggestion() {
        officerSets("120000.00");
        recalculationWrites("90000.00");

        ApiResponse<EmployeeTdsResponse> cleared = controller.clearSuggestion(employeeId, FY);

        assertThat(cleared.data().suggestion()).isNull();
        assertThat(cleared.data().annualTax()).isEqualByComparingTo("120000.00");
        assertThat(cleared.data().source()).isEqualTo("OFFICER");
        assertThat(tdsService.active(employeeId, FY).orElseThrow().hasSuggestion())
                .isFalse();
        assertThat(tdsService.history(employeeId, FY)).hasSize(1);
        assertThatThrownBy(() -> controller.applySuggestion(employeeId, FY))
                .isInstanceOf(EmployeeTdsNotFoundException.class);
    }

    @Test
    @DisplayName("An officer's new figure still supersedes, and starts with no suggestion")
    void officerPutStillSupersedes() {
        officerSets("120000.00");
        recalculationWrites("90000.00");

        officerSets("130000.00");

        EmployeeTds active = tdsService.active(employeeId, FY).orElseThrow();
        assertThat(active.getAnnualTax()).isEqualByComparingTo("130000.00");
        assertThat(active.hasSuggestion()).isFalse();
        assertThat(tdsService.history(employeeId, FY)).hasSize(2);
    }

    @Test
    @DisplayName("With no officer figure, a recalculation supersedes the calculator's row as before")
    void calculatorRowIsSuperseded() {
        recalculationWrites("90000.00");
        recalculationWrites("95000.00");

        EmployeeTds active = tdsService.active(employeeId, FY).orElseThrow();
        assertThat(active.getAnnualTax()).isEqualByComparingTo("95000.00");
        assertThat(active.hasSuggestion()).isFalse();
        assertThat(tdsService.history(employeeId, FY)).hasSize(2);
    }

    @Test
    @DisplayName("V223 relabels a calculator figure mislabelled OFFICER before D-147, and nothing an officer typed")
    void v223RelabelsOnlyCalculatorRowsMislabelledOfficer() throws Exception {
        UUID calculatorEmployee = employeeId;
        UUID typedEmployee =
                TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-SUG-02", "typed@acme.com", "Ravi", "Nair");
        UUID strayEmployee =
                TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-SUG-03", "stray@acme.com", "Meera", "Das");
        UUID computationId = taxComputationRepository
                .save(computationFor(calculatorEmployee))
                .getId();

        // As the calculator wrote a salary-default figure before D-147: no declaration, so OFFICER.
        mislabelled(calculatorEmployee, "SALARY_DEFAULT " + computationId);
        mislabelled(typedEmployee, "officer figure");
        // Calculator-shaped note, but the id is no computation of this employee: left alone.
        mislabelled(strayEmployee, "SALARY_REVISION " + UUID.randomUUID());

        String migration = new String(
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("db/migration/payroll/V223__tds_suggestion_and_reopen_month.sql")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        String relabel = migration.substring(migration.indexOf("UPDATE payroll.employee_tds"));
        try (Connection conn = PayrollTestSchema.migrationConnection();
                Statement st = conn.createStatement()) {
            st.execute(relabel);
        }

        assertThat(tdsService.active(calculatorEmployee, FY).orElseThrow().getSource())
                .isEqualTo(TdsSource.DECLARATION);
        assertThat(tdsService.active(typedEmployee, FY).orElseThrow().getSource())
                .isEqualTo(TdsSource.OFFICER);
        assertThat(tdsService.active(strayEmployee, FY).orElseThrow().getSource())
                .isEqualTo(TdsSource.OFFICER);
    }

    private void mislabelled(UUID employee, String note) {
        tdsService.record(
                employee,
                FY,
                new TdsFigures(
                        TaxRegime.NEW,
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("80000.00"),
                        "2026-04",
                        null,
                        note,
                        TdsSource.OFFICER));
    }

    private static TaxComputationRecord computationFor(UUID employee) {
        return new TaxComputationRecord(
                TENANT_A,
                employee,
                null,
                FY,
                "NEW",
                TaxTrigger.OFFICER,
                new BigDecimal("1200000.00"),
                BigDecimal.ZERO,
                new BigDecimal("75000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000000.00"),
                new BigDecimal("80000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("80000.00"),
                "{}",
                Instant.now(),
                null,
                "test");
    }
}
