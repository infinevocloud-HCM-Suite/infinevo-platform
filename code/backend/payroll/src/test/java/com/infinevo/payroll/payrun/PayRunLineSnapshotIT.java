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

/**
 * W-29.2 §7 — a line keeps the component's code and name as they were at compute time: renaming the
 * catalogue entry afterwards does not rewrite a computed month.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunLineSnapshotIT extends AbstractIntegrationTest {

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

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
    @DisplayName("Rename a catalogue component after compute; the line still shows the old code and name")
    void renameDoesNotRewriteLines() throws SQLException {
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        UUID employee = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "S-01", catalogue);
        PayRunResponse run = payRunService.create(YearMonth.of(2026, 7));
        payRunService.lock(run.id());
        worker.computeNow(run.id());

        PayRunTestSchema.execute(
                "UPDATE payroll.earning SET name = 'Base pay', code = 'BASE' WHERE id = ?", catalogue.basic());

        assertThat(payRunService.lines(run.id(), employee).lines())
                .filteredOn(l -> catalogue.basic().equals(l.componentId()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.componentCode()).isEqualTo("BASIC");
                    assertThat(l.componentName()).isEqualTo("Basic");
                });
    }
}
