package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * W-29.1 §7 — five employees: included; no salary; no bank; terminated last month; joined next
 * month. The rows are INCLUDED, SKIPPED NO_SALARY, SKIPPED NO_BANK_DETAILS, absent, absent, and the
 * counts on the run match them.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunInclusionIT extends AbstractIntegrationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final LocalDate SINCE = LocalDate.of(2025, 1, 1);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Included, no salary, no bank, left last month, joins next month -> 1 included, 2 skipped, 2 absent")
    void fiveEmployees() throws SQLException {
        UUID included = PayRunTestSchema.insertPayableEmployee(TENANT_A, "I-01");
        UUID noSalary = PayRunTestSchema.insertEmployee(TENANT_A, "I-02", SINCE, "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, noSalary);
        UUID noBank = PayRunTestSchema.insertEmployee(TENANT_A, "I-03", SINCE, "ACTIVE", null);
        PayRunTestSchema.insertSalary(TENANT_A, noBank, SINCE);
        UUID leftLastMonth =
                PayRunTestSchema.insertEmployee(TENANT_A, "I-04", SINCE, "TERMINATED", LocalDate.of(2026, 3, 31));
        PayRunTestSchema.insertSalary(TENANT_A, leftLastMonth, SINCE);
        PayRunTestSchema.insertBank(TENANT_A, leftLastMonth);
        UUID joinsNextMonth =
                PayRunTestSchema.insertEmployee(TENANT_A, "I-05", LocalDate.of(2026, 5, 1), "ACTIVE", null);
        PayRunTestSchema.insertSalary(TENANT_A, joinsNextMonth, LocalDate.of(2026, 5, 1));
        PayRunTestSchema.insertBank(TENANT_A, joinsNextMonth);

        PayRunResponse run = payRunService.create(APRIL);

        assertThat(run.includedCount()).isEqualTo(1);
        assertThat(run.skippedCount()).isEqualTo(2);

        List<EmployeePayRunResponse> rows = payRunService
                .employees(run.id(), null, PageRequest.of(0, 50, Sort.by("createdAt", "id")))
                .getContent();
        assertThat(rows)
                .extracting(EmployeePayRunResponse::employeeId)
                .containsExactlyInAnyOrder(included, noSalary, noBank)
                .doesNotContain(leftLastMonth, joinsNextMonth);
        assertThat(row(rows, included).inclusionStatus()).isEqualTo(InclusionStatus.INCLUDED);
        assertThat(row(rows, included).skipReason()).isNull();
        assertThat(row(rows, included).salaryVersionId()).isNotNull();
        assertThat(row(rows, included).employeeNumber()).isEqualTo("I-01");
        assertThat(row(rows, noSalary).skipReason()).isEqualTo(SkipReason.NO_SALARY);
        assertThat(row(rows, noBank).skipReason()).isEqualTo(SkipReason.NO_BANK_DETAILS);

        assertThat(payRunService
                        .employees(run.id(), InclusionStatus.SKIPPED, PageRequest.of(0, 50, Sort.by("createdAt", "id")))
                        .getContent())
                .extracting(EmployeePayRunResponse::employeeId)
                .containsExactlyInAnyOrder(noSalary, noBank);
    }

    @Test
    @DisplayName("A leaver terminated mid-period is still included — legacy dropped them unpaid")
    void midMonthLeaverIsIncluded() throws SQLException {
        UUID leaver = PayRunTestSchema.insertEmployee(TENANT_A, "I-06", SINCE, "TERMINATED", LocalDate.of(2026, 4, 15));
        PayRunTestSchema.insertSalary(TENANT_A, leaver, SINCE);
        PayRunTestSchema.insertBank(TENANT_A, leaver);

        PayRunResponse run = payRunService.create(APRIL);

        assertThat(run.includedCount()).isEqualTo(1);
        assertThat(payRunService
                        .employees(run.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                        .getContent())
                .extracting(EmployeePayRunResponse::employeeId)
                .containsExactly(leaver);
    }

    private static EmployeePayRunResponse row(List<EmployeePayRunResponse> rows, UUID employeeId) {
        return rows.stream()
                .filter(r -> r.employeeId().equals(employeeId))
                .findFirst()
                .orElseThrow();
    }
}
