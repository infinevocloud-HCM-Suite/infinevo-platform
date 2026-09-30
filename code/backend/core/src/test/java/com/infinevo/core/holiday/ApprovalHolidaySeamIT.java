package com.infinevo.core.holiday;

import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * W-17 F-1 — the approval escalation seam ({@code core.approval.HolidayQueryService}) is answered by
 * the real holiday calendar, not the empty fallback, so escalation skips configured holidays.
 */
@SpringBootTest(classes = HolidayTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class ApprovalHolidaySeamIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private com.infinevo.core.approval.HolidayQueryService approvalSeam;

    @Autowired
    private HolidayCalendarRepository calendarRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @BeforeAll
    static void applySchema() throws Exception {
        HolidayTestSchema.apply();
        HolidayTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        HolidayTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        HolidayTestSchema.clearAll();
        TenantContext.set(TENANT_A);
        HolidayCalendar calendar = calendarRepository.save(new HolidayCalendar(TENANT_A, "Default", true));
        holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "Independence Day",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                false,
                null));
        holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "Optional Festival",
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 18),
                true,
                null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Exactly one approval HolidayQueryService bean exists and it is the holiday adapter")
    void approvalSeam_isTheHolidayAdapter() {
        assertThat(context.getBeansOfType(com.infinevo.core.approval.HolidayQueryService.class))
                .hasSize(1);
        assertThat(approvalSeam).isInstanceOf(ApprovalHolidayQueryAdapter.class);
    }

    @Test
    @DisplayName("Escalation's holidaysBetween returns the configured holiday; restricted ones are left out")
    void approvalSeam_returnsConfiguredHoliday() {
        List<LocalDate> dates =
                approvalSeam.holidaysBetween(null, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20));

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 15));
    }
}
