package com.infinevo.payroll.priorpayroll;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * W-38.1 §7: Integration test for the two-way guard between regular pay runs and prior payroll rows.
 * Covers:
 * - A REGULAR run for 2026-08 => a 2026-08 row import is rejected with REAL_RUN_EXISTS.
 * - A CANCELLED run does not block import.
 * - Imported rows for 2026-09 => creating a regular pay run for 2026-09 throws PriorPayrollExistsException (409).
 * - Deleting those imported rows => the regular run for 2026-09 is created successfully.
 * - An off-cycle run is not blocked by prior payroll rows.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PriorPayrollRunGuardIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private PriorPayrollImportService importService;

    @Autowired
    private PriorPayrollService priorPayrollService;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayRunRepository payRunRepository;

    private UUID empId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();

        TenantContext.set(TENANT_A);
        givenSchedule();
        empId = PayRunTestSchema.insertPayableEmployee(TENANT_A, "EMP-01");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a REGULAR run blocks prior payroll import with REAL_RUN_EXISTS; a CANCELLED run does not block")
    void payRunBlocksPriorImportUnlessCancelled() throws Exception {
        TenantContext.set(TENANT_A);

        // Create a REGULAR run for 2026-08 (leave it DRAFT/active)
        payRunService.create(YearMonth.of(2026, 8));

        // Create a REGULAR run for 2026-07 and cancel it
        PayRunResponse run07 = payRunService.create(YearMonth.of(2026, 7));
        payRunService.cancel(run07.id());

        // Now attempt importing rows for 2026-08 and 2026-07
        String csv =
                """
                employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay
                EMP-01,2026-08,100000,1800,0,200,5000,93000
                EMP-01,2026-07,100000,1800,0,200,5000,93000
                """;

        UUID docId =
                PayrollTestSchema.insertDocument(TENANT_A, "guard_import.csv", csv.getBytes(StandardCharsets.UTF_8));

        PriorPayrollImportResponse response = importService.importFile(docId, FY, false);

        assertThat(response.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(response.rowsTotal()).isEqualTo(2);
        // 2026-07 succeeds (1 imported), 2026-08 fails with REAL_RUN_EXISTS (1 failed)
        assertThat(response.rowsImported()).isEqualTo(1);
        assertThat(response.rowsFailed()).isEqualTo(1);

        // Verify in DB that only 2026-07 row was inserted
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT period FROM payroll.prior_payroll_month WHERE tenant_id = ?")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("period")).isEqualTo("2026-07");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName(
            "prior payroll row blocks regular pay run creation with PriorPayrollExistsException; delete frees it; off-cycle run is not blocked")
    void priorPayrollBlocksRegularRunUntilDeleted() throws Exception {
        TenantContext.set(TENANT_A);

        // Import a row for 2026-09
        String csv =
                """
                employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay
                EMP-01,2026-09,100000,1800,0,200,5000,93000
                """;

        UUID docId = PayrollTestSchema.insertDocument(TENANT_A, "sep_import.csv", csv.getBytes(StandardCharsets.UTF_8));

        PriorPayrollImportResponse importResponse = importService.importFile(docId, FY, false);
        assertThat(importResponse.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED);
        assertThat(importResponse.rowsImported()).isEqualTo(1);

        // 1. Regular run creation for 2026-09 must be refused
        assertThatThrownBy(() -> payRunService.create(YearMonth.of(2026, 9)))
                .isInstanceOf(PriorPayrollExistsException.class);

        // 2. Off-cycle run for 2026-09 is NOT blocked by prior payroll
        PayRunResponse offCycleRun =
                payRunService.createOffCycle(LocalDate.of(2026, 9, 20), List.of(empId), "off-cycle for September");
        assertThat(offCycleRun).isNotNull();
        assertThat(offCycleRun.runType()).isEqualTo(PayRunType.OFF_CYCLE);
        assertThat(offCycleRun.period()).isEqualTo("2026-09");

        // 3. Delete the imported row for 2026-09
        UUID rowId;
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM payroll.prior_payroll_month WHERE tenant_id = ? AND period = '2026-09'")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                rowId = (UUID) rs.getObject("id");
            }
        }

        priorPayrollService.delete(rowId);
        assertThat(priorPayrollService.hasImportedRows("2026-09")).isFalse();

        // 4. Regular run creation for 2026-09 now succeeds!
        PayRunResponse regularRun = payRunService.create(YearMonth.of(2026, 9));
        assertThat(regularRun).isNotNull();
        assertThat(regularRun.runType()).isEqualTo(PayRunType.REGULAR);
        assertThat(regularRun.period()).isEqualTo("2026-09");
    }

    private void givenSchedule() {
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
    }
}
