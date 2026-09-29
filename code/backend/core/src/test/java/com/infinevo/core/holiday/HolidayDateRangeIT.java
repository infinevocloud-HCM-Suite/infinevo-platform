package com.infinevo.core.holiday;

import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-17 (Issue #18) — Integration test verifying real PostgreSQL date-range overlap queries
 * in HolidayRepository and HolidayQueryService without mocks.
 */
@SpringBootTest(classes = HolidayTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class HolidayDateRangeIT extends AbstractIntegrationTest {

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private HolidayCalendarRepository calendarRepository;

    @Autowired
    private HolidayQueryService queryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Derived and @Query repository reads run outside any transaction when called directly, and
    // the tenant-binding datasource refuses an auto-commit connection. Reads go through here.
    private <T> T inTransaction(Supplier<T> read) {
        return new TransactionTemplate(transactionManager).execute(status -> read.get());
    }

    private HolidayCalendar calendar;

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

        calendar = new HolidayCalendar(TENANT_A, "Primary Calendar", true);
        calendar = calendarRepository.save(calendar);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("findHolidaysBetween correctly matches various date range overlap boundaries against real PostgreSQL")
    void dateRangeBoundaryOverlaps() {
        // H1: completely before query range (2026-01-10 to 2026-01-12)
        holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "New Year Fest",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12),
                false,
                null));

        // H2: overlaps start boundary (2026-02-27 to 2026-03-02)
        Holiday h2 = holidayRepository.save(new Holiday(
                TENANT_A, calendar.getId(), "Holi", LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 2), false, null));

        // H3: strictly within range (2026-03-15 to 2026-03-15)
        Holiday h3 = holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "Id-ul-Fitr",
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 3, 15),
                false,
                null));

        // H4: overlaps end boundary (2026-03-30 to 2026-04-03)
        Holiday h4 = holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "Good Friday",
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 4, 3),
                false,
                null));

        // H5: completely after query range (2026-05-01 to 2026-05-01)
        holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "Labour Day",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 1),
                false,
                null));

        // H6: encloses whole query range (2026-02-01 to 2026-04-30) on a second calendar
        HolidayCalendar calendar2 = calendarRepository.save(new HolidayCalendar(TENANT_A, "Enclosing Calendar", false));
        Holiday h6 = holidayRepository.save(new Holiday(
                TENANT_A,
                calendar2.getId(),
                "Extended Season",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 4, 30),
                false,
                null));

        // Query range: 2026-03-01 to 2026-03-31
        LocalDate queryFrom = LocalDate.of(2026, 3, 1);
        LocalDate queryTo = LocalDate.of(2026, 3, 31);

        List<Holiday> results = inTransaction(
                () -> holidayRepository.findHolidaysBetween(TENANT_A, calendar.getId(), queryFrom, queryTo));

        // H2, H3, H4 must be matched; H1 and H5 must NOT be matched
        assertThat(results).extracting(Holiday::getId).containsExactly(h2.getId(), h3.getId(), h4.getId());

        // For calendar2: H6 encloses range completely, must match
        List<Holiday> enclosingResults = inTransaction(
                () -> holidayRepository.findHolidaysBetween(TENANT_A, calendar2.getId(), queryFrom, queryTo));
        assertThat(enclosingResults).extracting(Holiday::getId).containsExactly(h6.getId());

        // Test isHoliday method
        assertThat(inTransaction(
                        () -> holidayRepository.isHoliday(TENANT_A, calendar.getId(), LocalDate.of(2026, 2, 26))))
                .isFalse();
        assertThat(inTransaction(
                        () -> holidayRepository.isHoliday(TENANT_A, calendar.getId(), LocalDate.of(2026, 2, 27))))
                .isTrue();
        assertThat(inTransaction(
                        () -> holidayRepository.isHoliday(TENANT_A, calendar.getId(), LocalDate.of(2026, 3, 1))))
                .isTrue();
        assertThat(inTransaction(
                        () -> holidayRepository.isHoliday(TENANT_A, calendar.getId(), LocalDate.of(2026, 3, 2))))
                .isTrue();
        assertThat(inTransaction(
                        () -> holidayRepository.isHoliday(TENANT_A, calendar.getId(), LocalDate.of(2026, 3, 3))))
                .isFalse();
    }

    @Test
    @DisplayName("queryService.holidaysBetween executes real SQL query falling back to default calendar when unmapped")
    void queryServiceRealExecution() {
        holidayRepository.save(new Holiday(
                TENANT_A,
                calendar.getId(),
                "Republic Day",
                LocalDate.of(2026, 1, 26),
                LocalDate.of(2026, 1, 26),
                false,
                "National Holiday"));

        // When unmapped location queried, falls back to default calendar
        List<HolidayResponse> holidays =
                queryService.holidaysBetween(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(holidays).hasSize(1);
        assertThat(holidays.get(0).name()).isEqualTo("Republic Day");
        assertThat(holidays.get(0).from()).isEqualTo(LocalDate.of(2026, 1, 26));
    }
}
