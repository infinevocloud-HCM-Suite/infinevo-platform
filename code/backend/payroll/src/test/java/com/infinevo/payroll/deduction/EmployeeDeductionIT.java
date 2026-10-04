package com.infinevo.payroll.deduction;

import static com.infinevo.payroll.deduction.DeductionTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

/**
 * W-35.2 §7 — a batch is one deduction row and one {@code AD_HOC_DEDUCTION} ledger row per line, all
 * or nothing; a reversal is one ledger reversal and the row {@code REVERSED}, never deleted; a second
 * reversal is refused; the employee reads only their own.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeDeductionIT extends AbstractIntegrationTest {

    /** The service decides "now" by the Indian month, so the test does too. */
    private static final YearMonth THIS_MONTH = YearMonth.now(ZoneId.of("Asia/Kolkata"));

    @Autowired
    private EmployeeDeductionService deductionService;

    @Autowired
    private PayInputService payInputService;

    private UUID alice;
    private UUID bob;

    @BeforeAll
    static void applySchema() throws Exception {
        DeductionTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        DeductionTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        DeductionTestSchema.clean();
        TenantContext.set(TENANT_A);
        alice = DeductionTestSchema.insertEmployee(TENANT_A, "D-01", "ACTIVE");
        bob = DeductionTestSchema.insertEmployee(TENANT_A, "D-02", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    @Test
    @DisplayName("Three lines for two employees: three POSTED rows and three AD_HOC_DEDUCTION ledger rows")
    void batchWritesRowsAndLedger() throws SQLException {
        EmployeeDeductionBatchResponse batch = deductionService.enter(List.of(
                line(alice, "ADVANCE_RECOVERY", "1500.00"),
                line(alice, "PENALTY", "200"),
                line(bob, "DAMAGE", "750.25")));

        assertThat(batch.count()).isEqualTo(3);
        assertThat(batch.rows()).allSatisfy(row -> {
            assertThat(row.status()).isEqualTo(DeductionState.POSTED);
            assertThat(row.period()).isEqualTo(THIS_MONTH.toString());
            assertThat(row.postedPeriod()).isEqualTo(THIS_MONTH.toString());
            assertThat(row.payInputId()).isNotNull();
        });
        assertThat(DeductionTestSchema.deductionRows(TENANT_A)).isEqualTo(3);
        assertThat(DeductionTestSchema.ledgerRows(TENANT_A)).isEqualTo(3);

        List<PayInputResponse> ledger = payInputService.forPeriod(THIS_MONTH).rows();
        for (EmployeeDeductionResponse row : batch.rows()) {
            PayInputResponse posted = ledger.stream()
                    .filter(p -> p.id().equals(row.payInputId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(posted.kind()).isEqualTo(PayInputKind.AD_HOC_DEDUCTION);
            assertThat(posted.sourceModule()).isEqualTo("payroll");
            assertThat(posted.sourceRef()).isEqualTo("employee_deduction:" + row.id());
            assertThat(posted.employeeId()).isEqualTo(row.employeeId());
            assertThat(posted.amount()).isEqualByComparingTo(row.amount());
        }
        assertThat(deductionService.get(batch.rows().get(2).id()).deductionType())
                .isEqualTo(DeductionType.DAMAGE);
    }

    @Test
    @DisplayName("A batch whose third line is invalid writes nothing, in either table")
    void badThirdLineWritesNothing() throws SQLException {
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> deductionService.enter(List.of(
                        line(alice, "OTHER", "100"), line(bob, "OTHER", "100"), line(stranger, "OTHER", "100"))))
                .isInstanceOfSatisfying(EmployeeDeductionValidationException.class, e -> assertThat(e.line())
                        .isEqualTo(2));
        assertThatThrownBy(() -> deductionService.enter(
                        List.of(line(alice, "OTHER", "100"), line(bob, "OTHER", "100"), line(bob, "OTHER", "-1"))))
                .isInstanceOfSatisfying(EmployeeDeductionValidationException.class, e -> assertThat(e.line())
                        .isEqualTo(2));

        assertThat(DeductionTestSchema.deductionRows(TENANT_A)).isZero();
        assertThat(DeductionTestSchema.ledgerRows(TENANT_A)).isZero();
    }

    @Test
    @DisplayName("A terminated employee is refused")
    void inactiveEmployeeRefused() throws SQLException {
        UUID leaver = DeductionTestSchema.insertEmployee(TENANT_A, "D-09", "TERMINATED");

        assertThatThrownBy(() -> deductionService.enter(List.of(line(leaver, "OTHER", "100"))))
                .isInstanceOf(EmployeeDeductionValidationException.class)
                .hasMessageContaining("TERMINATED");
        assertThat(DeductionTestSchema.ledgerRows(TENANT_A)).isZero();
    }

    @Test
    @DisplayName("Reverse: one ledger reversal with reverses_id, the row REVERSED and kept; a second reverse is 409")
    void reverseNetsOut() throws SQLException {
        UUID officer = DeductionTestSchema.insertEmployee(TENANT_A, "OFF-1", "ACTIVE");
        EmployeeDeductionResponse row = deductionService
                .enter(List.of(line(alice, "ADVANCE_RECOVERY", "1500")))
                .rows()
                .get(0);
        PayrollTestApp.CURRENT_EMPLOYEE.set(PayrollTestSchema.createTestEmployee(
                officer, TENANT_A, "OFF-1", "Payroll", "Officer", "officer@example.com"));

        EmployeeDeductionResponse reversed = deductionService.reverse(row.id(), " entered twice ");

        assertThat(reversed.status()).isEqualTo(DeductionState.REVERSED);
        assertThat(reversed.reversalPayInputId()).isNotNull();
        assertThat(reversed.reversedAt()).isNotNull();
        assertThat(reversed.reversedBy()).isEqualTo(officer);
        assertThat(DeductionTestSchema.deductionRows(TENANT_A)).isEqualTo(1);
        assertThat(DeductionTestSchema.count(
                        "SELECT count(*) FROM core.pay_input WHERE id = ? AND reverses_id = ?",
                        reversed.reversalPayInputId(),
                        row.payInputId()))
                .isEqualTo(1);
        // Netted out: what the pay run reads for Alice sums to zero.
        BigDecimal net = payInputService.forPeriod(THIS_MONTH).rows().stream()
                .filter(p -> p.employeeId().equals(alice))
                .map(p -> p.reversesId() == null ? p.amount() : p.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net).isEqualByComparingTo("0");

        assertThatThrownBy(() -> deductionService.reverse(row.id(), "again"))
                .isInstanceOf(DeductionAlreadyReversedException.class);
        assertThatThrownBy(() -> deductionService.reverse(row.id(), " "))
                .isInstanceOf(EmployeeDeductionValidationException.class);
        assertThatThrownBy(() -> deductionService.reverse(UUID.randomUUID(), "x"))
                .isInstanceOf(EmployeeDeductionNotFoundException.class);
    }

    @Test
    @DisplayName("GET /me shows only the caller's rows, reversed ones included; no linked employee is 403")
    void ownRowsOnly() {
        List<EmployeeDeductionResponse> rows = deductionService
                .enter(List.of(line(alice, "OTHER", "100"), line(bob, "OTHER", "200"), line(alice, "PENALTY", "50")))
                .rows();
        deductionService.reverse(rows.get(0).id(), "mistake");
        PayrollTestApp.CURRENT_EMPLOYEE.set(
                PayrollTestSchema.createTestEmployee(alice, TENANT_A, "D-01", "Alice", "A", "alice@example.com"));

        List<EmployeeDeductionResponse> own = deductionService.listOwn();

        assertThat(own).hasSize(2).allSatisfy(r -> assertThat(r.employeeId()).isEqualTo(alice));
        assertThat(own)
                .extracting(EmployeeDeductionResponse::status)
                .containsExactlyInAnyOrder(DeductionState.REVERSED, DeductionState.POSTED);

        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        assertThatThrownBy(() -> deductionService.listOwn()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("A proof must be the employee's own EMPLOYEE_DOCUMENT; anything else is refused")
    void proofDocument() throws SQLException {
        UUID own = DeductionTestSchema.insertDocument(TENANT_A, alice, DocumentKind.EMPLOYEE_DOCUMENT);
        UUID bobs = DeductionTestSchema.insertDocument(TENANT_A, bob, DocumentKind.EMPLOYEE_DOCUMENT);
        UUID receipt = DeductionTestSchema.insertDocument(TENANT_A, alice, DocumentKind.REIMBURSEMENT_RECEIPT);

        EmployeeDeductionResponse withProof = deductionService
                .enter(List.of(lineWithDocument(alice, own)))
                .rows()
                .get(0);
        assertThat(withProof.documentId()).isEqualTo(own);

        assertThatThrownBy(() -> deductionService.enter(List.of(lineWithDocument(alice, bobs))))
                .isInstanceOf(EmployeeDeductionValidationException.class)
                .hasMessageContaining("does not belong");
        assertThatThrownBy(() -> deductionService.enter(List.of(lineWithDocument(alice, receipt))))
                .isInstanceOf(EmployeeDeductionValidationException.class)
                .hasMessageContaining("EMPLOYEE_DOCUMENT");
        assertThatThrownBy(() -> deductionService.enter(List.of(lineWithDocument(alice, UUID.randomUUID()))))
                .isInstanceOf(EmployeeDeductionValidationException.class)
                .hasMessageContaining("no document");
        assertThat(DeductionTestSchema.deductionRows(TENANT_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("Two identical lines are two deductions; the list filters by employee, period, status and type")
    void duplicatesAndFilters() {
        List<EmployeeDeductionResponse> rows = deductionService
                .enter(List.of(
                        line(alice, "ADVANCE_RECOVERY", "100"),
                        line(alice, "ADVANCE_RECOVERY", "100"),
                        line(bob, "LOAN_RECOVERY", "300")))
                .rows();
        deductionService.reverse(rows.get(2).id(), "wrong employee");
        PageRequest page = PageRequest.of(0, 25);

        assertThat(deductionService.list(alice, null, null, null, page).getTotalElements())
                .isEqualTo(2);
        assertThat(deductionService
                        .list(null, THIS_MONTH, DeductionState.REVERSED, null, page)
                        .getContent())
                .extracting(EmployeeDeductionResponse::id)
                .containsExactly(rows.get(2).id());
        assertThat(deductionService
                        .list(null, null, null, DeductionType.ADVANCE_RECOVERY, page)
                        .getTotalElements())
                .isEqualTo(2);
        assertThat(deductionService
                        .list(null, THIS_MONTH.minusMonths(3), null, null, page)
                        .getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("W-47.4: employee_name is on the batch rows, the list, the read, the reversal and the own list")
    void rowsCarryEmployeeName() {
        List<EmployeeDeductionResponse> batch = deductionService
                .enter(List.of(line(alice, "OTHER", "100"), line(bob, "PENALTY", "50")))
                .rows();
        assertThat(batch)
                .extracting(EmployeeDeductionResponse::employeeName)
                .containsExactly("First Last", "First Last");
        assertThat(batch).extracting(EmployeeDeductionResponse::employeeId).containsExactly(alice, bob);

        assertThat(deductionService
                        .list(null, null, null, null, PageRequest.of(0, 25))
                        .getContent())
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.employeeName()).isEqualTo("First Last"));
        assertThat(deductionService.get(batch.get(0).id()).employeeName()).isEqualTo("First Last");
        assertThat(deductionService.reverse(batch.get(1).id(), "mistake").employeeName())
                .isEqualTo("First Last");

        PayrollTestApp.CURRENT_EMPLOYEE.set(
                PayrollTestSchema.createTestEmployee(alice, TENANT_A, "D-01", "Alice", "A", "alice@example.com"));
        assertThat(deductionService.listOwn()).singleElement().satisfies(r -> assertThat(r.employeeName())
                .isEqualTo("First Last"));
    }

    @Test
    @DisplayName("V101: RLS on, money and periods typed as the spec says, the three indexes present")
    void migrationShape() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT relrowsecurity FROM pg_class WHERE oid = 'payroll.employee_deduction'::regclass")) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT column_name, data_type, numeric_precision, numeric_scale "
                            + "FROM information_schema.columns WHERE table_schema = 'payroll' "
                            + "AND table_name = 'employee_deduction' "
                            + "AND column_name IN ('amount', 'period', 'posted_period', 'pay_input_id') ORDER BY column_name")) {
                List<String> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add(rs.getString(1) + ":" + rs.getString(2)
                            + (rs.getString(1).equals("amount") ? ":" + rs.getInt(3) + "," + rs.getInt(4) : ""));
                }
                assertThat(columns)
                        .containsExactly(
                                "amount:numeric:19,4",
                                "pay_input_id:uuid",
                                "period:character",
                                "posted_period:character");
            }
            try (ResultSet rs = st.executeQuery("SELECT indexname FROM pg_indexes "
                    + "WHERE schemaname = 'payroll' AND tablename = 'employee_deduction'")) {
                List<String> indexes = new ArrayList<>();
                while (rs.next()) {
                    indexes.add(rs.getString(1));
                }
                assertThat(indexes)
                        .contains(
                                "idx_emp_deduction_tenant_employee_period",
                                "idx_emp_deduction_tenant_period_status",
                                "uk_emp_deduction_tenant_pay_input");
            }
        }
    }

    private static EmployeeDeductionLineRequest line(UUID employee, String type, String amount) {
        return new EmployeeDeductionLineRequest(
                employee, THIS_MONTH.toString(), type, new BigDecimal(amount), "Recovery", null, null);
    }

    private static EmployeeDeductionLineRequest lineWithDocument(UUID employee, UUID document) {
        return new EmployeeDeductionLineRequest(
                employee, THIS_MONTH.toString(), "DAMAGE", new BigDecimal("400"), "Broken laptop", null, document);
    }
}
