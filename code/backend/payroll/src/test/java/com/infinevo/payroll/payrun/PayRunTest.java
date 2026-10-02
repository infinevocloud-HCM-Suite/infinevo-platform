package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.queue.QueueProducer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The pay date is checked against the tenant's own date, not the JVM's UTC one (W-47.2 §4, §14
 * decision 4). From 00:00 to 05:30 IST the UTC date is still yesterday, and the old check refused an
 * officer paying on the day as "in the future".
 */
class PayRunTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** 01:00 IST on 1 November 2026 is 19:30 UTC on 31 October. */
    private static final Instant ONE_AM_IST = Instant.parse("2026-10-31T19:30:00Z");

    private static final LocalDate IST_DATE = LocalDate.of(2026, 11, 1);
    private static final LocalDate UTC_DATE = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("pay accepts the tenant's today and refuses the tenant's tomorrow")
    void paysOnTheTenantsTodayNotTomorrow() {
        LocalDate today = LocalDate.of(2026, 10, 31);

        PayRun paid = approvedOctoberRun();
        paid.pay(today, today, "payer", Instant.now());
        assertThat(paid.getStatus()).isEqualTo(PayRunStatus.PAID);
        assertThat(paid.getPaidOn()).isEqualTo(today);

        PayRun refused = approvedOctoberRun();
        assertThatThrownBy(() -> refused.pay(today.plusDays(1), today, "payer", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
        assertThat(refused.getStatus()).isEqualTo(PayRunStatus.APPROVED);
    }

    @Test
    @DisplayName("at 01:00 IST (19:30 UTC the day before) the IST date is accepted")
    void acceptsTheIstDateAtOneInTheMorning() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(TENANT))).thenReturn(List.of("Asia/Kolkata"));
        PayRunServiceImpl service = service(jdbc, Clock.fixed(ONE_AM_IST, ZoneOffset.UTC));

        LocalDate today = service.tenantToday(TENANT);
        assertThat(today).isEqualTo(IST_DATE);

        PayRun run = approvedOctoberRun();
        run.pay(IST_DATE, today, "payer", ONE_AM_IST);
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.PAID);
        assertThat(run.getPaidOn()).isEqualTo(IST_DATE);

        // The defect this replaces: against the JVM's UTC date the same pay date is "in the future".
        assertThatThrownBy(() -> approvedOctoberRun().pay(IST_DATE, UTC_DATE, "payer", ONE_AM_IST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("today follows the tenant's own timezone, not a fixed one")
    void followsTheTenantsZone() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(TENANT))).thenReturn(List.of("UTC"));

        assertThat(service(jdbc, Clock.fixed(ONE_AM_IST, ZoneOffset.UTC)).tenantToday(TENANT))
                .isEqualTo(UTC_DATE);
    }

    @Test
    @DisplayName("a tenant with no usable timezone pays against Asia/Kolkata, the column's default")
    void fallsBackToTheColumnDefault() {
        Clock clock = Clock.fixed(ONE_AM_IST, ZoneOffset.UTC);

        JdbcTemplate noRow = mock(JdbcTemplate.class);
        when(noRow.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());
        assertThat(service(noRow, clock).tenantToday(TENANT)).isEqualTo(IST_DATE);

        JdbcTemplate badZone = mock(JdbcTemplate.class);
        when(badZone.queryForList(anyString(), eq(String.class), eq(TENANT))).thenReturn(List.of("Not/AZone"));
        assertThat(service(badZone, clock).tenantToday(TENANT)).isEqualTo(IST_DATE);

        assertThat(service(null, clock).tenantToday(TENANT)).isEqualTo(IST_DATE);
    }

    @SuppressWarnings("unchecked")
    private static PayRunServiceImpl service(JdbcTemplate jdbc, Clock clock) {
        return new PayRunServiceImpl(
                mock(PayRunRepository.class),
                mock(EmployeePayRunRepository.class),
                mock(PayPeriodService.class),
                mock(EmployeeService.class),
                mock(PayRunInclusionService.class),
                mock(PayInputService.class),
                mock(EmployeePayRunLineRepository.class),
                mock(JobService.class),
                (ObjectProvider<QueueProducer>) mock(ObjectProvider.class),
                null,
                null,
                jdbc,
                clock,
                mock(PlatformTransactionManager.class));
    }

    private static PayRun approvedOctoberRun() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        PayRun run = new PayRun(TENANT, YearMonth.of(2026, 10), start, end, start.plusDays(24), end, 0, 0, "officer");
        run.lock("officer", Instant.now());
        run.startComputing("officer");
        run.completeComputation(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), 0, "officer", Instant.now());
        run.approve("approver", Instant.now());
        return run;
    }
}
