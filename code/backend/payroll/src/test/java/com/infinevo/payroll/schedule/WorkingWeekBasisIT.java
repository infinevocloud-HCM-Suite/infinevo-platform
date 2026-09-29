package com.infinevo.payroll.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.lop.WorkingDayBasisCalculator;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying that the payroll pay schedule integrates into
 * core's {@link WorkingDayBasisCalculator} via the {@link com.infinevo.core.lop.WorkingWeekSource} port (W-28 §7).
 *
 * <p>Validates the primary wiring W-28 exists for:
 * <ul>
 *   <li>Tenant A has a Mon–Fri work week (5 days).</li>
 *   <li>Tenant B has a Mon–Sat work week (6 days).</li>
 *   <li>Both are on an ORG_DAYS policy with weekends_payable = false.</li>
 *   <li>{@link WorkingDayBasisCalculator#basisFor} returns different payable days for July 2026: 23 and 27.</li>
 * </ul>
 */
@SpringBootTest(classes = PayScheduleTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class WorkingWeekBasisIT extends AbstractIntegrationTest {

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private WorkingDayBasisCalculator basisCalculator;

    private static final UUID TENANT_A = PayScheduleTestSchema.TENANT_A;
    private static final UUID TENANT_B = PayScheduleTestSchema.TENANT_B;

    @BeforeAll
    static void applySchema() throws Exception {
        PayScheduleTestSchema.apply();
        PayScheduleTestSchema.seedTenants();
    }

    @BeforeEach
    void setUp() throws SQLException {
        PayScheduleTestSchema.clearSchedules();

        // Configure Tenant A: Mon–Fri work week
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        TenantContext.clear();

        // Configure Tenant B: Mon–Sat work week
        TenantContext.set(TENANT_B);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5, 6), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "WorkingDayBasisCalculator returns 23 days for Mon-Fri tenant and 27 days for Mon-Sat tenant in July 2026")
    void differentWorkingWeeksProduceDifferentPayableDays() {
        YearMonth july2026 = YearMonth.of(2026, 7);

        // Tenant A: Mon-Fri → 23 working days
        TenantContext.set(TENANT_A);
        WorkingDayBasisResponse basisA = basisCalculator.basisFor(july2026, null);
        assertThat(basisA.payableDays())
                .as("Tenant A (Mon-Fri) July 2026 payable days")
                .isEqualByComparingTo(new BigDecimal("23"));

        TenantContext.clear();

        // Tenant B: Mon-Sat → 27 working days
        TenantContext.set(TENANT_B);
        WorkingDayBasisResponse basisB = basisCalculator.basisFor(july2026, null);
        assertThat(basisB.payableDays())
                .as("Tenant B (Mon-Sat) July 2026 payable days")
                .isEqualByComparingTo(new BigDecimal("27"));
    }
}
